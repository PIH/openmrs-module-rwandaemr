package org.openmrs.module.rwandaemr.radiology.queue;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PacsStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadiologyQueueServiceTest {

	private static final long MINUTE = 60 * 1000L;

	private RadiologyConfig radiologyConfig;
	private RwandaEmrService rwandaEmrService;
	private RadiologyQueueService service;
	private Concept studyConstruct;
	private Concept reportConstruct;
	private Concept footXray;
	private Concept handXray;
	private Patient patient;
	private Encounter encounter;
	private Map<Concept, Modality> orderables;
	private Date orderTime;

	@BeforeEach
	public void setUp() {
		radiologyConfig = mock(RadiologyConfig.class);
		rwandaEmrService = mock(RwandaEmrService.class);
		RwandaEmrConfig rwandaEmrConfig = mock(RwandaEmrConfig.class);
		service = new RadiologyQueueService(null, null, radiologyConfig, rwandaEmrConfig, rwandaEmrService, null);

		studyConstruct = concept(100, "Radiology study construct");
		reportConstruct = concept(101, "Radiology report construct");
		when(radiologyConfig.getRadiologyStudyConstruct()).thenReturn(studyConstruct);
		when(radiologyConfig.getRadiologyReportConstruct()).thenReturn(reportConstruct);
		when(rwandaEmrService.getObsByOrder(any())).thenReturn(Collections.<Obs>emptyList());

		footXray = concept(1, "X-ray of left calcaneus");
		handXray = concept(2, "X-ray of right humerus");
		orderables = new HashMap<Concept, Modality>();
		orderables.put(footXray, Modality.DX);
		orderables.put(handXray, Modality.DX);

		patient = new Patient(10);
		patient.addName(new PersonName("Jean", null, "Mugabo"));
		patient.setGender("M");
		patient.setBirthdate(new Date(System.currentTimeMillis() - 30L * 366 * 24 * 60 * MINUTE));
		patient.addIdentifier(new PatientIdentifier("3220N0EF6", null, null));

		encounter = new Encounter(500);
		encounter.setPatient(patient);
		encounter.setLocation(new Location(7));
		encounter.getLocation().setName("Emergency");
		orderTime = new Date();
	}

	@Test
	public void shouldShowTestsSentTogetherAsOneStudyRow() throws Exception {
		TestOrder foot = order(1, "ORD-1", footXray, "Fell from a tree");
		TestOrder hand = order(2, "ORD-2", handXray, "Fell from a tree");
		foot.setAccessionNumber("ORD-1");
		hand.setAccessionNumber("ORD-1");

		RadiologyQueueRow row = service.toRow(Arrays.asList(foot, hand), orderables, new HashMap<Integer, OrderPayment>());

		assertThat(row.getOrderNumbers(), contains("ORD-1", "ORD-2"));
		assertThat(row.getTypeOfStudy(), equalTo("X-ray of left calcaneus, X-ray of right humerus"));
		assertThat(row.getClinicalHistory(), equalTo("Fell from a tree"));
		assertThat(row.getModality(), equalTo("DX"));
		assertThat(row.getPatientIdentifier(), equalTo("3220N0EF6"));
		assertThat(row.getPatientName(), equalTo("Jean Mugabo"));
		assertThat(row.getSex(), equalTo("M"));
		assertThat(row.getRequestingPhysician(), equalTo("Doc Hollywood"));
		assertThat(row.getRequestingDepartment(), equalTo("Emergency"));
		assertThat(row.getPacsStatus(), equalTo(PacsStatus.SENT));
		assertThat(row.getPaymentStatus(), equalTo(PaymentStatus.NOT_BILLED));
	}

	@Test
	public void shouldShowPacsProgress() throws Exception {
		TestOrder notSent = order(1, "ORD-1", footXray, null);
		assertThat(service.toRow(Collections.singletonList(notSent), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.NOT_SENT));

		// PACS reported the study as done but sent no link to its images
		TestOrder doneWithoutLink = order(2, "ORD-2", footXray, null);
		doneWithoutLink.setAccessionNumber("ORD-2");
		when(rwandaEmrService.getObsByOrder(doneWithoutLink)).thenReturn(Collections.singletonList(obs(studyConstruct)));
		RadiologyQueueRow doneWithoutLinkRow = service.toRow(Collections.singletonList(doneWithoutLink), orderables, new HashMap<>());
		assertThat(doneWithoutLinkRow.getPacsStatus(), equalTo(PacsStatus.STUDY_DONE_NO_LINK));
		assertThat(doneWithoutLinkRow.isProcessed(), equalTo(true));

		Concept imagesAvailable = concept(102, "Radiology images available");
		when(radiologyConfig.getRadiologyImagesAvailable()).thenReturn(imagesAvailable);
		TestOrder imaged = order(5, "ORD-5", footXray, null);
		imaged.setAccessionNumber("ORD-5");
		Obs study = obs(studyConstruct);
		Obs images = obs(imagesAvailable);
		images.setComment("https://pacs.example/viewer?AccessionNumber=ORD-5");
		study.addGroupMember(images);
		when(rwandaEmrService.getObsByOrder(imaged)).thenReturn(Collections.singletonList(study));
		RadiologyQueueRow imagedRow = service.toRow(Collections.singletonList(imaged), orderables, new HashMap<>());
		assertThat(imagedRow.getPacsStatus(), equalTo(PacsStatus.IMAGES_RECEIVED));
		assertThat(imagedRow.getImageUrl(), equalTo("https://pacs.example/viewer?AccessionNumber=ORD-5"));

		TestOrder reported = order(3, "ORD-3", footXray, null);
		reported.setAccessionNumber("ORD-3");
		when(rwandaEmrService.getObsByOrder(reported)).thenReturn(Arrays.asList(obs(studyConstruct), obs(reportConstruct)));
		assertThat(service.toRow(Collections.singletonList(reported), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.REPORTED));

		TestOrder cancelled = order(4, "ORD-4", footXray, null);
		cancelled.setAccessionNumber("ORD-4");
		FieldUtils.writeField(cancelled, "dateStopped", new Date(), true);
		assertThat(service.toRow(Collections.singletonList(cancelled), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.CANCELLED));
	}

	@Test
	public void shouldOpenADoneStudyWithoutALinkFromPacsInTheViewerByItsAccessionNumber() throws Exception {
		when(radiologyConfig.getImageViewerUrl("ORD-7")).thenReturn("https://pacs.example/Launch_Viewer?AccessionNumber=ORD-7");
		TestOrder done = order(7, "ORD-7", footXray, null);
		done.setAccessionNumber("ORD-7");
		when(rwandaEmrService.getObsByOrder(done)).thenReturn(Collections.singletonList(obs(studyConstruct)));

		RadiologyQueueRow row = service.toRow(Collections.singletonList(done), orderables, new HashMap<>());

		assertThat(row.getImageUrl(), equalTo("https://pacs.example/Launch_Viewer?AccessionNumber=ORD-7"));
		assertThat(row.getPacsStatus(), equalTo(PacsStatus.IMAGES_RECEIVED));

		// Not before PACS has done the study
		TestOrder sent = order(8, "ORD-8", footXray, null);
		sent.setAccessionNumber("ORD-8");
		when(radiologyConfig.getImageViewerUrl("ORD-8")).thenReturn("https://pacs.example/Launch_Viewer?AccessionNumber=ORD-8");
		RadiologyQueueRow sentRow = service.toRow(Collections.singletonList(sent), orderables, new HashMap<>());
		assertThat(sentRow.getImageUrl(), nullValue());
		assertThat(sentRow.getPacsStatus(), equalTo(PacsStatus.SENT));
	}

	@Test
	public void shouldOnlyShowAStudyReportedInOpenMrsAsReportedOnceEveryTestHasItsReport() throws Exception {
		when(radiologyConfig.isManualReportModality(Modality.DX)).thenReturn(true);
		TestOrder foot = order(1, "ORD-1", footXray, null);
		TestOrder hand = order(2, "ORD-2", handXray, null);
		foot.setAccessionNumber("ORD-1");
		hand.setAccessionNumber("ORD-1");
		when(rwandaEmrService.getObsByOrder(foot)).thenReturn(Arrays.asList(obs(studyConstruct), obs(reportConstruct)));
		when(rwandaEmrService.getObsByOrder(hand)).thenReturn(Collections.singletonList(obs(studyConstruct)));

		RadiologyQueueRow row = service.toRow(Arrays.asList(foot, hand), orderables, new HashMap<>());

		assertThat(row.getPacsStatus(), equalTo(PacsStatus.STUDY_DONE_NO_LINK));
		assertThat(row.getTests().get(0).isReported(), equalTo(true));
		assertThat(row.getTests().get(1).isReported(), equalTo(false));

		when(rwandaEmrService.getObsByOrder(hand)).thenReturn(Arrays.asList(obs(studyConstruct), obs(reportConstruct)));
		assertThat(service.toRow(Arrays.asList(foot, hand), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.REPORTED));

		// A study reported through PACS shares one report
		when(radiologyConfig.isManualReportModality(Modality.DX)).thenReturn(false);
		when(rwandaEmrService.getObsByOrder(hand)).thenReturn(Collections.singletonList(obs(studyConstruct)));
		assertThat(service.toRow(Arrays.asList(foot, hand), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.REPORTED));
	}

	@Test
	public void shouldShowTheLatestCreatinineOnCtStudiesOnly() {
		RadiologyQueueRow ct = new RadiologyQueueRow();
		ct.setModality("CT");
		ct.setPatientId(10);
		ct.setPacsStatus(PacsStatus.SENT);
		RadiologyQueueRow xray = new RadiologyQueueRow();
		xray.setModality("DX");
		xray.setPatientId(10);
		xray.setPacsStatus(PacsStatus.SENT);
		Map<Integer, RadiologyQueueRow.Creatinine> latest = new HashMap<Integer, RadiologyQueueRow.Creatinine>();
		latest.put(10, new RadiologyQueueRow.Creatinine("1.2", "mg/dL", orderTime, false));

		RadiologyQueueService.setCreatinine(Arrays.asList(ct, xray), latest);

		assertThat(ct.getCreatinine().getDisplay(), equalTo("1.2 mg/dL"));
		assertThat(xray.getCreatinine(), nullValue());
	}

	@Test
	public void shouldFlagACreatinineAboveTheNormalRange() {
		org.openmrs.ConceptNumeric creatinine = new org.openmrs.ConceptNumeric(790);
		creatinine.setUnits("mg/dL");
		creatinine.setHiNormal(1.3);

		RadiologyQueueRow.Creatinine high = RadiologyQueueService.toCreatinine(2.10, null, orderTime, creatinine);
		assertThat(high.getDisplay(), equalTo("2.1 mg/dL"));
		assertThat(high.isHigh(), equalTo(true));
		assertThat(RadiologyQueueService.toCreatinine(1.0, null, orderTime, creatinine).isHigh(), equalTo(false));
		assertThat(RadiologyQueueService.toCreatinine(1.0, null, orderTime, creatinine).getDisplay(), equalTo("1 mg/dL"));
		assertThat(RadiologyQueueService.toCreatinine(null, "Pending", orderTime, creatinine).getDisplay(), equalTo("Pending"));
	}

	@Test
	public void shouldUseTheTimeTheRequestWasCreatedRatherThanTheDateOnlyActivationDate() throws Exception {
		TestOrder foot = order(1, "ORD-1", footXray, null);
		// Forms record only the encounter date as the activation date
		foot.setDateActivated(org.apache.commons.lang.time.DateUtils.truncate(orderTime, java.util.Calendar.DATE));

		RadiologyQueueRow row = service.toRow(Collections.singletonList(foot), orderables, new HashMap<>());

		assertThat(row.getTimeRequested(), equalTo(orderTime));
	}

	@Test
	public void shouldShowWhetherTheExamWasProcessed() throws Exception {
		TestOrder notSent = order(1, "ORD-1", footXray, null);
		assertThat(service.toRow(Collections.singletonList(notSent), orderables, new HashMap<>()).isProcessed(), equalTo(false));

		TestOrder sent = order(2, "ORD-2", footXray, null);
		sent.setAccessionNumber("ORD-2");
		assertThat(service.toRow(Collections.singletonList(sent), orderables, new HashMap<>()).isProcessed(), equalTo(false));

		TestOrder imaged = order(3, "ORD-3", footXray, null);
		imaged.setAccessionNumber("ORD-3");
		when(rwandaEmrService.getObsByOrder(imaged)).thenReturn(Collections.singletonList(obs(studyConstruct)));
		assertThat(service.toRow(Collections.singletonList(imaged), orderables, new HashMap<>()).isProcessed(), equalTo(true));

		TestOrder completed = order(4, "ORD-4", footXray, null);
		completed.setFulfillerStatus(org.openmrs.Order.FulfillerStatus.COMPLETED);
		RadiologyQueueRow completedRow = service.toRow(Collections.singletonList(completed), orderables, new HashMap<>());
		assertThat(completedRow.isProcessed(), equalTo(true));
		assertThat(completedRow.getProcessedLabel(), equalTo("Processed"));

		TestOrder cancelled = order(5, "ORD-5", footXray, null);
		cancelled.setFulfillerStatus(org.openmrs.Order.FulfillerStatus.COMPLETED);
		FieldUtils.writeField(cancelled, "dateStopped", new Date(), true);
		assertThat(service.toRow(Collections.singletonList(cancelled), orderables, new HashMap<>()).isProcessed(), equalTo(false));
	}

	@Test
	public void shouldShowUrgencyAndWhetherThePatientIsReadyForTheExam() throws Exception {
		TestOrder foot = order(1, "ORD-1", footXray, null);
		TestOrder hand = order(2, "ORD-2", handXray, null);
		hand.setUrgency(org.openmrs.Order.Urgency.STAT);
		foot.setAccessionNumber("ORD-1");
		hand.setAccessionNumber("ORD-1");
		Map<Integer, OrderPayment> payments = new HashMap<Integer, OrderPayment>();
		payments.put(1, payment(PaymentStatus.PAID, orderTime));
		payments.put(2, payment(PaymentStatus.PAID, orderTime));

		RadiologyQueueRow row = service.toRow(Arrays.asList(foot, hand), orderables, payments);

		assertThat(row.isStat(), equalTo(true));
		assertThat(row.getTests().size(), equalTo(2));
		assertThat(row.getTests().get(1).isStat(), equalTo(true));
		// Paid, sent to PACS and not done yet
		assertThat(row.isReadyForExam(), equalTo(true));

		payments.put(2, payment(PaymentStatus.NOT_PAID, null));
		assertThat(service.toRow(Arrays.asList(foot, hand), orderables, payments).isReadyForExam(), equalTo(false));
	}

	@Test
	public void shouldShowTheImageLinkAndReportReceivedFromPacs() throws Exception {
		Concept imagesAvailable = concept(102, "Radiology images available");
		Concept reportType = concept(103, "Type of radiology report");
		Concept finalReport = concept(104, "Final");
		when(radiologyConfig.getRadiologyImagesAvailable()).thenReturn(imagesAvailable);
		when(radiologyConfig.getRadiologyReportType()).thenReturn(reportType);
		TestOrder foot = order(1, "ORD-1", footXray, null);
		foot.setAccessionNumber("ORD-1");

		Obs study = obs(studyConstruct);
		Obs images = obs(imagesAvailable);
		images.setComment("^https://pacs.example/viewer?AccessionNumber=ORD-1");
		study.addGroupMember(images);
		Encounter studyEncounter = new Encounter();
		studyEncounter.setEncounterDatetime(new Date(orderTime.getTime() + 20 * MINUTE));
		study.setEncounter(studyEncounter);
		Obs report = obs(reportConstruct);
		Obs status = obs(reportType);
		status.setValueCoded(finalReport);
		report.addGroupMember(status);
		Encounter reportEncounter = new Encounter();
		reportEncounter.setEncounterDatetime(new Date(orderTime.getTime() + 90 * MINUTE));
		report.setEncounter(reportEncounter);
		when(rwandaEmrService.getObsByOrder(foot)).thenReturn(Arrays.asList(study, report));

		RadiologyQueueRow row = service.toRow(Collections.singletonList(foot), orderables, new HashMap<>());

		assertThat(row.getPacsStatus(), equalTo(PacsStatus.REPORTED));
		assertThat(row.getImageUrl(), equalTo("https://pacs.example/viewer?AccessionNumber=ORD-1"));
		assertThat(row.getImagesReceivedAt(), equalTo(studyEncounter.getEncounterDatetime()));
		assertThat(row.getReportedAt(), equalTo(reportEncounter.getEncounterDatetime()));
		assertThat(row.getReportStatus(), equalTo("Final"));
		assertThat(row.getReportOrderId(), equalTo(1));
		assertThat(row.isProcessed(), equalTo(true));
		assertThat(row.isReadyForExam(), equalTo(false));
	}

	@Test
	public void shouldCombineThePaymentOfTheTestsInAStudy() throws Exception {
		TestOrder foot = order(1, "ORD-1", footXray, null);
		TestOrder hand = order(2, "ORD-2", handXray, null);
		List<TestOrder> study = Arrays.asList(foot, hand);
		Date paidAt = new Date(orderTime.getTime() + 30 * MINUTE);

		assertThat(paymentOf(study, payment(PaymentStatus.PAID, paidAt), payment(PaymentStatus.PAID, null)).getPaymentStatus(),
				equalTo(PaymentStatus.PAID));
		assertThat(paymentOf(study, payment(PaymentStatus.PAID, paidAt), payment(PaymentStatus.NOT_PAID, null)).getPaymentStatus(),
				equalTo(PaymentStatus.PARTIALLY_PAID));
		assertThat(paymentOf(study, payment(PaymentStatus.NOT_PAID, null), payment(PaymentStatus.NOT_PAID, null)).getPaymentStatus(),
				equalTo(PaymentStatus.NOT_PAID));
		// A test that was not billed does not count against the ones that were
		RadiologyQueueRow row = paymentOf(study, payment(PaymentStatus.PAID, paidAt), payment(PaymentStatus.NOT_BILLED, null));
		assertThat(row.getPaymentStatus(), equalTo(PaymentStatus.PAID));
		assertThat(row.getTimeOfPayment(), equalTo(paidAt));
		assertThat(paymentOf(study, payment(PaymentStatus.NOT_BILLED, null), payment(PaymentStatus.NOT_BILLED, null)).getPaymentStatus(),
				equalTo(PaymentStatus.NOT_BILLED));
	}

	private RadiologyQueueRow paymentOf(List<TestOrder> study, OrderPayment first, OrderPayment second) {
		Map<Integer, OrderPayment> payments = new HashMap<Integer, OrderPayment>();
		payments.put(study.get(0).getOrderId(), first);
		payments.put(study.get(1).getOrderId(), second);
		RadiologyQueueRow row = new RadiologyQueueRow();
		RadiologyQueueService.setPayment(row, study, payments);
		return row;
	}

	private OrderPayment payment(PaymentStatus status, Date paidAt) {
		return new OrderPayment(status, paidAt);
	}

	private TestOrder order(Integer orderId, String orderNumber, Concept concept, String clinicalHistory) throws Exception {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setPatient(patient);
		order.setConcept(concept);
		order.setClinicalHistory(clinicalHistory);
		order.setDateActivated(orderTime);
		order.setDateCreated(orderTime);
		Provider provider = new Provider();
		Person person = new Person();
		person.addName(new PersonName("Doc", null, "Hollywood"));
		provider.setPerson(person);
		order.setOrderer(provider);
		encounter.addOrder(order);
		FieldUtils.writeField(order, "orderNumber", orderNumber, true);
		return order;
	}

	private Obs obs(Concept concept) {
		Obs obs = new Obs();
		obs.setConcept(concept);
		return obs;
	}

	private Concept concept(Integer id, String name) {
		Concept concept = new Concept(id);
		concept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		return concept;
	}
}
