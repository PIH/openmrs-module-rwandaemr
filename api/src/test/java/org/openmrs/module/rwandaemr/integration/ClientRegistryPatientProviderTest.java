package org.openmrs.module.rwandaemr.integration;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openmrs.Patient;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.MockRwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.util.ConfigUtil;

import java.text.SimpleDateFormat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class ClientRegistryPatientProviderTest {

	protected Log log = LogFactory.getLog(getClass());

	FhirContext fhirContext;
	ClientRegistryPatientTranslator clientRegistryPatientTranslator;
	CitizenTranslator upidPatientTranslator;
	ClientRegistryPatientProvider provider;
	LocationTagUtil locationTagUtil;
	RwandaEmrConfig rwandaEmrConfig;
	IntegrationConfig integrationConfig;

	@BeforeEach
	public void setUp() {
		fhirContext = FhirContext.forR4Cached();
		rwandaEmrConfig = new MockRwandaEmrConfig();
		locationTagUtil = Mockito.mock(LocationTagUtil.class);
		integrationConfig = new IntegrationConfig(rwandaEmrConfig, locationTagUtil);
		clientRegistryPatientTranslator = new ClientRegistryPatientTranslator(rwandaEmrConfig, integrationConfig);
		upidPatientTranslator = new CitizenTranslator(rwandaEmrConfig, integrationConfig);
		provider = new ClientRegistryPatientProvider(fhirContext, rwandaEmrConfig, integrationConfig, clientRegistryPatientTranslator);
	}
	
	@Test
	public void shouldTestUsingHttp() {
		if (!isConfiguredToRun()) {
			log.warn("NOT EXECUTING " + getClass().getSimpleName() + " AS CLIENT REGISTRY CONFIGURATION IS MISSING");
			log.warn("THE REQUIRED PROPERTIES SHOULD BE SET USING `-Dproperty=value` WHEN EXECUTING THE TEST");
			return;
		}
		ClientRegistryPatient clientRegistryPatient = provider.fetchPatientFromClientRegistry("220919-7657-5617", "UPI");
		Patient patient = clientRegistryPatientTranslator.toPatient(clientRegistryPatient);
		assertThat(patient, notNullValue());
		assertThat(patient.getGender(), equalTo("M"));
		assertThat(new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthdate()), equalTo("1999-03-20"));
	}

	private boolean isConfiguredToRun() {
		return  ConfigUtil.getSystemProperty(IntegrationConfig.HIE_URL_PROPERTY) != null &&
				ConfigUtil.getSystemProperty(IntegrationConfig.HIE_USERNAME_PROPERTY) != null &&
				ConfigUtil.getSystemProperty(IntegrationConfig.HIE_PASSWORD_PROPERTY) != null;
	}
}
