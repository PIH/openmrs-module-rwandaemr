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
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyPaymentLookup.BillItem;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PacsStatus;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow.PaymentStatus;

import java.math.BigDecimal;
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

		TestOrder imaged = order(2, "ORD-2", footXray, null);
		imaged.setAccessionNumber("ORD-2");
		when(rwandaEmrService.getObsByOrder(imaged)).thenReturn(Collections.singletonList(obs(studyConstruct)));
		assertThat(service.toRow(Collections.singletonList(imaged), orderables, new HashMap<>()).getPacsStatus(),
				equalTo(PacsStatus.IMAGES_RECEIVED));

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

	@Test
	public void shouldWorkOutTheStatusOfABillItem() {
		assertThat(item(1, 0, false, new BigDecimal("1500")).getStatus(), equalTo(PaymentStatus.NOT_PAID));
		assertThat(item(1, 1, false, new BigDecimal("1500")).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(item(2, 1, false, new BigDecimal("1500")).getStatus(), equalTo(PaymentStatus.PARTIALLY_PAID));
		assertThat(item(1, 0, true, new BigDecimal("1500")).getStatus(), equalTo(PaymentStatus.PAID));
		// Fully covered by insurance: nothing for the patient to pay
		assertThat(item(1, 0, false, BigDecimal.ZERO).getStatus(), equalTo(PaymentStatus.PAID));
	}

	@Test
	public void shouldMatchEachOrderToTheClosestBillItemForTheSamePatientAndTest() throws Exception {
		TestOrder foot = order(1, "ORD-1", footXray, null);
		TestOrder hand = order(2, "ORD-2", handXray, null);
		TestOrder otherFoot = order(3, "ORD-3", footXray, null);
		otherFoot.setDateCreated(new Date(orderTime.getTime() + 5 * 60 * MINUTE));

		BillItem footItem = billItem(footXray, orderTime.getTime() + 2000, 1);
		footItem.lastPaymentDate = new Date(orderTime.getTime() + 40 * MINUTE);
		BillItem laterFootItem = billItem(footXray, orderTime.getTime() + 5 * 60 * MINUTE + 3000, 0);
		BillItem otherPatientsItem = billItem(handXray, orderTime.getTime() + 1000, 1);
		otherPatientsItem.patientId = 99;
		BillItem tooOld = billItem(handXray, orderTime.getTime() - 3 * 60 * MINUTE, 1);

		Map<Integer, OrderPayment> payments = RadiologyPaymentLookup.matchOrdersToBillItems(
				Arrays.asList(foot, hand, otherFoot), Arrays.asList(laterFootItem, footItem, otherPatientsItem, tooOld));

		assertThat(payments.get(1).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(payments.get(1).getLastPaymentDate(), equalTo(footItem.lastPaymentDate));
		assertThat(payments.get(2).getStatus(), equalTo(PaymentStatus.NOT_BILLED));
		assertThat(payments.get(3).getStatus(), equalTo(PaymentStatus.NOT_PAID));
		assertThat(payments.get(3).getLastPaymentDate(), nullValue());
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

	private BillItem item(int quantity, int paidQuantity, boolean markedPaid, BigDecimal patientDue) {
		BillItem item = new BillItem();
		item.quantity = new BigDecimal(quantity);
		item.paidQuantity = new BigDecimal(paidQuantity);
		item.markedPaid = markedPaid;
		item.patientDue = patientDue;
		return item;
	}

	private BillItem billItem(Concept concept, long createdTime, int paidQuantity) {
		BillItem item = item(1, paidQuantity, false, new BigDecimal("1500"));
		item.patientId = patient.getPatientId();
		item.conceptId = concept.getConceptId();
		item.createdDate = new Date(createdTime);
		return item;
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
