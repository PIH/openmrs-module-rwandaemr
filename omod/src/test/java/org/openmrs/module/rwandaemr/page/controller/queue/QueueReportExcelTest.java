package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Date;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PersonName;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

public class QueueReportExcelTest {

    @Test
    public void shouldCreateExcelReportWithDisplayedQueueValues() throws Exception {
        QueueEntry entry = new QueueEntry();
        entry.setId(42);
        entry.setQueueNumber("EMERG-001");
        Patient patient = new Patient(12);
        patient.addName(new PersonName("Aline", null, "Uwase"));
        entry.setPatient(patient);
        Location servicePoint = new Location(7);
        servicePoint.setName("Emergency");
        entry.setServicePoint(servicePoint);
        entry.setTransferReason("Needs urgent consultation");
        entry.setPriority(QueuePriority.NORMAL);
        entry.setStatus(QueueStatus.CALLED);
        entry.setArrivalTime(new Date(1_000_000L));
        entry.setCompletedTime(new Date(2_000_000L));

        byte[] report = QueueReportExcel.create(Collections.singletonList(entry),
                Collections.singletonMap(42, "12 min"));

        assertTrue(report.length > 0);
        Workbook workbook = new HSSFWorkbook(new ByteArrayInputStream(report));
        Sheet sheet = workbook.getSheet("Queue report");
        assertEquals("Queue #", sheet.getRow(0).getCell(0).getStringCellValue());

        Row row = sheet.getRow(1);
        assertEquals("EMERG-001", row.getCell(0).getStringCellValue());
        assertEquals("Aline Uwase", row.getCell(1).getStringCellValue());
        assertEquals("Emergency", row.getCell(2).getStringCellValue());
        assertEquals("Needs urgent consultation", row.getCell(3).getStringCellValue());
        assertEquals("Not Emergency", row.getCell(4).getStringCellValue());
        assertEquals("Called", row.getCell(5).getStringCellValue());
        assertEquals("12 min", row.getCell(6).getStringCellValue());
        assertEquals(entry.getArrivalTime(), row.getCell(7).getDateCellValue());
        assertEquals(entry.getCompletedTime(), row.getCell(8).getDateCellValue());
    }
}
