package org.openmrs.module.rwandaemr.rest;

import org.junit.jupiter.api.Test;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LabOrderPaymentRestControllerTest {

	@Test
	public void shouldAnswerUnknownOrderWith400() throws Exception {
		ResponseEntity<?> response = handle(new IllegalArgumentException("Order missing not found"));
		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
		assertEquals(false, ((Map<?, ?>) response.getBody()).get("success"));
		assertEquals("Order missing not found", ((Map<?, ?>) response.getBody()).get("message"));
		assertEquals(2, ((Map<?, ?>) response.getBody()).size());
	}

	@Test
	public void shouldAnswerApiExceptionWith400() throws Exception {
		assertEquals(HttpStatus.BAD_REQUEST, handle(new APIException("bad")).getStatusCode());
	}

	@Test
	public void shouldAnswerMissingPrivilegeWith403() throws Exception {
		assertEquals(HttpStatus.FORBIDDEN, handle(new APIAuthenticationException("no")).getStatusCode());
	}

	/**
	 * @return the response of the exception handler Spring would pick for the given exception
	 */
	private ResponseEntity<?> handle(Exception e) throws Exception {
		Method handler = new ExceptionHandlerMethodResolver(LabOrderPaymentRestController.class).resolveMethod(e);
		return (ResponseEntity<?>) handler.invoke(new LabOrderPaymentRestController(), e);
	}
}
