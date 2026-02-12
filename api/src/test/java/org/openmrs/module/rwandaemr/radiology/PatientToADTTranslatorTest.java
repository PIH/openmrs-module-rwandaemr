package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class PatientToADTTranslatorTest extends BaseHL7TranslatorTest {

    /**
     * Example Message from Medsynapse RIS HL7 Integration Document
     * MSH|^~\&|HIS_APP|HIS_FACILITY|PACS_APP|PACS_FACILITY|20130614131415||ADT^A08|MsgCtrlId_ADT|P|2.3
     * EVN|A08|20130614131415
     * PID|1||PatientID||PatientLast^First^Middle^^Title||20000514|F
     */
    @Test
    public void shouldTranslateFromPatientToADT() throws Exception {
        String dateTimeBefore = ymdhms.format(new Date());
        String message = patientToADTTranslator.toADT_A08(patient);
        String dateTimeAfter = ymdhms.format(new Date());

        // The start of the MSH is expected to declare the separator character, so test that first
        assertThat(message, startsWith("MSH|"));
        // Add an extra delimiter so splitting works correctly
        message = message.replace("MSH|", "MSH||");

        testNumberOfFields(message, "MSH", 12);
        testField(message, "MSH", 2, equalTo("^~\\&"));
        testField(message, "MSH", 3, equalTo("OpenMRS"));  // sending application
        testField(message, "MSH", 4, equalTo("HIS_FACILITY")); // sending facility
        testField(message, "MSH", 5, equalTo("PACS_APP")); // receiving application
        testField(message, "MSH", 6, equalTo("PACS_FACILITY")); // receiving facility
        testField(message, "MSH", 7, greaterThanOrEqualTo(dateTimeBefore)); // Date/time of message
        testField(message, "MSH", 7, lessThanOrEqualTo(dateTimeAfter)); // Date/time of message
        testField(message, "MSH", 8, emptyString());
        testField(message, "MSH", 9, equalTo("ADT^A08")); // Message Type
        testField(message, "MSH", 10, not(blankOrNullString())); // Message Control ID
        testField(message, "MSH", 11, equalTo("P"));
        testField(message, "MSH", 12, equalTo("2.3"));

        testNumberOfFields(message, "EVN", 2);
        testField(message, "EVN", 1, equalTo("A08"));
        testField(message, "EVN", 2, equalTo("20250414110000"));

        testNumberOfFields(message, "PID", 8);
        testField(message, "PID", 1, equalTo("1")); // Set ID
        testField(message, "PID", 2, emptyString());
        testField(message, "PID", 3, equalTo("P1234")); // Patient ID
        testField(message, "PID", 4, emptyString());
        testField(message, "PID", 5, equalTo("Hornblower^Horatio")); // Patient Name
        testField(message, "PID", 6, emptyString());
        testField(message, "PID", 7, equalTo(ymd.format(patient.getBirthdate())));  // Birthdate
        testField(message, "PID", 8, equalTo("M"));  // Gender
    }
}