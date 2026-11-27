package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * Responses and requests of the MMI reception steps, as described in the RHIP Unified Insurance Integration Guide: MMI
 */
public class MmiReceptionTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String RECEPTION_OPENED = "{" +
			"\"success\":true," +
			"\"message\":\"Patient reception submitted successfully\"," +
			"\"insuranceType\":\"mmi\"," +
			"\"facilityFosaId\":\"10023\"," +
			"\"patientIdentifier\":\"1212222-22\"," +
			"\"data\":{" +
			"\"visitReferenceNumber\":\"1212222-22-20261124-1\"," +
			"\"facilityFosaId\":\"10023\"," +
			"\"bpCode\":\"BP-0042\"," +
			"\"patientNumber\":\"1212222-22\"," +
			"\"patientTypeNumber\":1," +
			"\"receptionDate\":\"2026-11-24 09:12:44\"," +
			"\"status\":\"open\"," +
			"\"practitionerLicenseNumber\":null," +
			"\"visitType\":null}," +
			"\"timestamp\":\"2026-11-24T07:12:44Z\"}";

	private static final String RECEPTION_PENDING = "{" +
			"\"success\":false," +
			"\"message\":\"This patient already has a pending reception. Complete its billing before starting a new one.\"," +
			"\"insuranceType\":\"mmi\"," +
			"\"facilityFosaId\":\"10023\"," +
			"\"patientIdentifier\":\"1212222-22\"," +
			"\"data\":{\"visitReferenceNumber\":\"1212222-22-20261117-1\",\"status\":\"pending\"}," +
			"\"timestamp\":\"2026-11-24T07:12:44Z\"}";

	@Test
	public void shouldReadReceptionNumberFromVisitReferenceNumber() throws Exception {
		MmiReceptionResponse response = MAPPER.readValue(RECEPTION_OPENED, MmiReceptionResponse.class);
		assertThat(response.isSuccess(), equalTo(true));
		assertThat(response.getData().getReceptionNumber(), equalTo("1212222-22-20261124-1"));
		assertThat(response.getData().getBpCode(), equalTo("BP-0042"));
		assertThat(response.getData().getStatus(), equalTo("open"));
	}

	@Test
	public void shouldReadReceptionNumberFromVisitReferenceNumberInRegistrationResponse() throws Exception {
		InsurancePatientReceptionResponse response = MAPPER.readValue(RECEPTION_OPENED, InsurancePatientReceptionResponse.class);
		assertThat(response.isSuccess(), equalTo(true));
		assertThat(response.getData().getReceptionNumber(), equalTo("1212222-22-20261124-1"));
	}

	@Test
	public void shouldReadOpenReceptionFromPendingResponse() throws Exception {
		MmiReceptionResponse response = MAPPER.readValue(RECEPTION_PENDING, MmiReceptionResponse.class);
		String receptionNumber = response.getData().getReceptionNumber();
		assertThat(receptionNumber, equalTo("1212222-22-20261117-1"));
		assertThat(MmiPendingReception.isPendingReception(400, response.isSuccess(), receptionNumber), equalTo(true));
	}

	@Test
	public void shouldNotTreatOtherFailuresAsPendingReception() {
		assertThat(MmiPendingReception.isPendingReception(400, false, null), equalTo(false));
		assertThat(MmiPendingReception.isPendingReception(200, true, "1212222-22-20261124-1"), equalTo(false));
		assertThat(MmiPendingReception.isPendingReception(500, false, "1212222-22-20261124-1"), equalTo(false));
	}

	@Test
	public void shouldDescribePendingReceptionOpenedFromThisEmr() {
		MmiPatientReceptionLogEntry openedHere = new MmiPatientReceptionLogEntry();
		openedHere.setVisitId(42);
		Calendar date = Calendar.getInstance();
		date.set(2026, Calendar.NOVEMBER, 17);
		openedHere.setDateCreated(date.getTime());
		String message = MmiPendingReception.describe("1212222-22-20261117-1", openedHere);
		assertThat(message, containsString("1212222-22-20261117-1"));
		assertThat(message, containsString("opened from this EMR on 17 Nov 2026 (visit 42)"));
		assertThat(message, containsString("voucher closure"));
	}

	@Test
	public void shouldDescribePendingReceptionUnknownToThisEmr() {
		String message = MmiPendingReception.describe("1212222-22-20261117-1", null);
		assertThat(message, containsString("no record of it"));
		assertThat(message, not(containsString("voucher closure")));
	}

	@Test
	public void shouldReadPatientTypes() throws Exception {
		String json = "{\"success\":true,\"message\":\"Patient types retrieved successfully\",\"insuranceType\":\"mmi\"," +
				"\"facilityFosaId\":\"10023\",\"patientTypes\":[{\"typeId\":\"1\",\"typeName\":\"Standard\"," +
				"\"description\":\"MMI Patient Type: Standard\",\"isActive\":true,\"patientPercent\":15,\"mmiPercent\":85}]}";
		MmiPatientTypesResponse response = MAPPER.readValue(json, MmiPatientTypesResponse.class);
		assertThat(response.getPatientTypes().size(), equalTo(1));
		assertThat(response.getPatientTypes().get(0).getTypeId(), equalTo("1"));
		assertThat(response.getPatientTypes().get(0).isActive(), equalTo(true));
		assertThat(response.getPatientTypes().get(0).getMmiPercent(), equalTo(85));
	}

	@Test
	public void shouldBuildChangeTypePayload() {
		InsuranceEligibilityProvider provider = new InsuranceEligibilityProvider(new InsuranceIntegrationConfig());
		Map<String, Object> payload = provider.buildChangeReceptionTypePayload("MMI", "10023", "1212222-22-20261124-1", 2);
		assertThat(payload.get("insuranceType"), equalTo("mmi"));
		assertThat(payload.get("facilityFosaId"), equalTo("10023"));
		assertThat(payload.get("visitReferenceNumber"), equalTo("1212222-22-20261124-1"));
		assertThat(payload.get("patientTypeNo"), equalTo(2));
	}
}
