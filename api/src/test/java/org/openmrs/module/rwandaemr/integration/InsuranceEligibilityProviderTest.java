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
import org.openmrs.util.OpenmrsUtil;

import java.io.File;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;

public class InsuranceEligibilityProviderTest {

	protected Log log = LogFactory.getLog(getClass());

	InsuranceEligibilityProvider provider;
	InsuranceIntegrationConfig config;
	Properties p;

	@Before
	public void setUp() {
		p = new Properties();
		try {
			File userHome = new File(System.getProperty("user.home"));
			OpenmrsUtil.loadProperties(p, new File(userHome, ".rwanda-insurance-test.properties"));
		}
		catch (Exception ignore) {}
		config = new InsuranceIntegrationConfig() {
			@Override
			public String getEligibilityCheckUrl() {
				return p.getProperty(InsuranceIntegrationConfig.ELIGIBILITY_CHECK_URL, "");
			}

			@Override
			public String getEligibilityCheckApiKey() {
				return p.getProperty(InsuranceIntegrationConfig.ELIGIBILITY_CHECK_API_KEY, "");
			}

			@Override
			public String getEligibilityCheckApiOrigin() {
				return p.getProperty(InsuranceIntegrationConfig.ELIGIBILITY_CHECK_API_ORIGIN, "");
			}
		};
		provider = new InsuranceEligibilityProvider(config);
	}

	@Test
	public void shouldConnectToIntegrationEndpoint() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		for (String property : p.stringPropertyNames()) {
			log.warn(property + ": " + p.getProperty(property));
		}
		String type = p.getProperty("connectionTest.type");
		String identifier = p.getProperty("connectionTest.identifier");
		IntegrationResponse response = provider.checkEligibility(type, identifier);
		System.out.println(response);
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
		return !p.isEmpty();
	}
}
