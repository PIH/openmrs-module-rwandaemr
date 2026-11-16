package org.openmrs.module.rwandaemr.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import javax.transaction.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationConfig;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationService;

public class LabResultNotificationEventHandlerTest {

	private LabNotificationConfig config;
	private LabNotificationService notificationService;
	private LabResultNotificationEventHandler handler;

	@BeforeEach
	public void setUp() {
		config = mock(LabNotificationConfig.class);
		notificationService = mock(LabNotificationService.class);
		handler = new LabResultNotificationEventHandler(config, notificationService);
		when(config.isEnabled()).thenReturn(true);
	}

	@Test
	public void shouldNotifyOnceAfterCompletedTestOrderIsCommitted() {
		TestOrder order = completedOrder("order-uuid");
		OpenmrsObjectEvent event = event(order, OpenmrsObjectEvent.Operation.UPDATE);

		handler.afterTransactionCompletion(1, Arrays.asList(event, event), Status.STATUS_COMMITTED);

		verify(notificationService).notifyPatient("order-uuid", null, null, false);
	}

	@Test
	public void shouldNotNotifyBeforeOrderIsCompleted() {
		TestOrder order = completedOrder("order-uuid");
		order.setFulfillerStatus(Order.FulfillerStatus.IN_PROGRESS);

		handler.afterTransactionCompletion(1,
				Collections.singletonList(event(order, OpenmrsObjectEvent.Operation.UPDATE)), Status.STATUS_COMMITTED);

		verify(notificationService, never()).notifyPatient("order-uuid", null, null, false);
	}

	@Test
	public void shouldNotNotifyWhenTransactionRollsBack() {
		TestOrder order = completedOrder("order-uuid");

		handler.afterTransactionCompletion(1,
				Collections.singletonList(event(order, OpenmrsObjectEvent.Operation.UPDATE)), Status.STATUS_ROLLEDBACK);

		verify(notificationService, never()).notifyPatient("order-uuid", null, null, false);
	}

	@Test
	public void shouldNotNotifyWhenFeatureIsDisabled() {
		when(config.isEnabled()).thenReturn(false);
		TestOrder order = completedOrder("order-uuid");

		handler.afterTransactionCompletion(1,
				Collections.singletonList(event(order, OpenmrsObjectEvent.Operation.UPDATE)), Status.STATUS_COMMITTED);

		verify(notificationService, never()).notifyPatient("order-uuid", null, null, false);
	}

	@Test
	public void shouldNotNotifyForNonLabOrders() {
		Order order = new Order();
		order.setUuid("order-uuid");
		order.setFulfillerStatus(Order.FulfillerStatus.COMPLETED);

		handler.afterTransactionCompletion(1,
				Collections.singletonList(event(order, OpenmrsObjectEvent.Operation.UPDATE)), Status.STATUS_COMMITTED);

		verify(notificationService, never()).notifyPatient("order-uuid", null, null, false);
	}

	private TestOrder completedOrder(String uuid) {
		TestOrder order = new TestOrder();
		order.setUuid(uuid);
		order.setFulfillerStatus(Order.FulfillerStatus.COMPLETED);
		return order;
	}

	private OpenmrsObjectEvent event(Order order, OpenmrsObjectEvent.Operation operation) {
		return new OpenmrsObjectEvent(order, operation, 1);
	}
}
