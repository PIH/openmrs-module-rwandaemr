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
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.MockRwandaEmrConfig;
import org.openmrs.module.rwandaemr.MockRwandaEmrService;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseHL7TranslatorTest {

    AdtService adtService;
    AdministrationService administrationService;
    ConceptService conceptService;
    EncounterService encounterService;
    OrderService orderService;
    MockRwandaEmrService rwandaEmrService;
    RwandaEmrConfig rwandaEmrConfig;
    RadiologyConfig radiologyConfig;
    PatientToADTTranslator patientToADTTranslator;
    OrderToORMTranslator orderToORMTranslator;
    ORUToObsTranslator oruToObsTranslator;
    Location orderLocation;
    Location visitLocation;
    CareSetting inpatient;
    CareSetting outpatient;
    ConceptSource loinc;
    ConceptSource emrapi;
    ConceptMapType sameAs;
    Concept crConceptSet;
    Concept ctConceptSet;
    Concept usConceptSet;
    Concept xrayConcept;
    Concept radiologyStudyConstruct;
    Concept radiologyReportConstruct;
    Concept radiologyProcedurePerformed;
    Concept dateOfTest;
    Concept radiologyAccessionNumber;
    Concept radiologyImagesAvailable;
    Concept radiologyReportType;
    Concept radiologyReportComments;
    Concept preliminaryConcept;
    Concept finalConcept;
    Concept correctionConcept;

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
        encounterService = mock(EncounterService.class);
        orderService = mock(OrderService.class);
        rwandaEmrService = new MockRwandaEmrService();
        radiologyConfig = new RadiologyConfig(administrationService, conceptService, encounterService);
        rwandaEmrConfig = new MockRwandaEmrConfig(null, null, null, null, null, null, radiologyConfig);
        patientToADTTranslator = new PatientToADTTranslator(adtService, conceptService, rwandaEmrConfig);
        orderToORMTranslator = new OrderToORMTranslator(adtService, conceptService, rwandaEmrConfig);
        oruToObsTranslator = new ORUToObsTranslator(adtService, conceptService, rwandaEmrConfig, orderService, rwandaEmrService);
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
        emrapi = new ConceptSource();
        emrapi.setName("org.openmrs.module.emrapi");
        sameAs = new ConceptMapType();
        sameAs.setName("SAME-AS");
        crConceptSet = new Concept();
        ctConceptSet = new Concept();
        usConceptSet = new Concept();
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_SUPPORTED_MODALITIES, "")).thenReturn("CR,CT,US");
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "CR", "")).thenReturn(crConceptSet.getUuid());
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "CT", "")).thenReturn(ctConceptSet.getUuid());
        when(administrationService.getGlobalProperty(RadiologyConfig.GP_ORDERABLES_PREFIX + "US", "")).thenReturn(usConceptSet.getUuid());
        xrayConcept = createConcept("X-RAY, FOOT", loinc, "36657-5");
        crConceptSet.addSetMember(xrayConcept);
        when(conceptService.getConceptSourceByName("LOINC")).thenReturn(loinc);
        when(conceptService.getConceptSourceByName("org.openmrs.module.emrapi")).thenReturn(emrapi);
        when(conceptService.getConceptMapTypeByName("SAME-AS")).thenReturn(sameAs);
        when(conceptService.getConceptByReference(crConceptSet.getUuid())).thenReturn(crConceptSet);
        when(conceptService.getConceptByReference(ctConceptSet.getUuid())).thenReturn(ctConceptSet);
        when(conceptService.getConceptByReference(usConceptSet.getUuid())).thenReturn(usConceptSet);
        when(conceptService.getConceptByReference("LOINC:36657-5")).thenReturn(xrayConcept);
        radiologyStudyConstruct = createConcept(emrapi, "Radiology study construct");
        when(radiologyConfig.getRadiologyStudyConstruct()).thenReturn(radiologyStudyConstruct);
        radiologyReportConstruct = createConcept(emrapi, "Radiology report construct");
        when(radiologyConfig.getRadiologyReportConstruct()).thenReturn(radiologyReportConstruct);
        radiologyProcedurePerformed = createConcept(emrapi, "Radiology procedure performed");
        when(radiologyConfig.getRadiologyProcedurePerformed()).thenReturn(radiologyProcedurePerformed);
        dateOfTest = createConcept(emrapi, "Date of test");
        when(radiologyConfig.getDateOfTest()).thenReturn(dateOfTest);
        radiologyAccessionNumber = createConcept(emrapi, "Radiology accession number");
        when(radiologyConfig.getRadiologyAccessionNumber()).thenReturn(radiologyAccessionNumber);
        radiologyImagesAvailable = createConcept(emrapi, "Radiology images available");
        when(radiologyConfig.getRadiologyImagesAvailable()).thenReturn(radiologyImagesAvailable);
        radiologyReportType = createConcept(emrapi, "Type of radiology report");
        when(radiologyConfig.getRadiologyReportType()).thenReturn(radiologyReportType);
        radiologyReportComments = createConcept(emrapi, "Radiology report comments");
        when(radiologyConfig.getRadiologyReportComments()).thenReturn(radiologyReportComments);
        preliminaryConcept = createConcept(emrapi, "Radiology report preliminary");
        when(radiologyConfig.getPreliminaryStatusConcept()).thenReturn(preliminaryConcept);
        finalConcept = createConcept(emrapi, "Radiology report final");
        when(radiologyConfig.getFinalStatusConcept()).thenReturn(finalConcept);
        correctionConcept = createConcept(emrapi, "Radiology report correction");
        when(radiologyConfig.getCorrectionStatusConcept()).thenReturn(correctionConcept);

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
        FieldUtils.writeField(testOrder, "orderNumber", "ORD-764591", true);
        testOrder.setConcept(xrayConcept);

        when(orderService.getOrderByOrderNumber("ORD-764591")).thenReturn(testOrder);
    }

    Concept createConcept(ConceptSource source, String code) {
        return createConcept(code, source, code);
    }

    Concept createConcept(String name, ConceptSource source, String code) {
        Concept c = new Concept();
        c.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
        ConceptReferenceTerm term = new ConceptReferenceTerm();
        term.setConceptSource(source);
        term.setCode(code);
        ConceptMap m = new ConceptMap();
        m.setConcept(xrayConcept);
        m.setConceptMapType(sameAs);
        m.setConceptReferenceTerm(term);
        c.setConceptMappings(Collections.singletonList(m));
        return c;
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