package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.model.v23.message.ORM_O01;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.openmrs.Order;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

public class OrderToORMTranslatorTest extends BaseHL7TranslatorTest {

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
        ORM_O01 orm_o01 = orderToORMTranslator.toORM_O01(testOrder);
        String message = orm_o01.encode();
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
        testField(message, "PID", 3, equalTo("P1234")); // Patient ID
        testField(message, "PID", 4, emptyString());
        testField(message, "PID", 5, equalTo("Hornblower^Horatio")); // Patient Name
        testField(message, "PID", 6, emptyString());
        testField(message, "PID", 7, equalTo(ymd.format(patient.getBirthdate())));  // Birthdate
        testField(message, "PID", 8, equalTo("M"));  // Gender
        testField(message, "PID", 9, emptyString());
        testField(message, "PID", 10, emptyString());
        testField(message, "PID", 11, emptyString());
        testField(message, "PID", 12, emptyString());
        testField(message, "PID", 13, equalTo("^^Twisted ankle")); // TODO: For now we have phone number commented out

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
        testField(message, "OBR", 2, equalTo("ORD-764591"));  // Order Number
        testField(message, "OBR", 3, equalTo("University Hospital")); // Center ID
        testField(message, "OBR", 4, equalTo("36657-5^X-RAY, FOOT"));  // Test Ordered
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
        message = orderToORMTranslator.toORM_O01(testOrder).encode();
        testField(message, "OBR", 5, equalTo("STAT"));

        // Test scheduled order
        testOrder.setUrgency(Order.Urgency.ON_SCHEDULED_DATE);
        testOrder.setScheduledDate(DateUtils.addDays(new Date(), 5));
        testOrder.setOrderReasonNonCoded("Check for Fracture");
        message = orderToORMTranslator.toORM_O01(testOrder).encode();
        testField(message, "OBR", 5, equalTo("ROUTINE"));
        testField(message, "OBR", 36, equalTo(ymdhms.format(testOrder.getScheduledDate())));
        testField(message, "OBR", 31, equalTo("^" + testOrder.getOrderReasonNonCoded()));

        // Test Discontinue
        testOrder.setVoided(true);
        message = orderToORMTranslator.toORM_O01(testOrder).encode();
        testField(message, "ORC", 1, equalTo("CA"));
        testOrder.setVoided(false);
        message = orderToORMTranslator.toORM_O01(testOrder).encode();
        testField(message, "ORC", 1, equalTo("NW"));
        FieldUtils.writeField(testOrder, "dateStopped", new Date(), true);
        message = orderToORMTranslator.toORM_O01(testOrder).encode();
        testField(message, "ORC", 1, equalTo("CA"));
    }
}