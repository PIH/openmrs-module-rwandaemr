package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadiologyOrderCancelServiceTest extends BaseHL7TranslatorTest {

	private RadiologyOrderCancelService service;
	private RadiologyReportService radiologyReportService;
	private Provider radiographer;

	@BeforeEach
	public void setUpService() {
		radiologyReportService = mock(RadiologyReportService.class);
		radiographer = new Provider(55);
		when(radiologyReportService.getCurrentProvider()).thenReturn(radiographer);
		service = new RadiologyOrderCancelService(orderService, orderToORMTranslator, radiologyConfig, rwandaEmrService,
				radiologyReportService);
		UserContext userContext = mock(UserContext.class);
		when(userContext.hasPrivilege(anyString())).thenReturn(true);
		when(userContext.isAuthenticated()).thenReturn(true);
		Context.setUserContext(userContext);
	}

	@AfterEach
	public void clearUserContext() {
		Context.clearUserContext();
	}

	@Test
	public void shouldCancelAnOrderThatHasNotBeenDoneByDiscontinuingIt() {
		Order discontinuation = new TestOrder();
		when(orderService.discontinueOrder(eq(testOrder), eq("Patient refused the exam"), any(Date.class), eq(radiographer),
				eq(testOrder.getEncounter()))).thenReturn(discontinuation);

		assertThat(service.canCancel(testOrder), equalTo(true));
		Order result = service.cancel(testOrder, "  Patient refused the exam ");

		assertThat(result, equalTo(discontinuation));
	}

	@Test
	public void shouldNotCancelAnExamThatWasAlreadyDone() {
		Obs study = new Obs();
		study.setConcept(radiologyStudyConstruct);
		rwandaEmrService.addObsForOrder(testOrder, study);

		assertThat(service.canCancel(testOrder), equalTo(false));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> service.cancel(testOrder, "Wrong exam"));
		assertThat(e.getMessage(), containsString("already been done"));
		verify(orderService, never()).discontinueOrder(any(Order.class), anyString(), any(), any(), any());
	}

	@Test
	public void shouldNotCancelAnOrderTwice() throws Exception {
		FieldUtils.writeField(testOrder, "dateStopped", new Date(), true);

		assertThat(service.getReasonOrderCannotBeCancelled(testOrder), containsString("already cancelled"));
	}

	@Test
	public void shouldRequireAReason() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.cancel(testOrder, " "));
		assertThat(e.getMessage(), containsString("reason"));
	}

	@Test
	public void shouldRequireTheUserToBeAProvider() {
		when(radiologyReportService.getCurrentProvider()).thenReturn(null);

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.cancel(testOrder, "Duplicate"));
		assertThat(e.getMessage(), containsString("not linked to a provider"));
	}

	@Test
	public void shouldNotCancelOrdersThatAreNotForRadiology() {
		TestOrder labOrder = new TestOrder();
		labOrder.setConcept(new org.openmrs.Concept(999999));

		assertThat(service.getReasonOrderCannotBeCancelled(labOrder), equalTo("This is not a radiology order."));
		assertThat(service.getReasonOrderCannotBeCancelled(testOrder), nullValue());
	}
}
