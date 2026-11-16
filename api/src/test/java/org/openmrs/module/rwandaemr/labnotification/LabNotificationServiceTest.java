package org.openmrs.module.rwandaemr.labnotification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.RwandaEmrService;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LabNotificationServiceTest {

	@AfterEach
	public void tearDown() {
		System.clearProperty(LabNotificationService.REQUIRE_RESULT_VALIDATION);
	}

	private LabNotificationService service(Order order) {
		RwandaEmrService rwandaEmrService = mock(RwandaEmrService.class);
		when(rwandaEmrService.getObsByOrder(order)).thenReturn(Collections.singletonList(new Obs()));
		return new LabNotificationService(null, rwandaEmrService, null, null, null, null, null);
	}

	private Order order(Order.FulfillerStatus status) {
		Order order = new TestOrder();
		order.setFulfillerStatus(status);
		return order;
	}

	@Test
	public void isResultReady_shouldRequireCompletedWhenValidationRequired() {
		System.setProperty(LabNotificationService.REQUIRE_RESULT_VALIDATION, "true");
		Order inProgress = order(Order.FulfillerStatus.IN_PROGRESS);
		assertFalse(service(inProgress).isResultReady(inProgress));
		Order completed = order(Order.FulfillerStatus.COMPLETED);
		assertTrue(service(completed).isResultReady(completed));
	}

	@Test
	public void isResultReady_shouldAcceptResultsWhenValidationNotRequired() {
		System.setProperty(LabNotificationService.REQUIRE_RESULT_VALIDATION, "false");
		Order inProgress = order(Order.FulfillerStatus.IN_PROGRESS);
		assertTrue(service(inProgress).isResultReady(inProgress));
	}
}
