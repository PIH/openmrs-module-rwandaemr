package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptSource;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Order;
import org.openmrs.Patient;
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
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OrderToORMTranslatorTest {

    AdtService adtService;
    AdministrationService administrationService;
    ConceptService conceptService;
    RwandaEmrConfig rwandaEmrConfig;
    RadiologyConfig radiologyConfig;
    OrderToORMTranslator translator;
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
        translator = new OrderToORMTranslator(adtService, conceptService, rwandaEmrConfig);
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

    /**
     * Example Message from Medsynapse RIS HL7 Integration Document (Unscheduled Study)
     * MSH|^~\&|HIS_APP|HIS_FACILITY|PACS_APP|PACS_FACILITY|20130614131415||ORM^O01|MsgCtrlId_ORM|P|2.3
     * PID|1||PatientID||PatientLast^First^Middle^^Title||20000514|F|||||Phone^Email^History
     * PV1|1|||||||RefPhyID^RefPhyName||||||||||OP||50
     * ORC|NW|||||||||||RadiologistID
     * OBR|1|HOID01|CenterID|TestID^TestName||||||||||||||||||||CR||||||||||TechnicianID
     * <br/>
     * Example Message from Medsynapse RIS HL7 Integration Document (Scheduled Study)
     * MSH|^~\&|HIS_APP|HIS_FACILITY|PACS_APP|PACS_FACILITY|20130614131415||ORM^O01|MsgCtrlId_ORM|P|2.3
     * PID|1||PatientID||PatientLast^First^Middle^^Title||20000514|F|||||Phone^Email^History|||||AdmissionId
     * PV1|1|||||||RefPhyID^RefPhyName||||||||||OP||50
     * ORC|NW|||||||||||RadiologistID
     * OBR|1|HOID01|CenterID|TestID^TestName|ROUTINE||||||||||||||||AETitle|||CR|||||||^ReasonForStudy|||
     * TechnicianID||20321025141236
     * ZDS|1.2.3.4
     */
    @Test
    public void shouldTranslateFromOrderToORM() throws Exception {
        String dateTimeBefore = ymdhms.format(new Date());
        String message = translator.toORM_001(testOrder);
        String dateTimeAfter = ymdhms.format(new Date());

        // The start of the MSH is expected to declare the separator character, so test that first
        assertThat(message, startsWith("MSH|"));
        // Add an extra delimiter so splitting works correctly
        message = message.replace("MSH|", "MSH||");

        testNumberOfFields(message, "MSH", 12);
        testField(message, "MSH", 2, equalTo("^~\\&"));
        testField(message, "MSH", 3, equalTo("OpenMRS"));  // sending application
        testField(message, "MSH", 4, equalTo(visitLocation.getName())); // sending facility
        testField(message, "MSH", 5, equalTo("PACS_APP")); // receiving application
        testField(message, "MSH", 6, equalTo("PACS_FACILITY")); // receiving facility
        testField(message, "MSH", 7, greaterThanOrEqualTo(dateTimeBefore)); // Date/time of message
        testField(message, "MSH", 7, lessThanOrEqualTo(dateTimeAfter)); // Date/time of message
        testField(message, "MSH", 8, emptyString());
        testField(message, "MSH", 9, equalTo("ORM^O01")); // Message Type
        testField(message, "MSH", 10, not(blankOrNullString())); // Message Control ID
        testField(message, "MSH", 11, equalTo("P"));
        testField(message, "MSH", 12, equalTo("2.3"));

        testNumberOfFields(message, "PID", 13);
        testField(message, "PID", 1, equalTo("1")); // Set ID
        testField(message, "PID", 2, emptyString());
        testField(message, "PID", 3, equalTo(patient.getUuid())); // Patient ID
        testField(message, "PID", 4, emptyString());
        testField(message, "PID", 5, equalTo("Hornblower^Horatio")); // Patient Name
        testField(message, "PID", 6, emptyString());
        testField(message, "PID", 7, equalTo(ymd.format(patient.getBirthdate())));  // Birthdate
        testField(message, "PID", 8, equalTo("M"));  // Gender
        testField(message, "PID", 9, emptyString());
        testField(message, "PID", 10, emptyString());
        testField(message, "PID", 11, emptyString());
        testField(message, "PID", 12, emptyString());
        testField(message, "PID", 13, equalTo("111-222-3333^^Twisted ankle"));

        testNumberOfFields(message, "PV1", 18);
        testField(message, "PV1", 1, equalTo("1")); // Set ID
        testField(message, "PV1", 2, emptyString());
        testField(message, "PV1", 3, emptyString());
        testField(message, "PV1", 4, emptyString());
        testField(message, "PV1", 5, emptyString());
        testField(message, "PV1", 6, emptyString());
        testField(message, "PV1", 7, emptyString());
        testField(message, "PV1", 8, equalTo("DOC-123^Hollywood^Doc")); // Referring Doctor
        testField(message, "PV1", 9, emptyString());
        testField(message, "PV1", 10, emptyString());
        testField(message, "PV1", 11, emptyString());
        testField(message, "PV1", 12, emptyString());
        testField(message, "PV1", 13, emptyString());
        testField(message, "PV1", 14, emptyString());
        testField(message, "PV1", 15, emptyString());
        testField(message, "PV1", 16, emptyString());
        testField(message, "PV1", 17, emptyString());
        testField(message, "PV1", 18, equalTo("IP"));  // Patient Type

        testNumberOfFields(message, "ORC", 1);
        testField(message, "ORC", 1, equalTo("NW"));  // Order Type

        testNumberOfFields(message, "OBR", 36);
        testField(message, "OBR", 1, equalTo("1"));  // Set ID
        testField(message, "OBR", 2, equalTo("ORD-1111"));  // Order Number
        testField(message, "OBR", 3, equalTo("University Hospital")); // Center ID
        testField(message, "OBR", 4, equalTo("12121^X-Ray"));  // Test Ordered
        testField(message, "OBR", 5, equalTo("ROUTINE"));
        testField(message, "OBR", 6, emptyString());
        testField(message, "OBR", 7, emptyString());
        testField(message, "OBR", 8, emptyString());
        testField(message, "OBR", 9, emptyString());
        testField(message, "OBR", 10, emptyString());
        testField(message, "OBR", 11, emptyString());
        testField(message, "OBR", 12, emptyString());
        testField(message, "OBR", 13, emptyString());
        testField(message, "OBR", 14, emptyString());
        testField(message, "OBR", 15, emptyString());
        testField(message, "OBR", 16, emptyString());
        testField(message, "OBR", 17, emptyString());
        testField(message, "OBR", 18, emptyString());
        testField(message, "OBR", 19, emptyString());
        testField(message, "OBR", 20, emptyString());
        testField(message, "OBR", 21, equalTo("CR"));
        testField(message, "OBR", 22, emptyString());
        testField(message, "OBR", 23, emptyString());
        testField(message, "OBR", 24, equalTo("CR"));
        testField(message, "OBR", 25, emptyString());
        testField(message, "OBR", 26, emptyString());
        testField(message, "OBR", 27, emptyString());
        testField(message, "OBR", 28, emptyString());
        testField(message, "OBR", 29, emptyString());
        testField(message, "OBR", 30, emptyString());
        testField(message, "OBR", 31, emptyString());
        testField(message, "OBR", 32, emptyString());
        testField(message, "OBR", 33, emptyString());
        testField(message, "OBR", 34, emptyString());
        testField(message, "OBR", 35, emptyString());
        testField(message, "OBR", 36, equalTo(ymdhms.format(testOrder.getDateActivated())));

        // Test STAT order
        testOrder.setUrgency(Order.Urgency.STAT);
        message = translator.toORM_001(testOrder);
        testField(message, "OBR", 5, equalTo("STAT"));

        // Test scheduled order
        testOrder.setUrgency(Order.Urgency.ON_SCHEDULED_DATE);
        testOrder.setScheduledDate(DateUtils.addDays(new Date(), 5));
        testOrder.setOrderReasonNonCoded("Check for Fracture");
        message = translator.toORM_001(testOrder);
        testField(message, "OBR", 5, equalTo("ROUTINE"));
        testField(message, "OBR", 36, equalTo(ymdhms.format(testOrder.getScheduledDate())));
        testField(message, "OBR", 31, equalTo("^" + testOrder.getOrderReasonNonCoded()));

        // Test Discontinue
        testOrder.setVoided(true);
        message = translator.toORM_001(testOrder);
        testField(message, "ORC", 1, equalTo("CA"));
        testOrder.setVoided(false);
        message = translator.toORM_001(testOrder);
        testField(message, "ORC", 1, equalTo("NW"));
        FieldUtils.writeField(testOrder, "dateStopped", new Date(), true);
        message = translator.toORM_001(testOrder);
        testField(message, "ORC", 1, equalTo("CA"));
    }

    private String[] getMessageSegment(String message, String segment) {
        for (String line : message.split("[\\r\\n]")) {
            String[] components = line.split("\\|");
            if (components[0].equals(segment)) {
                return components;
            }
        }
        return null;
    }

    private void testField(String message, String segment, int num, Matcher<String> valueMatcher) {
        String[] segmentElements = getMessageSegment(message, segment);
        assertThat(segmentElements, notNullValue());
        assertThat(segmentElements.length, greaterThan(num));
        assertThat(Arrays.asList(segmentElements).toString(), segmentElements[num], valueMatcher);
    }

    private void testNumberOfFields(String message, String segment, int num) {
        String[] segmentElements = getMessageSegment(message, segment);
        assertThat(segmentElements, notNullValue());
        assertThat(Arrays.asList(segmentElements).toString(), segmentElements.length-1, equalTo(num)); // Subtract 1 to ignore the segment name
    }
}