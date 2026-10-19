package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.BillItem;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class OrderPaymentLookupTest {

	private static final long MINUTE = 60 * 1000L;

	private Patient patient;
	private Encounter encounter;
	private Date orderTime;
	private Concept footXray;
	private Concept handXray;

	@BeforeEach
	public void setUp() {
		patient = new Patient(7);
		encounter = new Encounter(70);
		orderTime = new Date();
		footXray = new Concept(101);
		handXray = new Concept(102);
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
	public void shouldMatchEachOrderToTheClosestBillItemForTheSamePatientAndTest() {
		TestOrder foot = order(1, footXray);
		TestOrder hand = order(2, handXray);
		TestOrder otherFoot = order(3, footXray);
		otherFoot.setDateCreated(new Date(orderTime.getTime() + 5 * 60 * MINUTE));

		BillItem footItem = billItem(footXray, orderTime.getTime() + 2000, 1);
		footItem.lastPaymentDate = new Date(orderTime.getTime() + 40 * MINUTE);
		BillItem laterFootItem = billItem(footXray, orderTime.getTime() + 5 * 60 * MINUTE + 3000, 0);
		BillItem otherPatientsItem = billItem(handXray, orderTime.getTime() + 1000, 1);
		otherPatientsItem.patientId = 99;
		BillItem tooOld = billItem(handXray, orderTime.getTime() - 3 * 60 * MINUTE, 1);

		Map<Integer, OrderPayment> payments = OrderPaymentLookup.matchOrdersToBillItems(
				Arrays.asList(foot, hand, otherFoot), Arrays.asList(laterFootItem, footItem, otherPatientsItem, tooOld));

		assertThat(payments.get(1).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(payments.get(1).getLastPaymentDate(), equalTo(footItem.lastPaymentDate));
		assertThat(payments.get(2).getStatus(), equalTo(PaymentStatus.NOT_BILLED));
		assertThat(payments.get(3).getStatus(), equalTo(PaymentStatus.NOT_PAID));
		assertThat(payments.get(3).getLastPaymentDate(), nullValue());
	}

	@Test
	public void shouldMatchTheTestsOfASetBilledAtTheSetLevelToTheSetsBillItem() {
		Concept hemogram = new Concept(200);
		Concept hemoglobin = new Concept(201);
		Concept platelets = new Concept(202);
		Map<Concept, Concept> billedAtSetLevel = new HashMap<Concept, Concept>();
		billedAtSetLevel.put(hemoglobin, hemogram);
		billedAtSetLevel.put(platelets, hemogram);

		TestOrder hemoglobinOrder = order(1, hemoglobin);
		TestOrder plateletsOrder = order(2, platelets);
		TestOrder handOrder = order(3, handXray);
		BillItem hemogramItem = billItem(hemogram, orderTime.getTime() + 1000, 1);

		Map<Integer, OrderPayment> payments = OrderPaymentLookup.matchOrdersToBillItems(
				Arrays.asList(hemoglobinOrder, plateletsOrder, handOrder), Collections.singletonList(hemogramItem),
				billedAtSetLevel);

		assertThat(payments.get(1).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(payments.get(2).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(payments.get(3).getStatus(), equalTo(PaymentStatus.NOT_BILLED));
	}

	@Test
	public void shouldNotShareASetsBillItemWithTheSameSetOrderedInAnotherEncounter() {
		Concept hemogram = new Concept(200);
		Concept hemoglobin = new Concept(201);
		Map<Concept, Concept> billedAtSetLevel = Collections.singletonMap(hemoglobin, hemogram);

		TestOrder first = order(1, hemoglobin);
		TestOrder second = order(2, hemoglobin);
		second.setEncounter(new Encounter(71));
		second.setDateCreated(new Date(orderTime.getTime() + 2 * 60 * MINUTE));
		BillItem firstItem = billItem(hemogram, orderTime.getTime() + 1000, 1);

		Map<Integer, OrderPayment> payments = OrderPaymentLookup.matchOrdersToBillItems(
				Arrays.asList(first, second), Collections.singletonList(firstItem), billedAtSetLevel);

		assertThat(payments.get(1).getStatus(), equalTo(PaymentStatus.PAID));
		assertThat(payments.get(2).getStatus(), equalTo(PaymentStatus.NOT_BILLED));
	}

	private TestOrder order(Integer orderId, Concept concept) {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setPatient(patient);
		order.setEncounter(encounter);
		order.setConcept(concept);
		order.setDateActivated(orderTime);
		order.setDateCreated(orderTime);
		return order;
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
}
