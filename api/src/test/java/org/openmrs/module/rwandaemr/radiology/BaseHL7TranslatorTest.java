package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.MockRwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseHL7TranslatorTest {

    AdtService adtService;
    AdministrationService administrationService;
    ConceptService conceptService;
    RwandaEmrConfig rwandaEmrConfig;
    RadiologyConfig radiologyConfig;
    PatientToADTTranslator patientToADTTranslator;
    OrderToORMTranslator orderToORMTranslator;
    Location orderLocation;
    Location visitLocation;
    CareSetting inpatient;
    CareSetting outpatient;
    ConceptSource loinc;
    ConceptMapType sameAs;
    Concept crConceptSet;
    Concept ctConceptSet;
    Concept usConceptSet;
    Concept xrayConcept;

    SimpleDateFormat ymd = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat ymdhms = new SimpleDateFormat("yyyyMMddHHmmss");

    Patient patient;
    Provider orderingProvider;
    Encounter encounter;
    TestOrder testOrder;

    @Before
    public void setUp() throws Exception {
        adtService = mock(AdtService.class);
        administrationService = mock(AdministrationService.class);
        conceptService = mock(ConceptService.class);
        radiologyConfig = new RadiologyConfig(administrationService, conceptService);
        rwandaEmrConfig = new MockRwandaEmrConfig(null, null, null, null, null, radiologyConfig);
        patientToADTTranslator = new PatientToADTTranslator(adtService, conceptService, rwandaEmrConfig);
        orderToORMTranslator = new OrderToORMTranslator(adtService, conceptService, rwandaEmrConfig);
        orderLocation = new Location();
        orderLocation.setUuid("order-location-uuid");
        orderLocation.setName("Emergency Room");
        visitLocation = new Location();
        visitLocation.setUuid("visit-location-uuid");
        visitLocation.setName("University Hospital");
        when(adtService.getLocationThatSupportsVisits(orderLocation)).thenReturn(visitLocation);
        inpatient = new CareSetting();
        inpatient.setName("Inpatient");
        inpatient.setCareSettingType(CareSetting.CareSettingType.INPATIENT);
        outpatient = new CareSetting();
        outpatient.setName("Outpatient");
        outpatient.setCareSettingType(CareSetting.CareSettingType.OUTPATIENT);
        loinc = new ConceptSource();
        loinc.setName("LOINC");
        sameAs = new ConceptMapType();
        sameAs.setName("SAME-AS");
        crConceptSet = new Concept();
        ctConceptSet = new Concept();
        usConceptSet = new Concept();
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_SUPPORTED_MODALITIES, "")).thenReturn("CR,CT,US");
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "CR", "")).thenReturn(crConceptSet.getUuid());
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "CT", "")).thenReturn(ctConceptSet.getUuid());
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "US", "")).thenReturn(usConceptSet.getUuid());
        xrayConcept = new Concept();
        xrayConcept.setFullySpecifiedName(new ConceptName("X-Ray", Locale.ENGLISH));
        ConceptReferenceTerm xrayTerm = new ConceptReferenceTerm();
        xrayTerm.setConceptSource(loinc);
        xrayTerm.setCode("12121");
        ConceptMap xrayLoinc = new ConceptMap();
        xrayLoinc.setConcept(xrayConcept);
        xrayLoinc.setConceptMapType(sameAs);
        xrayLoinc.setConceptReferenceTerm(xrayTerm);
        xrayConcept.setConceptMappings(Collections.singletonList(xrayLoinc));
        crConceptSet.addSetMember(xrayConcept);
        when(conceptService.getConceptSourceByName("LOINC")).thenReturn(loinc);
        when(conceptService.getConceptMapTypeByName("SAME-AS")).thenReturn(sameAs);
        when(conceptService.getConceptByReference(crConceptSet.getUuid())).thenReturn(crConceptSet);
        when(conceptService.getConceptByReference(ctConceptSet.getUuid())).thenReturn(ctConceptSet);
        when(conceptService.getConceptByReference(usConceptSet.getUuid())).thenReturn(usConceptSet);

        patient = new Patient();
        patient.setUuid(UUID.randomUUID().toString());
        patient.addName(new PersonName("Horatio", null, "Hornblower"));
        patient.setGender("M");
        patient.setBirthdate(ymd.parse("19821128"));
        patient.addAttribute(new PersonAttribute(rwandaEmrConfig.getTelephoneNumber(), "111-222-3333"));
        patient.addIdentifier(new PatientIdentifier("P-1234", rwandaEmrConfig.getPrimaryCareIdentifierType(), null));
        patient.setPersonDateCreated(ymdhms.parse("20250303110000"));
        patient.setPersonDateChanged(ymdhms.parse("20250414110000"));

        orderingProvider = new Provider();
        orderingProvider.setIdentifier("DOC-123");
        Person orderingPerson = new Person();
        orderingPerson.addName(new PersonName("Doc", null, "Hollywood"));
        orderingProvider.setPerson(orderingPerson);

        encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setLocation(orderLocation);
        encounter.setEncounterDatetime(new Date());

        testOrder = new TestOrder();
        testOrder.setPatient(patient);
        encounter.addOrder(testOrder);
        testOrder.setDateActivated(encounter.getEncounterDatetime());
        testOrder.setClinicalHistory("Twisted ankle");
        testOrder.setOrderer(orderingProvider);
        testOrder.setCareSetting(inpatient);
        FieldUtils.writeField(testOrder, "orderNumber", "ORD-1111", true);
        testOrder.setConcept(xrayConcept);
    }

    String[] getMessageSegment(String message, String segment) {
        for (String line : message.split("[\\r\\n]")) {
            String[] components = line.split("\\|");
            if (components[0].equals(segment)) {
                return components;
            }
        }
        return null;
    }

    void testField(String message, String segment, int num, Matcher<String> valueMatcher) {
        String[] segmentElements = getMessageSegment(message, segment);
        assertThat(segmentElements, notNullValue());
        assertThat(segmentElements.length, greaterThan(num));
        assertThat(Arrays.asList(segmentElements).toString(), segmentElements[num], valueMatcher);
    }

    void testNumberOfFields(String message, String segment, int num) {
        String[] segmentElements = getMessageSegment(message, segment);
        assertThat(segmentElements, notNullValue());
        assertThat(Arrays.asList(segmentElements).toString(), segmentElements.length-1, equalTo(num)); // Subtract 1 to ignore the segment name
    }
}