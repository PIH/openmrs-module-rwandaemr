package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.rwandaemr.integration.insurance.CbhiDetails;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceEligibilityProvider;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceIntegrationConfig;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceNotFoundResponse;
import org.openmrs.module.rwandaemr.integration.insurance.RamaDetails;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;

public class InsuranceEligibilityProviderTest {

	protected Log log = LogFactory.getLog(getClass());

	InsuranceEligibilityProvider provider;
	InsuranceIntegrationConfig config;

	@Before
	public void setUp() {
		config = new InsuranceIntegrationConfig() {
			@Override
			public String getEligibilityCheckUrl() {
				return null; //"https://rhip.moh.gov.rw/backend/rssb_integration/api/v1/members/eligibility-check";
			}
		};
		provider = new InsuranceEligibilityProvider(config);
	}
	
	@Test
	public void shouldHandleValidCbhiEligibilityResponse() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		String type = "cbhi";
		String identifier = "1195080033589077";
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(true));
		assertThat(response.getResponseCode(), equalTo(200));
		assertThat(response.getResponseEntity(), isA(CbhiDetails.class));
		assertThat(response.getErrorMessage(), nullValue());
		CbhiDetails details = (CbhiDetails) response.getResponseEntity();
		assertThat(details.getTotalMembers(), equalTo(1));

		identifier = "1194170010945086";
		response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(true));
		assertThat(response.getResponseCode(), equalTo(200));
		assertThat(response.getResponseEntity(), isA(CbhiDetails.class));
		assertThat(response.getErrorMessage(), nullValue());
		details = (CbhiDetails) response.getResponseEntity();
		assertThat(details.getTotalMembers(), equalTo(2));
		assertThat(details.getMembers().get(0).getType(), equalTo("HEAD"));
		assertThat(details.getMembers().get(1).getType(), equalTo("BENEFICIARY"));
	}

	@Test
	public void shouldHandleValidRamaEligibilityResponse() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		String type = "rama";
		String identifier = "20746004W";
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(true));
		assertThat(response.getResponseCode(), equalTo(200));
		assertThat(response.getResponseEntity(), isA(RamaDetails.class));
		assertThat(response.getErrorMessage(), nullValue());
		RamaDetails details = (RamaDetails) response.getResponseEntity();
		assertThat(details.getMainAffiliateId(), equalTo(identifier));
	}

	@Test
	public void shouldHandleNoHouseholdFoundResponse() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		String type = "cbhi";
		String identifier = "RCA2411104607";
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(true));
		assertThat(response.getResponseCode(), equalTo(404));
		assertThat(response.getResponseEntity(), isA(InsuranceNotFoundResponse.class));
		assertThat(response.getErrorMessage(), nullValue());
		InsuranceNotFoundResponse r = (InsuranceNotFoundResponse) response.getResponseEntity();
		assertThat(r.isSuccess(), equalTo(false));
		assertThat(r.getMessage(), equalTo("Household is not found"));
	}

	@Test
	public void shouldHandleInvalidRequestResponse() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		String type = "rama";
		String identifier = "ABCDE";
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(true));
		assertThat(response.getResponseCode(), equalTo(400));
		assertThat(response.getResponseEntity(), isA(InsuranceNotFoundResponse.class));
		InsuranceNotFoundResponse r = (InsuranceNotFoundResponse) response.getResponseEntity();
		assertThat(r.isSuccess(), equalTo(false));
	}

	@Test
	public void shouldHandleConnectivityError() {
		config = new InsuranceIntegrationConfig() {
			@Override
			public String getEligibilityCheckUrl() {
				return "http://invalid-url";
			}
		};
		provider = new InsuranceEligibilityProvider(config);
		String type = "rama";
		String identifier = "ABCDE";
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		assertThat(response.isEndpointAccessible(), equalTo(false));
	}

	private boolean isConfiguredToRun() {
		return config.getEligibilityCheckUrl() != null;
	}
}
