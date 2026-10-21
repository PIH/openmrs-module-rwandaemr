package org.openmrs.module.rwandaemr.radiology.queue;

import org.apache.commons.lang.StringUtils;
import org.hibernate.query.NativeQuery;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.radiology.HL7Utils;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PacsStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
	private final OrderPaymentLookup paymentLookup;

	public RadiologyQueueService(@Autowired DbSessionFactory dbSessionFactory,
								 @Autowired OrderService orderService,
								 @Autowired RadiologyConfig radiologyConfig,
								 @Autowired RwandaEmrConfig rwandaEmrConfig,
								 @Autowired @Qualifier("rwandaEmrService") RwandaEmrService rwandaEmrService,
								 @Autowired OrderPaymentLookup paymentLookup) {
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
		setCreatinine(rows, getLatestCreatinine(rows));
		setSentTimes(rows);
		return rows;
	}

	/**
	 * Records when each study was sent to PACS: its first new order message that PACS accepted or did not answer.  Studies
	 * sent before messages were logged have no time.
	 */
	private void setSentTimes(List<RadiologyQueueRow> rows) {
		Set<String> accessionNumbers = new HashSet<String>();
		for (RadiologyQueueRow row : rows) {
			if (StringUtils.isNotBlank(row.getAccessionNumber())) {
				accessionNumbers.add(row.getAccessionNumber());
			}
		}
		if (accessionNumbers.isEmpty()) {
			return;
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select accession_number, min(date_created) from rwandaemr_radiology_hl7_message " +
						"where direction = 'OUT' and order_control = 'NW' and status in ('ACCEPTED', 'NO_RESPONSE') " +
						"and accession_number in (:accessionNumbers) group by accession_number");
		query.setParameterList("accessionNumbers", accessionNumbers);
		Map<String, Date> sentTimes = new HashMap<String, Date>();
		for (Object result : query.list()) {
			Object[] values = (Object[]) result;
			sentTimes.put((String) values[0], (Date) values[1]);
		}
		for (RadiologyQueueRow row : rows) {
			row.setSentAt(sentTimes.get(row.getAccessionNumber()));
		}
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
		Set<String> reasons = new LinkedHashSet<String>();
		Date timeRequested = null;
		for (TestOrder order : orders) {
			row.getOrderIds().add(order.getOrderId());
			row.getOrderNumbers().add(order.getOrderNumber());
			String testName = getConceptName(order.getConcept());
			boolean stat = order.getUrgency() == Order.Urgency.STAT;
			studyTypes.add(testName);
			row.getTests().add(new RadiologyQueueRow.Test(order.getOrderId(), order.getOrderNumber(), testName, stat));
			row.setStat(row.isStat() || stat);
			String reason = order.getOrderReason() != null ? order.getOrderReason().getDisplayString() :
					order.getOrderReasonNonCoded();
			if (StringUtils.isNotBlank(reason)) {
				reasons.add(reason.trim());
			}
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
		row.setReason(reasons.isEmpty() ? null : StringUtils.join(reasons, "; "));
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

		row.setPacsStatus(active.isEmpty() ? PacsStatus.CANCELLED :
				getPacsStatus(orders, row, radiologyConfig.isManualReportModality(modality)));
		boolean completed = false;
		for (TestOrder order : orders) {
			completed = completed || order.getFulfillerStatus() == Order.FulfillerStatus.COMPLETED;
		}
		row.setProcessed(!active.isEmpty() && (completed || row.getPacsStatus().isStudyDone()));
		setPayment(row, orders, payments);
		return row;
	}

	/**
	 * Works out the PACS status from the results recorded for the study, and records on the row when the images and the
	 * report were received, the image viewer link and the report status.  A study PACS reported as done without a link to
	 * its images is shown as such, rather than as having its images received.  For modalities reported in OpenMRS each
	 * test has its own report, so the study is only reported once all its tests are.
	 */
	private PacsStatus getPacsStatus(List<TestOrder> orders, RadiologyQueueRow row, boolean reportedPerTest) {
		if (StringUtils.isBlank(orders.get(0).getAccessionNumber())) {
			return PacsStatus.NOT_SENT;
		}
		Concept studyConstruct = radiologyConfig.getRadiologyStudyConstruct();
		Concept reportConstruct = radiologyConfig.getRadiologyReportConstruct();
		Concept imagesAvailable = radiologyConfig.getRadiologyImagesAvailable();
		Concept reportType = radiologyConfig.getRadiologyReportType();
		boolean imagesReceived = false;
		boolean reported = false;
		int testsReported = 0;
		for (TestOrder order : orders) {
			RadiologyQueueRow.Test test = getTest(row, order);
			boolean testReported = false;
			for (Obs obs : rwandaEmrService.getObsByOrder(order)) {
				if (obs.getConcept().equals(studyConstruct)) {
					imagesReceived = true;
					row.setImagesReceivedAt(earliest(row.getImagesReceivedAt(), getObsTime(obs)));
					Obs images = getGroupMember(obs, imagesAvailable);
					if (images != null && StringUtils.isNotBlank(images.getComment()) && row.getImageUrl() == null) {
						row.setImageUrl(HL7Utils.getViewerUrl(images.getComment()));
					}
				}
				else if (obs.getConcept().equals(reportConstruct)) {
					reported = true;
					testReported = true;
					row.setReportedAt(earliest(row.getReportedAt(), getObsTime(obs)));
					row.setReportOrderId(row.getReportOrderId() == null ? order.getOrderId() : row.getReportOrderId());
					Obs status = getGroupMember(obs, reportType);
					if (status != null && status.getValueCoded() != null) {
						row.setReportStatus(getConceptName(status.getValueCoded()));
						if (test != null) {
							test.setReportStatus(getConceptName(status.getValueCoded()));
						}
					}
				}
			}
			if (testReported) {
				testsReported++;
				if (test != null) {
					test.setReported(true);
				}
			}
		}
		// PACS only sends the link with the report, so a done study without one is opened in the viewer by its accession number
		if (imagesReceived && row.getImageUrl() == null) {
			row.setImageUrl(radiologyConfig.getImageViewerUrl(orders.get(0).getAccessionNumber()));
		}
		if (reportedPerTest ? testsReported == orders.size() : reported) {
			return PacsStatus.REPORTED;
		}
		if (!imagesReceived) {
			return PacsStatus.SENT;
		}
		return row.getImageUrl() == null ? PacsStatus.STUDY_DONE_NO_LINK : PacsStatus.IMAGES_RECEIVED;
	}

	private RadiologyQueueRow.Test getTest(RadiologyQueueRow row, TestOrder order) {
		for (RadiologyQueueRow.Test test : row.getTests()) {
			if (test.getOrderId() != null && test.getOrderId().equals(order.getOrderId())) {
				return test;
			}
		}
		return null;
	}

	private Obs getGroupMember(Obs group, Concept concept) {
		if (concept != null && group.getGroupMembers() != null) {
			for (Obs member : group.getGroupMembers()) {
				if (concept.equals(member.getConcept()) && !member.getVoided()) {
					return member;
				}
			}
		}
		return null;
	}

	private Date getObsTime(Obs obs) {
		if (obs.getEncounter() != null && obs.getEncounter().getEncounterDatetime() != null) {
			return obs.getEncounter().getEncounterDatetime();
		}
		return obs.getObsDatetime();
	}

	private Date earliest(Date current, Date candidate) {
		return current == null || (candidate != null && candidate.before(current)) ? candidate : current;
	}

	/**
	 * Shows the patient's latest creatinine result on CT studies, as contrast may be given
	 */
	static void setCreatinine(List<RadiologyQueueRow> rows, Map<Integer, RadiologyQueueRow.Creatinine> latestByPatient) {
		for (RadiologyQueueRow row : rows) {
			if (Modality.CT.name().equals(row.getModality()) && row.getPacsStatus() != PacsStatus.CANCELLED) {
				row.setCreatinine(latestByPatient.get(row.getPatientId()));
			}
		}
	}

	/**
	 * @return the latest creatinine result of each patient with a CT study in the rows, by patient id
	 */
	private Map<Integer, RadiologyQueueRow.Creatinine> getLatestCreatinine(List<RadiologyQueueRow> rows) {
		Map<Integer, RadiologyQueueRow.Creatinine> ret = new HashMap<Integer, RadiologyQueueRow.Creatinine>();
		Set<Integer> patientIds = new HashSet<Integer>();
		for (RadiologyQueueRow row : rows) {
			if (Modality.CT.name().equals(row.getModality()) && row.getPatientId() != null) {
				patientIds.add(row.getPatientId());
			}
		}
		Concept creatinine = patientIds.isEmpty() ? null : radiologyConfig.getCreatinineConcept();
		if (creatinine == null) {
			return ret;
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.person_id, o.value_numeric, o.value_text, o.obs_datetime from obs o " +
						"where o.concept_id = :conceptId and o.voided = 0 and o.person_id in (:patientIds) " +
						"and (o.value_numeric is not null or o.value_text is not null) " +
						"order by o.person_id, o.obs_datetime desc, o.obs_id desc");
		query.setParameter("conceptId", creatinine.getConceptId());
		query.setParameterList("patientIds", patientIds);
		ConceptNumeric numeric = Context.getConceptService().getConceptNumeric(creatinine.getConceptId());
		for (Object result : query.list()) {
			Object[] values = (Object[]) result;
			Integer patientId = ((Number) values[0]).intValue();
			if (!ret.containsKey(patientId)) {
				ret.put(patientId, toCreatinine(values[1] == null ? null : ((Number) values[1]).doubleValue(),
						(String) values[2], (Date) values[3], numeric));
			}
		}
		return ret;
	}

	static RadiologyQueueRow.Creatinine toCreatinine(Double valueNumeric, String valueText, Date date, ConceptNumeric numeric) {
		if (valueNumeric == null) {
			return new RadiologyQueueRow.Creatinine(StringUtils.trimToEmpty(valueText), null, date, false);
		}
		String value = new BigDecimal(valueNumeric.toString()).stripTrailingZeros().toPlainString();
		String units = numeric == null ? null : StringUtils.trimToNull(numeric.getUnits());
		boolean high = numeric != null && numeric.getHiNormal() != null && valueNumeric > numeric.getHiNormal();
		return new RadiologyQueueRow.Creatinine(value, units, date, high);
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
