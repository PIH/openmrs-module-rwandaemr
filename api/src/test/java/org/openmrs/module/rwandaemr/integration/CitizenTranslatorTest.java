package org.openmrs.module.rwandaemr.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Patient;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.MockRwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;

import java.io.InputStream;
import java.text.SimpleDateFormat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

public class CitizenTranslatorTest {

    CitizenTranslator upidPatientTranslator;
    RwandaEmrConfig rwandaEmrConfig;
    IntegrationConfig integrationConfig;
    LocationTagUtil locationTagUtil;

    @BeforeEach
    public void setUp() {
        rwandaEmrConfig = new MockRwandaEmrConfig();
        locationTagUtil = mock(LocationTagUtil.class);
        integrationConfig = new IntegrationConfig(rwandaEmrConfig, locationTagUtil);
        upidPatientTranslator = new CitizenTranslator(rwandaEmrConfig, integrationConfig);
    }

    @Test
    public void shouldTranslateFromFhirPatientToOpenmrsPatient() throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("upid-generator-example-nid.json")) {
            ObjectMapper mapper = new ObjectMapper();
            CitizenResponse r = mapper.readValue(is, CitizenResponse.class);
            assertThat(r, notNullValue());
            assertThat(r.getStatus(), equalTo("ok"));
            assertThat(r.getData(), notNullValue());
            Patient p = upidPatientTranslator.toPatient(r.getData());
            assertThat(p, notNullValue());
            assertThat(p.getGender(), equalTo("M"));
            assertThat(new SimpleDateFormat("yyyy-MM-dd").format(p.getBirthdate()), equalTo("1990-01-01"));
            assertThat(p.getNames().size(), equalTo(1));
            assertThat(p.getGivenName(), equalTo("Paul"));
            assertThat(p.getFamilyName(), equalTo("MANISHIMWE"));
            assertThat(p.getDead(), equalTo(false));
            assertThat(p.getIdentifiers().size(), equalTo(4));
            assertThat(p.getPatientIdentifier(rwandaEmrConfig.getNationalId()).getIdentifier(), equalTo("xxxxxxxxxxxxxxxxx"));
            assertThat(p.getPatientIdentifier(rwandaEmrConfig.getNIN()).getIdentifier(), equalTo("000-0000-000"));
            assertThat(p.getPatientIdentifier(rwandaEmrConfig.getNidApplicationNumber()).getIdentifier(), equalTo("01957862"));
            assertThat(p.getPatientIdentifier(rwandaEmrConfig.getUPID()).getIdentifier(), equalTo("230606-0022-4112"));
            assertThat(p.getActiveAttributes().size(), equalTo(2));
            assertThat(p.getAttribute(rwandaEmrConfig.getMothersName()).getValue(), equalTo("Kaliza Jeanne"));
            assertThat(p.getAttribute(rwandaEmrConfig.getFathersName()).getValue(), equalTo("Manzi Callixte"));
        }
    }
}