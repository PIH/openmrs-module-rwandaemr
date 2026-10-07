package org.openmrs.module.rwandaemr.radiology.queue;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.NativeQuery;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PacsStatus;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the radiology queue: the radiology studies requested in a date range, with their PACS and payment status
 */
@Component
public class RadiologyQueueService {

	public static final String PRIVILEGE_VIEW_RADIOLOGY_QUEUE = "Task: rwandaemr.radiologyQueue";

	private final DbSessionFactory dbSessionFactory;
	private final OrderService orderService;
	private final RadiologyConfig radiologyConfig;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final RwandaEmrService rwandaEmrService;
	private final RadiologyPaymentLookup paymentLookup;

	public RadiologyQueueService(@Autowired DbSessionFactory dbSessionFactory,
								 @Autowired OrderService orderService,
								 @Autowired RadiologyConfig radiologyConfig,
								 @Autowired RwandaEmrConfig rwandaEmrConfig,
								 @Autowired @Qualifier("rwandaEmrService") RwandaEmrService rwandaEmrService,
								 @Autowired RadiologyPaymentLookup paymentLookup) {
		this.dbSessionFactory = dbSessionFactory;
		this.orderService = orderService;
		this.radiologyConfig = radiologyConfig;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.rwandaEmrService = rwandaEmrService;
		this.paymentLookup = paymentLookup;
	}

	/**
	 * @return one row per radiology study requested (created) between the given dates, oldest first.  Tests sent to PACS as one
	 * combined study share an accession number and make up one row.
	 */
	@Transactional(readOnly = true)
	public List<RadiologyQueueRow> getQueue(Date fromDate, Date toDate) {
		Map<Concept, Modality> orderables = radiologyConfig.getOrderables(radiologyConfig.getModalityConceptSets());
		List<TestOrder> orders = getRadiologyOrders(orderables.keySet(), fromDate, toDate);
		Map<Integer, OrderPayment> payments = paymentLookup.getPayments(orders);

		// Group tests into studies: tests sent together share an accession number within their encounter
		Map<String, List<TestOrder>> studies = new LinkedHashMap<String, List<TestOrder>>();
		for (TestOrder order : orders) {
			String key = StringUtils.isBlank(order.getAccessionNumber()) ? "order:" + order.getOrderId() :
					"study:" + (order.getEncounter() == null ? "" : order.getEncounter().getEncounterId()) + ":"
							+ order.getAccessionNumber();
			studies.computeIfAbsent(key, k -> new ArrayList<TestOrder>()).add(order);
		}

		List<RadiologyQueueRow> rows = new ArrayList<RadiologyQueueRow>();
		for (List<TestOrder> study : studies.values()) {
			rows.add(toRow(study, orderables, payments));
		}
		return rows;
	}

	private List<TestOrder> getRadiologyOrders(Set<Concept> orderables, Date fromDate, Date toDate) {
		List<TestOrder> ret = new ArrayList<TestOrder>();
		if (orderables.isEmpty()) {
			return ret;
		}
		List<Integer> conceptIds = new ArrayList<Integer>();
		for (Concept concept : orderables) {
			conceptIds.add(concept.getConceptId());
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.order_id from orders o where o.voided = 0 and o.order_action <> 'DISCONTINUE' " +
						"and o.concept_id in (:conceptIds) and o.date_created between :fromDate and :toDate " +
						"order by o.date_created, o.order_id");
		query.setParameterList("conceptIds", conceptIds);
		query.setParameter("fromDate", fromDate);
		query.setParameter("toDate", toDate);
		for (Object orderId : query.list()) {
			Order order = orderService.getOrder(((Number) orderId).intValue());
			if (order instanceof TestOrder) {
				ret.add((TestOrder) order);
			}
		}
		return ret;
	}

	RadiologyQueueRow toRow(List<TestOrder> study, Map<Concept, Modality> orderables, Map<Integer, OrderPayment> payments) {
		// A cancelled test is left out of a study that still has active tests
		List<TestOrder> active = new ArrayList<TestOrder>();
		for (TestOrder order : study) {
			if (order.getDateStopped() == null) {
				active.add(order);
			}
		}
		List<TestOrder> orders = active.isEmpty() ? study : active;
		TestOrder first = orders.get(0);

		RadiologyQueueRow row = new RadiologyQueueRow();
		row.setAccessionNumber(first.getAccessionNumber());
		Set<String> studyTypes = new LinkedHashSet<String>();
		Set<String> histories = new LinkedHashSet<String>();
		Date timeRequested = null;
		for (TestOrder order : orders) {
			row.getOrderIds().add(order.getOrderId());
			row.getOrderNumbers().add(order.getOrderNumber());
			studyTypes.add(getConceptName(order.getConcept()));
			if (StringUtils.isNotBlank(order.getClinicalHistory())) {
				histories.add(order.getClinicalHistory().trim());
			}
			// When the request was made: forms record only the date as the activation date, so use the creation time
			Date requested = order.getDateCreated() != null ? order.getDateCreated() : order.getDateActivated();
			if (timeRequested == null || requested.before(timeRequested)) {
				timeRequested = requested;
			}
		}
		row.setTypeOfStudy(StringUtils.join(studyTypes, ", "));
		row.setClinicalHistory(histories.isEmpty() ? null : StringUtils.join(histories, "; "));
		row.setTimeRequested(timeRequested);

		Patient patient = first.getPatient();
		row.setPatientId(patient.getPatientId());
		row.setPatientIdentifier(getPatientIdentifier(patient));
		PersonName name = patient.getPersonName();
		row.setPatientName(name == null ? null :
				StringUtils.trimToNull(StringUtils.defaultString(name.getGivenName()) + " "
						+ StringUtils.defaultString(name.getFamilyName())));
		row.setAge(patient.getAge());
		row.setSex(patient.getGender());

		Modality modality = orderables.get(first.getConcept());
		row.setModality(modality == null ? null : modality.name());
		row.setRequestingPhysician(getProviderName(first.getOrderer()));
		row.setRequestingDepartment(first.getEncounter() == null || first.getEncounter().getLocation() == null ? null :
				first.getEncounter().getLocation().getName());

		row.setPacsStatus(active.isEmpty() ? PacsStatus.CANCELLED : getPacsStatus(orders));
		boolean completed = false;
		for (TestOrder order : orders) {
			completed = completed || order.getFulfillerStatus() == Order.FulfillerStatus.COMPLETED;
		}
		row.setProcessed(!active.isEmpty() && (completed || row.getPacsStatus() == PacsStatus.IMAGES_RECEIVED
				|| row.getPacsStatus() == PacsStatus.REPORTED));
		setPayment(row, orders, payments);
		return row;
	}

	private PacsStatus getPacsStatus(List<TestOrder> orders) {
		if (StringUtils.isBlank(orders.get(0).getAccessionNumber())) {
			return PacsStatus.NOT_SENT;
		}
		Concept studyConstruct = radiologyConfig.getRadiologyStudyConstruct();
		Concept reportConstruct = radiologyConfig.getRadiologyReportConstruct();
		boolean imagesReceived = false;
		for (TestOrder order : orders) {
			for (Obs obs : rwandaEmrService.getObsByOrder(order)) {
				if (obs.getConcept().equals(reportConstruct)) {
					return PacsStatus.REPORTED;
				}
				imagesReceived = imagesReceived || obs.getConcept().equals(studyConstruct);
			}
		}
		return imagesReceived ? PacsStatus.IMAGES_RECEIVED : PacsStatus.SENT;
	}

	/**
	 * Tests that were not billed are left out when others in the study were.  The study is paid when all its billed
	 * tests are paid, not paid when none are, and partially paid otherwise.
	 */
	static void setPayment(RadiologyQueueRow row, List<TestOrder> orders, Map<Integer, OrderPayment> payments) {
		int billed = 0;
		int paid = 0;
		int unpaid = 0;
		Date lastPayment = null;
		for (TestOrder order : orders) {
			OrderPayment payment = payments.get(order.getOrderId());
			PaymentStatus status = payment == null ? PaymentStatus.NOT_BILLED : payment.getStatus();
			if (status == PaymentStatus.NOT_BILLED) {
				continue;
			}
			billed++;
			if (status == PaymentStatus.PAID) {
				paid++;
			}
			else if (status == PaymentStatus.NOT_PAID) {
				unpaid++;
			}
			Date paymentDate = payment.getLastPaymentDate();
			if (paymentDate != null && (lastPayment == null || paymentDate.after(lastPayment))) {
				lastPayment = paymentDate;
			}
		}
		if (billed == 0) {
			row.setPaymentStatus(PaymentStatus.NOT_BILLED);
		}
		else if (paid == billed) {
			row.setPaymentStatus(PaymentStatus.PAID);
		}
		else if (unpaid == billed) {
			row.setPaymentStatus(PaymentStatus.NOT_PAID);
		}
		else {
			row.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
		}
		row.setTimeOfPayment(lastPayment);
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
			PersonName name = provider.getPerson().getPersonName();
			return StringUtils.trimToNull(StringUtils.defaultString(name.getGivenName()) + " "
					+ StringUtils.defaultString(name.getFamilyName()));
		}
		return provider.getName();
	}

	private String getConceptName(Concept concept) {
		if (concept.getFullySpecifiedName(Locale.ENGLISH) != null) {
			return concept.getFullySpecifiedName(Locale.ENGLISH).getName();
		}
		return concept.getDisplayString();
	}
}
