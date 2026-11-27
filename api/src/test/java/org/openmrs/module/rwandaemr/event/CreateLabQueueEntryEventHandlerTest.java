package org.openmrs.module.rwandaemr.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OpenmrsObject;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.queue.QueueService;

public class CreateLabQueueEntryEventHandlerTest {

	private QueueService queueService;

	private CreateLabQueueEntryEventHandler handler;

	@BeforeEach
	public void setUp() {
		queueService = mock(QueueService.class);
		handler = new CreateLabQueueEntryEventHandler() {
			@Override
			QueueService getQueueService() {
				return queueService;
			}
		};
	}

	@Test
	public void shouldQueuePatientOnceForSeveralNewTestOrders() {
		Patient patient = patient("p1");
		Encounter encounter = new Encounter();
		List<OpenmrsObjectEvent> events = Arrays.asList(
				created(testOrder(patient, encounter, Order.Action.NEW)),
				created(testOrder(patient, encounter, Order.Action.NEW)));

		handler.beforeTransactionCompletion(1, events);

		verify(queueService, times(1)).addPatientToLaboratoryQueue(patient, encounter, CreateLabQueueEntryEventHandler.REASON);
	}

	@Test
	public void shouldQueueEachPatientWithNewTestOrders() {
		Patient p1 = patient("p1");
		Patient p2 = patient("p2");
		handler.beforeTransactionCompletion(1, Arrays.asList(
				created(testOrder(p1, null, Order.Action.NEW)),
				created(testOrder(p2, null, Order.Action.NEW))));

		verify(queueService).addPatientToLaboratoryQueue(eq(p1), any(), any());
		verify(queueService).addPatientToLaboratoryQueue(eq(p2), any(), any());
	}

	@Test
	public void shouldIgnoreNonNewTestOrdersOtherOrdersAndNestedTransactions() {
		Patient patient = patient("p1");
		DrugOrder drugOrder = new DrugOrder();
		drugOrder.setPatient(patient);
		List<OpenmrsObjectEvent> events = Arrays.asList(
				created(testOrder(patient, null, Order.Action.DISCONTINUE)),
				created(drugOrder),
				new OpenmrsObjectEvent(testOrder(patient, null, Order.Action.NEW), OpenmrsObjectEvent.Operation.UPDATE, 1));

		handler.beforeTransactionCompletion(1, events);
		handler.beforeTransactionCompletion(2, Collections.singletonList(created(testOrder(patient, null, Order.Action.NEW))));

		verifyNoInteractions(queueService);
	}

	@Test
	public void shouldNotPropagateQueueFailures() {
		Patient p1 = patient("p1");
		Patient p2 = patient("p2");
		when(queueService.addPatientToLaboratoryQueue(eq(p1), any(), any())).thenThrow(new RuntimeException("boom"));

		handler.beforeTransactionCompletion(1, Arrays.asList(
				created(testOrder(p1, null, Order.Action.NEW)),
				created(testOrder(p2, null, Order.Action.NEW))));

		verify(queueService).addPatientToLaboratoryQueue(eq(p2), any(), any());
		verify(queueService, never()).getLaboratoryServicePoint();
	}

	private Patient patient(String uuid) {
		Patient patient = new Patient();
		patient.setUuid(uuid);
		return patient;
	}

	private TestOrder testOrder(Patient patient, Encounter encounter, Order.Action action) {
		TestOrder order = new TestOrder();
		order.setPatient(patient);
		order.setEncounter(encounter);
		order.setAction(action);
		return order;
	}

	private OpenmrsObjectEvent created(OpenmrsObject object) {
		return new OpenmrsObjectEvent(object, OpenmrsObjectEvent.Operation.CREATE, 1);
	}
}
