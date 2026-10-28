package org.openmrs.module.rwandaemr.pathology.queue;

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
import org.openmrs.User;
import org.openmrs.api.UserService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.pathology.PathologyCase;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseRepository.StatusChange;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PathologyQueueServiceTest {

	private UserService userService;
	private PathologyQueueService service;
	private Concept hospital;
	private Concept hospitalNonCoded;
	private Concept specimen;
	private Concept specimenNonCoded;
	private Concept otherTests;
	private Concept biopsy;
	private Concept boneMarrow;
	private Encounter encounter;
	private Date orderTime;

	@BeforeEach
	public void setUp() {
		userService = mock(UserService.class);
		PathologyConfig pathologyConfig = mock(PathologyConfig.class);
		service = new PathologyQueueService(null, null, userService, pathologyConfig, null, mock(RwandaEmrConfig.class), null);

		hospital = concept(1, "Surgery facility");
		hospitalNonCoded = concept(2, "Surgery facility, non-coded");
		specimen = concept(3, "Specimen");
		specimenNonCoded = concept(4, "Specimen, non-coded");
		otherTests = concept(5, "Tests ordered, non-coded");
		when(pathologyConfig.getHospitalOfOrigin()).thenReturn(hospital);
		when(pathologyConfig.getHospitalOfOriginNonCoded()).thenReturn(hospitalNonCoded);
		when(pathologyConfig.getSpecimen()).thenReturn(specimen);
		when(pathologyConfig.getSpecimenNonCoded()).thenReturn(specimenNonCoded);
		when(pathologyConfig.getOtherTestsRequested()).thenReturn(otherTests);
		biopsy = concept(10, "Examination of a biopsy");
		boneMarrow = concept(11, "Bone marrow aspirate and/or biopsy");

		Patient patient = new Patient(20);
		patient.setUuid("patient-uuid");
		patient.addName(new PersonName("Aline", null, "Uwase"));
		patient.setGender("F");
		patient.setBirthdate(new Date(System.currentTimeMillis() - 40L * 366 * 24 * 60 * 60 * 1000));
		patient.addIdentifier(new PatientIdentifier("3220N0EF6", null, null));

		encounter = new Encounter(500);
		encounter.setUuid("encounter-uuid");
		encounter.setPatient(patient);
		encounter.setLocation(new Location(7));
		encounter.getLocation().setName("Butaro Hospital");
		orderTime = new Date(System.currentTimeMillis() - 60 * 60 * 1000);
	}

	@Test
	public void shouldShowTheTestsOfARequestAsOneRow() throws Exception {
		TestOrder first = order(1, "ORD-1", biopsy);
		TestOrder second = order(2, "ORD-2", boneMarrow);

		PathologyQueueRow row = toRow(Arrays.asList(first, second), null, new HashMap<Integer, OrderPayment>());

		assertThat(row.getEncounterId(), equalTo(500));
		assertThat(row.getEncounterUuid(), equalTo("encounter-uuid"));
		assertThat(row.getOrderNumbers(), contains("ORD-1", "ORD-2"));
		assertThat(row.getTests(), contains("Examination of a biopsy", "Bone marrow aspirate and/or biopsy"));
		assertThat(row.getPatientIdentifier(), equalTo("3220N0EF6"));
		assertThat(row.getPatientName(), equalTo("Aline Uwase"));
		assertThat(row.getPatientUuid(), equalTo("patient-uuid"));
		assertThat(row.getSex(), equalTo("F"));
		assertThat(row.getRequestingPhysician(), equalTo("Doc Rugwiro"));
		assertThat(row.getTimeRequested(), equalTo(orderTime));
		assertThat(row.getPaymentStatus(), equalTo(PaymentStatus.NOT_BILLED));
	}

	@Test
	public void shouldBeUrgentWhenATestIsUrgentAndShowTheAdditionalInformation() throws Exception {
		TestOrder routine = order(1, "ORD-1", biopsy);
		routine.setOrderReasonNonCoded(" Mass in the left breast ");
		TestOrder urgent = order(2, "ORD-2", boneMarrow);
		urgent.setUrgency(org.openmrs.Order.Urgency.STAT);

		assertThat(toRow(Collections.singletonList(routine), null, new HashMap<Integer, OrderPayment>()).isUrgent(),
				equalTo(false));
		PathologyQueueRow row = toRow(Arrays.asList(routine, urgent), null, new HashMap<Integer, OrderPayment>());
		assertThat(row.isUrgent(), equalTo(true));
		assertThat(row.getAdditionalInformation(), equalTo("Mass in the left breast"));
	}

	@Test
	public void shouldBeOrderedUntilTheLabHasACase() throws Exception {
		PathologyQueueRow row = toRow(Collections.singletonList(order(1, "ORD-1", biopsy)), null,
				new HashMap<Integer, OrderPayment>());

		assertThat(row.getStatus(), equalTo(PathologyStatus.ORDERED));
		assertThat(row.getStatusLabel(), equalTo("Ordered"));
		assertThat(row.getAccessionNumber(), nullValue());
		assertThat(row.isCancelled(), equalTo(false));
		assertThat(row.getHistory(), empty());
	}

	@Test
	public void shouldShowTheCaseAndWhoMovedIt() throws Exception {
		PathologyCase pathologyCase = new PathologyCase();
		pathologyCase.setPathologyCaseId(3);
		pathologyCase.setAccessionNumber("PAT2026-0007");
		pathologyCase.setStatus(PathologyStatus.GROSSED);
		Date received = new Date(orderTime.getTime() + 60000);
		Date grossed = new Date(orderTime.getTime() + 120000);
		pathologyCase.setDateCreated(received);
		pathologyCase.setDateChanged(grossed);
		User labTech = new User(4);
		labTech.setPerson(new Person(40));
		labTech.addName(new PersonName("Eric", null, "Habimana"));
		when(userService.getUser(4)).thenReturn(labTech);
		List<StatusChange> history = Arrays.asList(new StatusChange(PathologyStatus.RECEIVED, null, 4, received),
				new StatusChange(PathologyStatus.GROSSED, null, 4, grossed));

		PathologyQueueRow row = service.toRow(encounter, Collections.singletonList(order(1, "ORD-1", biopsy)),
				pathologyCase, history, new HashMap<Integer, OrderPayment>(), new HashMap<Integer, String>());

		assertThat(row.getCaseId(), equalTo(3));
		assertThat(row.getAccessionNumber(), equalTo("PAT2026-0007"));
		assertThat(row.getStatus(), equalTo(PathologyStatus.GROSSED));
		assertThat(row.getStatusChangedAt(), equalTo(grossed));
		assertThat(row.getHistory(), hasSize(2));
		assertThat(row.getHistory().get(0).getStatus(), equalTo(PathologyStatus.RECEIVED));
		assertThat(row.getHistory().get(1).getBy(), equalTo("Eric Habimana"));
	}

	@Test
	public void shouldBeCancelledWhenEveryTestWasCancelledBeforeReception() throws Exception {
		TestOrder cancelled = order(1, "ORD-1", biopsy);
		setDateStopped(cancelled, new Date());

		PathologyQueueRow row = toRow(Collections.singletonList(cancelled), null, new HashMap<Integer, OrderPayment>());

		assertThat(row.isCancelled(), equalTo(true));
		assertThat(row.getStatusLabel(), equalTo("Cancelled"));
		assertThat(row.getOrderNumbers(), contains("ORD-1"));
	}

	@Test
	public void shouldLeaveOutATestCancelledFromARequestWithOthers() throws Exception {
		TestOrder cancelled = order(1, "ORD-1", biopsy);
		setDateStopped(cancelled, new Date());
		TestOrder kept = order(2, "ORD-2", boneMarrow);

		PathologyQueueRow row = toRow(Arrays.asList(cancelled, kept), null, new HashMap<Integer, OrderPayment>());

		assertThat(row.isCancelled(), equalTo(false));
		assertThat(row.getOrderNumbers(), contains("ORD-2"));
	}

	@Test
	public void shouldCombineThePaymentOfTheTests() throws Exception {
		TestOrder first = order(1, "ORD-1", biopsy);
		TestOrder second = order(2, "ORD-2", boneMarrow);
		Map<Integer, OrderPayment> payments = new HashMap<Integer, OrderPayment>();
		payments.put(1, new OrderPayment(PaymentStatus.PAID, orderTime));
		payments.put(2, new OrderPayment(PaymentStatus.NOT_PAID, null));

		PathologyQueueRow row = toRow(Arrays.asList(first, second), null, payments);

		assertThat(row.getPaymentStatus(), equalTo(PaymentStatus.PARTIALLY_PAID));
		assertThat(row.getTimeOfPayment(), equalTo(orderTime));
	}

	@Test
	public void shouldShowWhatTheRequestFormRecorded() throws Exception {
		Concept kirehe = concept(30, "Kirehe Hospital");
		encounter.addObs(obs(hospital, kirehe, null));
		encounter.addObs(obs(otherTests, null, "Cytogenetics"));
		encounter.addObs(specimenGroup("057c58a8-2623-4374-a288-c0d62d5b4318", concept(32, "Other non-coded"),
				"Left axillary lymph node"));
		encounter.addObs(specimenGroup("a967b0ce-1f05-473e-9d6e-c38308b23e25", concept(31, "Breast tissue"), null));

		PathologyQueueRow row = toRow(Collections.singletonList(order(1, "ORD-1", biopsy)), null,
				new HashMap<Integer, OrderPayment>());

		assertThat(row.getHospitalOfOrigin(), equalTo("Kirehe Hospital"));
		assertThat(row.getOtherTestsRequested(), equalTo("Cytogenetics"));
		assertThat(row.getSpecimens(), contains("A: Breast tissue", "D: Left axillary lymph node"));
	}

	@Test
	public void shouldPreferTheHospitalTypedInAndElseWhereTheRequestWasEntered() throws Exception {
		List<TestOrder> orders = Collections.singletonList(order(1, "ORD-1", biopsy));
		assertThat(toRow(orders, null, new HashMap<Integer, OrderPayment>()).getHospitalOfOrigin(),
				equalTo("Butaro Hospital"));

		encounter.addObs(obs(hospital, concept(33, "Other non-coded"), null));
		encounter.addObs(obs(hospitalNonCoded, null, "Nyagatare Hospital"));
		assertThat(toRow(orders, null, new HashMap<Integer, OrderPayment>()).getHospitalOfOrigin(),
				equalTo("Nyagatare Hospital"));
	}

	private PathologyQueueRow toRow(List<TestOrder> orders, PathologyCase pathologyCase, Map<Integer, OrderPayment> payments) {
		return service.toRow(encounter, orders, pathologyCase, Collections.<StatusChange>emptyList(), payments,
				new HashMap<Integer, String>());
	}

	private TestOrder order(Integer orderId, String orderNumber, Concept concept) throws Exception {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setPatient(encounter.getPatient());
		order.setConcept(concept);
		order.setDateActivated(orderTime);
		order.setDateCreated(orderTime);
		Field orderNumberField = org.openmrs.Order.class.getDeclaredField("orderNumber");
		orderNumberField.setAccessible(true);
		orderNumberField.set(order, orderNumber);
		Provider orderer = new Provider(9);
		orderer.setPerson(new Person(90));
		orderer.getPerson().addName(new PersonName("Doc", null, "Rugwiro"));
		order.setOrderer(orderer);
		order.setEncounter(encounter);
		return order;
	}

	private void setDateStopped(TestOrder order, Date date) throws Exception {
		Field field = org.openmrs.Order.class.getDeclaredField("dateStopped");
		field.setAccessible(true);
		field.set(order, date);
	}

	private Obs obs(Concept question, Concept valueCoded, String valueText) {
		Obs obs = new Obs();
		obs.setConcept(question);
		obs.setValueCoded(valueCoded);
		obs.setValueText(valueText);
		return obs;
	}

	private Obs specimenGroup(String groupingConceptUuid, Concept specimenAnswer, String specimenText) {
		Obs group = new Obs();
		Concept groupingConcept = concept(100 + encounter.getAllObs().size(), "Specimen construct");
		groupingConcept.setUuid(groupingConceptUuid);
		group.setConcept(groupingConcept);
		group.addGroupMember(obs(specimen, specimenAnswer, null));
		if (specimenText != null) {
			group.addGroupMember(obs(specimenNonCoded, null, specimenText));
		}
		return group;
	}

	private Concept concept(Integer id, String name) {
		Concept concept = new Concept(id);
		concept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		return concept;
	}
}
