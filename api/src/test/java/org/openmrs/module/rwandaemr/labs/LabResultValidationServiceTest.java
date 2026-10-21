package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.pihapps.orders.OrderFulfillmentStatus;
import org.openmrs.module.rwandaemr.labs.LabResultValidationRepository.Action;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LabResultValidationServiceTest {

	private OrderService orderService;
	private LabResultValidationRepository repository;
	private LabResultValidationService service;
	private User technician;
	private User validator;

	@BeforeEach
	public void setUp() {
		orderService = mock(OrderService.class);
		repository = mock(LabResultValidationRepository.class);
		service = spy(new LabResultValidationService(mock(DbSessionFactory.class), orderService, repository, mock(AntibiogramConfig.class)));
		technician = new User(10);
		validator = new User(20);
		loginAs(validator);
	}

	@AfterEach
	public void tearDown() {
		Context.clearUserContext();
	}

	@Test
	public void shouldOnlyConsiderOrdersMarkedAsAwaitingValidationAsAwaitingValidation() {
		assertThat(LabResultValidationService.isAwaitingValidation(awaitingValidation(1)), equalTo(true));
		Order inProgress = awaitingValidation(2);
		inProgress.setFulfillerComment(null);
		assertThat(LabResultValidationService.isAwaitingValidation(inProgress), equalTo(false));
		Order completed = awaitingValidation(3);
		completed.setFulfillerStatus(Order.FulfillerStatus.COMPLETED);
		assertThat(LabResultValidationService.isAwaitingValidation(completed), equalTo(false));
	}

	@Test
	public void shouldCompleteOrdersValidatedByAnotherUser() {
		Order order = awaitingValidation(1);
		doReturn(new HashSet<User>(Collections.singleton(technician))).when(service).getResultEditors(order);

		service.validate(Collections.singletonList(order));

		verify(orderService).updateOrderFulfillerStatus(order, Order.FulfillerStatus.COMPLETED, LabResultValidationService.RESULTS_VALIDATED);
		verify(repository).saveDecision(eq(order), eq(Action.VALIDATED), isNull(), eq("10"), eq(validator));
	}

	@Test
	public void shouldNotLetTheUserWhoEnteredResultsValidateThem() {
		Order first = awaitingValidation(1);
		Order second = awaitingValidation(2);
		doReturn(new HashSet<User>(Collections.singleton(technician))).when(service).getResultEditors(first);
		doReturn(new HashSet<User>(Arrays.asList(technician, validator))).when(service).getResultEditors(second);

		APIException e = assertThrows(APIException.class, () -> service.validate(Arrays.asList(first, second)));

		assertThat(e.getMessage(), containsString("another user must validate"));
		// Nothing is saved when any order cannot be validated
		verify(orderService, never()).updateOrderFulfillerStatus(any(Order.class), any(Order.FulfillerStatus.class), anyString());
		verify(repository, never()).saveDecision(any(), any(), any(), any(), any());
	}

	@Test
	public void shouldNotValidateOrdersThatAreNotAwaitingValidation() {
		Order order = awaitingValidation(1);
		order.setFulfillerComment(null);
		assertThrows(APIException.class, () -> service.validate(Collections.singletonList(order)));
		verify(orderService, never()).updateOrderFulfillerStatus(any(Order.class), any(Order.FulfillerStatus.class), anyString());
	}

	@Test
	public void shouldReturnResultsForCorrectionWithTheReason() {
		Order order = awaitingValidation(1);
		doReturn(new HashSet<User>(Collections.singleton(technician))).when(service).getResultEditors(order);

		service.returnForCorrection(Collections.singletonList(order), " Hemoglobin value looks wrong ");

		verify(orderService).updateOrderFulfillerStatus(order, Order.FulfillerStatus.IN_PROGRESS,
				"Returned for correction: Hemoglobin value looks wrong");
		verify(repository).saveDecision(order, Action.RETURNED, "Hemoglobin value looks wrong", "10", validator);
	}

	@Test
	public void shouldRequireAReasonToReturnResultsForCorrection() {
		Order order = awaitingValidation(1);
		assertThrows(APIException.class, () -> service.returnForCorrection(Collections.singletonList(order), " "));
	}

	private Order awaitingValidation(int orderId) {
		TestOrder order = new TestOrder();
		order.setOrderId(orderId);
		order.setFulfillerStatus(Order.FulfillerStatus.IN_PROGRESS);
		order.setFulfillerComment(OrderFulfillmentStatus.RESULTS_AWAITING_VALIDATION);
		return order;
	}

	private void loginAs(User user) {
		UserContext userContext = mock(UserContext.class);
		when(userContext.getAuthenticatedUser()).thenReturn(user);
		when(userContext.hasPrivilege(anyString())).thenReturn(true);
		when(userContext.isAuthenticated()).thenReturn(true);
		Context.setUserContext(userContext);
	}
}
