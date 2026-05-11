package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class InsurancePatientReceptionResponseTest {

	@Test
	public void shouldUseNestedRhipErrorMessage() throws Exception {
		String json = "{" +
				"\"success\":false," +
				"\"message\":\"Voucher creation failed\"," +
				"\"errors\":[{" +
				"\"productId\":null," +
				"\"message\":\"[\\\"patientId must be a UUID\\\"\"," +
				"\"isValid\":null" +
				"}]," +
				"\"insuranceType\":\"cbhi\"," +
				"\"facilityFosaId\":\"30311017\"," +
				"\"patientIdentifier\":\"1199880145287129\"," +
				"\"data\":null," +
				"\"timestamp\":\"2026-04-29T14:51:11.011790366Z\"" +
				"}";

		InsurancePatientReceptionResponse response = new ObjectMapper()
				.readValue(json, InsurancePatientReceptionResponse.class);

		assertThat(response.getMessage(), equalTo("Voucher creation failed"));
		assertThat(response.getFirstErrorMessage(), equalTo("patientId must be a UUID"));
	}

	@Test
	public void shouldHandleValidJsonArrayErrorMessage() throws Exception {
		String json = "{" +
				"\"success\":false," +
				"\"message\":\"Voucher creation failed\"," +
				"\"errors\":[{\"message\":\"[\\\"procedure code is required\\\"]\"}]" +
				"}";

		InsurancePatientReceptionResponse response = new ObjectMapper()
				.readValue(json, InsurancePatientReceptionResponse.class);

		assertThat(response.getFirstErrorMessage(), equalTo("procedure code is required"));
	}
}
