package org.openmrs.module.rwandaemr.pathology.queue;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.NativeQuery;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.api.OrderService;
import org.openmrs.api.UserService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.pathology.PathologyCase;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseRepository;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseRepository.StatusChange;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the pathology queue: the pathology requests made in a date range, with where each is in the lab and whether
 * its tests are paid
 */
@Component
public class PathologyQueueService {

	private final DbSessionFactory dbSessionFactory;
	private final OrderService orderService;
	private final UserService userService;
	private final PathologyConfig pathologyConfig;
	private final PathologyCaseRepository caseRepository;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final OrderPaymentLookup paymentLookup;

	public PathologyQueueService(@Autowired DbSessionFactory dbSessionFactory,
								 @Autowired OrderService orderService,
								 @Autowired UserService userService,
								 @Autowired PathologyConfig pathologyConfig,
								 @Autowired PathologyCaseRepository caseRepository,
								 @Autowired RwandaEmrConfig rwandaEmrConfig,
								 @Autowired OrderPaymentLookup paymentLookup) {
		this.dbSessionFactory = dbSessionFactory;
		this.orderService = orderService;
		this.userService = userService;
		this.pathologyConfig = pathologyConfig;
		this.caseRepository = caseRepository;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.paymentLookup = paymentLookup;
	}

	/**
	 * @return one row per pathology request whose tests were ordered between the given dates, oldest first.  As a case can
	 * stay in the lab for weeks, cases still in the lab can be included however long ago they were requested.
	 */
	@Transactional(readOnly = true)
	public List<PathologyQueueRow> getQueue(Date fromDate, Date toDate, boolean includeCasesInLab) {
		return toRows(getPathologyOrders(fromDate, toDate, includeCasesInLab));
	}

	/**
	 * @return how the queue's search for the given dates goes, step by step, to find why requests are missing from it.
	 * Only counts, ids, dates and settings: nothing about patients.
	 */
	@Transactional(readOnly = true)
	public Map<String, Object> getDiagnostics(Date fromDate, Date toDate) {
		Map<String, Object> ret = new LinkedHashMap<String, Object>();
		OrderType orderType = pathologyConfig.getPathologyOrderType();
		ret.put("orderTypeId", orderType == null ? null : orderType.getOrderTypeId());
		ret.put("fromDate", String.valueOf(fromDate));
		ret.put("toDate", String.valueOf(toDate));
		ret.put("serverTimeZone", java.util.TimeZone.getDefault().getID());
		org.hibernate.Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		ret.put("databaseNow", String.valueOf(session.createSQLQuery("select now()").uniqueResult()));
		if (orderType == null) {
			return ret;
		}
		ret.put("ordersOfType", count(session, "o.order_type_id = :orderTypeId", orderType, null, null));
		ret.put("activeOrdersOfType", count(session, "o.order_type_id = :orderTypeId and o.voided = 0 " +
				"and o.order_action <> 'DISCONTINUE'", orderType, null, null));
		ret.put("activeOrdersOfTypeInRange", count(session, "o.order_type_id = :orderTypeId and o.voided = 0 " +
				"and o.order_action <> 'DISCONTINUE' and o.date_created between :fromDate and :toDate", orderType, fromDate, toDate));
		ret.put("latestOrderOfType", String.valueOf(session.createSQLQuery(
				"select max(o.date_created) from orders o where o.order_type_id = :orderTypeId")
				.setParameter("orderTypeId", orderType.getOrderTypeId()).uniqueResult()));
		List<TestOrder> orders = getPathologyOrders(fromDate, toDate, true);
		ret.put("testOrdersFound", orders.size());
		ret.put("requestRows", toRows(orders).size());
		return ret;
	}

	private Object count(org.hibernate.Session session, String where, OrderType orderType, Date fromDate, Date toDate) {
		NativeQuery<?> query = session.createSQLQuery("select count(*) from orders o where " + where);
		query.setParameter("orderTypeId", orderType.getOrderTypeId());
		if (fromDate != null) {
			query.setParameter("fromDate", fromDate);
			query.setParameter("toDate", toDate);
		}
		return query.uniqueResult();
	}

	/**
	 * @return one row per pathology request of the patient, oldest first
	 */
	@Transactional(readOnly = true)
	public List<PathologyQueueRow> getRequests(Patient patient) {
		List<TestOrder> orders = new ArrayList<TestOrder>();
		OrderType orderType = pathologyConfig.getPathologyOrderType();
		if (patient == null || orderType == null) {
			return Collections.emptyList();
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.order_id from orders o where o.voided = 0 and o.order_action <> 'DISCONTINUE' " +
						"and o.order_type_id = :orderTypeId and o.patient_id = :patientId order by o.date_created, o.order_id");
		query.setParameter("orderTypeId", orderType.getOrderTypeId());
		query.setParameter("patientId", patient.getPatientId());
		for (Object orderId : query.list()) {
			Order order = orderService.getOrder(((Number) orderId).intValue());
			if (order instanceof TestOrder) {
				orders.add((TestOrder) order);
			}
		}
		return toRows(orders);
	}

	private List<PathologyQueueRow> toRows(List<TestOrder> orders) {
		Map<Encounter, List<TestOrder>> requests = new LinkedHashMap<Encounter, List<TestOrder>>();
		List<Integer> encounterIds = new ArrayList<Integer>();
		for (TestOrder order : orders) {
			if (order.getEncounter() != null) {
				if (!requests.containsKey(order.getEncounter())) {
					encounterIds.add(order.getEncounter().getEncounterId());
				}
				requests.computeIfAbsent(order.getEncounter(), k -> new ArrayList<TestOrder>()).add(order);
			}
		}
		Map<Integer, OrderPayment> payments = paymentLookup.getPayments(orders);
		Map<Integer, PathologyCase> cases = caseRepository.getCases(encounterIds);
		List<Integer> caseIds = new ArrayList<Integer>();
		for (PathologyCase pathologyCase : cases.values()) {
			caseIds.add(pathologyCase.getPathologyCaseId());
		}
		Map<Integer, List<StatusChange>> histories = caseRepository.getHistories(caseIds);
		Map<Integer, String> userNames = new HashMap<Integer, String>();

		List<PathologyQueueRow> rows = new ArrayList<PathologyQueueRow>();
		for (Map.Entry<Encounter, List<TestOrder>> request : requests.entrySet()) {
			PathologyCase pathologyCase = cases.get(request.getKey().getEncounterId());
			List<StatusChange> history = pathologyCase == null ? Collections.<StatusChange>emptyList() :
					histories.getOrDefault(pathologyCase.getPathologyCaseId(), Collections.<StatusChange>emptyList());
			rows.add(toRow(request.getKey(), request.getValue(), pathologyCase, history, payments, userNames));
		}
		return rows;
	}

	private List<TestOrder> getPathologyOrders(Date fromDate, Date toDate, boolean includeCasesInLab) {
		List<TestOrder> ret = new ArrayList<TestOrder>();
		OrderType orderType = pathologyConfig.getPathologyOrderType();
		if (orderType == null) {
			return ret;
		}
		String casesInLab = includeCasesInLab ? "or o.encounter_id in (select c.encounter_id from rwandaemr_pathology_case c " +
				"where c.status in (:inLab)) " : "";
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.order_id from orders o where o.voided = 0 and o.order_action <> 'DISCONTINUE' " +
						"and o.order_type_id = :orderTypeId and (o.date_created between :fromDate and :toDate " +
						casesInLab + ") order by o.date_created, o.order_id");
		query.setParameter("orderTypeId", orderType.getOrderTypeId());
		query.setParameter("fromDate", fromDate);
		query.setParameter("toDate", toDate);
		if (includeCasesInLab) {
			List<String> inLab = new ArrayList<String>();
			for (PathologyStatus status : PathologyStatus.values()) {
				if (status.isInLab()) {
					inLab.add(status.name());
				}
			}
			query.setParameterList("inLab", inLab);
		}
		for (Object orderId : query.list()) {
			Order order = orderService.getOrder(((Number) orderId).intValue());
			if (order instanceof TestOrder) {
				ret.add((TestOrder) order);
			}
		}
		return ret;
	}

	PathologyQueueRow toRow(Encounter encounter, List<TestOrder> requestOrders, PathologyCase pathologyCase,
							List<StatusChange> history, Map<Integer, OrderPayment> payments, Map<Integer, String> userNames) {
		// A cancelled test is left out of a request that still has active tests
		List<TestOrder> active = new ArrayList<TestOrder>();
		for (TestOrder order : requestOrders) {
			if (order.getDateStopped() == null) {
				active.add(order);
			}
		}
		List<TestOrder> orders = active.isEmpty() ? requestOrders : active;

		PathologyQueueRow row = new PathologyQueueRow();
		row.setEncounterId(encounter.getEncounterId());
		row.setEncounterUuid(encounter.getUuid());
		Date timeRequested = null;
		Set<String> additionalInformation = new LinkedHashSet<String>();
		for (TestOrder order : orders) {
			row.getOrderIds().add(order.getOrderId());
			row.getOrderNumbers().add(order.getOrderNumber());
			row.getTests().add(getConceptName(order.getConcept()));
			row.setUrgent(row.isUrgent() || order.getUrgency() == Order.Urgency.STAT);
			if (StringUtils.isNotBlank(order.getOrderReasonNonCoded())) {
				additionalInformation.add(order.getOrderReasonNonCoded().trim());
			}
			// Forms record only the date as the activation date, so use the creation time
			Date requested = order.getDateCreated() != null ? order.getDateCreated() : order.getDateActivated();
			if (timeRequested == null || (requested != null && requested.before(timeRequested))) {
				timeRequested = requested;
			}
		}
		row.setTimeRequested(timeRequested);
		row.setAdditionalInformation(additionalInformation.isEmpty() ? null : StringUtils.join(additionalInformation, "; "));

		if (pathologyCase == null) {
			row.setStatus(PathologyStatus.ORDERED);
			row.setCancelled(active.isEmpty());
		}
		else {
			row.setCaseId(pathologyCase.getPathologyCaseId());
			row.setAccessionNumber(pathologyCase.getAccessionNumber());
			row.setStatus(pathologyCase.getStatus());
			row.setRejectionReason(pathologyCase.getRejectionReason());
			row.setStatusChangedAt(pathologyCase.getDateChanged() != null ? pathologyCase.getDateChanged() :
					pathologyCase.getDateCreated());
		}
		for (StatusChange change : history) {
			row.getHistory().add(new PathologyQueueRow.HistoryEntry(change.getStatus(), change.getReason(),
					getUserName(change.getUserId(), userNames), change.getDate()));
		}

		Patient patient = encounter.getPatient();
		row.setPatientId(patient.getPatientId());
		row.setPatientUuid(patient.getUuid());
		row.setPatientIdentifier(getPatientIdentifier(patient));
		row.setPatientName(getName(patient.getPersonName()));
		row.setAge(patient.getAge());
		row.setSex(patient.getGender());

		row.setRequestingPhysician(getProviderName(orders.get(0).getOrderer()));
		row.setRequestingDepartment(encounter.getLocation() == null ? null : encounter.getLocation().getName());
		setRequestFormAnswers(row, encounter);

		OrderPayment payment = OrderPaymentLookup.combine(orders, payments);
		row.setPaymentStatus(payment.getStatus());
		row.setTimeOfPayment(payment.getLastPaymentDate());
		return row;
	}

	/**
	 * Shows what the request form recorded about the specimen: the hospital it comes from, the specimens obtained and
	 * any other tests requested in free text.  The hospital is where the surgery or procedure was done, or else where the
	 * request was entered.
	 */
	private void setRequestFormAnswers(PathologyQueueRow row, Encounter encounter) {
		Concept hospital = pathologyConfig.getHospitalOfOrigin();
		Concept hospitalNonCoded = pathologyConfig.getHospitalOfOriginNonCoded();
		Concept specimen = pathologyConfig.getSpecimen();
		Concept specimenNonCoded = pathologyConfig.getSpecimenNonCoded();
		Concept otherTests = pathologyConfig.getOtherTestsRequested();
		String codedHospital = null;
		String nonCodedHospital = null;
		Set<String> otherTestsRequested = new LinkedHashSet<String>();
		Map<String, String> specimens = new TreeMap<String, String>();
		for (Obs obs : encounter.getObsAtTopLevel(false)) {
			Concept concept = obs.getConcept();
			if (concept.equals(hospital) && obs.getValueCoded() != null) {
				codedHospital = getConceptName(obs.getValueCoded());
			}
			else if (concept.equals(hospitalNonCoded) && StringUtils.isNotBlank(obs.getValueText())) {
				nonCodedHospital = obs.getValueText().trim();
			}
			else if (concept.equals(otherTests) && StringUtils.isNotBlank(obs.getValueText())) {
				otherTestsRequested.add(obs.getValueText().trim());
			}
			else if (PathologyConfig.SPECIMEN_GROUP_LETTERS.containsKey(concept.getUuid())) {
				// A specimen is the text given for it, or else the specimen chosen
				String text = null;
				String coded = null;
				for (Obs member : obs.getGroupMembers(false)) {
					if (member.getConcept().equals(specimenNonCoded) && StringUtils.isNotBlank(member.getValueText())) {
						text = member.getValueText().trim();
					}
					else if (member.getConcept().equals(specimen) && member.getValueCoded() != null) {
						coded = getConceptName(member.getValueCoded());
					}
				}
				if (text != null || coded != null) {
					String letter = PathologyConfig.SPECIMEN_GROUP_LETTERS.get(concept.getUuid());
					specimens.put(letter, letter + ": " + (text != null ? text : coded));
				}
			}
		}
		row.getSpecimens().addAll(specimens.values());
		String fromForm = nonCodedHospital != null ? nonCodedHospital : codedHospital;
		row.setHospitalOfOrigin(fromForm != null ? fromForm : row.getRequestingDepartment());
		row.setOtherTestsRequested(otherTestsRequested.isEmpty() ? null : StringUtils.join(otherTestsRequested, "; "));
	}

	private String getUserName(Integer userId, Map<Integer, String> userNames) {
		if (userId == null) {
			return null;
		}
		if (!userNames.containsKey(userId)) {
			User user = userService.getUser(userId);
			String name = user == null ? null : getName(user.getPersonName());
			userNames.put(userId, name != null ? name : (user == null ? null : user.getUsername()));
		}
		return userNames.get(userId);
	}

	private String getPatientIdentifier(Patient patient) {
		PatientIdentifier identifier = null;
		if (rwandaEmrConfig.getPrimaryCareIdentifierType() != null) {
			identifier = patient.getPatientIdentifier(rwandaEmrConfig.getPrimaryCareIdentifierType());
		}
		if (identifier == null) {
			identifier = patient.getPatientIdentifier();
		}
		return identifier == null ? null : identifier.getIdentifier();
	}

	private String getProviderName(Provider provider) {
		if (provider == null) {
			return null;
		}
		if (provider.getPerson() != null && provider.getPerson().getPersonName() != null) {
			return getName(provider.getPerson().getPersonName());
		}
		return provider.getName();
	}

	private String getName(PersonName name) {
		return name == null ? null : StringUtils.trimToNull(StringUtils.defaultString(name.getGivenName()) + " "
				+ StringUtils.defaultString(name.getFamilyName()));
	}

	private String getConceptName(Concept concept) {
		if (concept.getFullySpecifiedName(Locale.ENGLISH) != null) {
			return concept.getFullySpecifiedName(Locale.ENGLISH).getName();
		}
		return concept.getDisplayString();
	}
}
