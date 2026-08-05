package org.openmrs.module.rwandaemr.page.controller.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.AppointmentStatus;
import org.openmrs.module.rwandaemr.appointment.AppointmentVisitType;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public class AppointmentDashboardExcelTest {

    @Test
    public void shouldCreateExcelReportWithFilteredDashboardValues() throws Exception {
        Location servicePoint = new Location(7);
        servicePoint.setName("Dental Clinic");

        AppointmentSchedule bookedSchedule = schedule(21, servicePoint, new Date(86_400_000L), 20, true);
        AppointmentSchedule emptySchedule = schedule(22, servicePoint, new Date(172_800_000L), 10, false);
        AppointmentScheduleSummary bookedSummary = new AppointmentScheduleSummary(bookedSchedule, 1);
        AppointmentScheduleSummary emptySummary = new AppointmentScheduleSummary(emptySchedule, 0);

        Patient patient = new Patient(12);
        patient.addName(new PersonName("Aline", null, "Uwase"));
        PatientIdentifier identifier = new PatientIdentifier();
        identifier.setIdentifier("RW-1234");
        identifier.setPreferred(true);
        patient.addIdentifier(identifier);

        Program program = new Program(3);
        program.setName("HIV");
        AppointmentBooking booking = new AppointmentBooking();
        booking.setPatient(patient);
        booking.setProgram(program);
        booking.setVisitType(AppointmentVisitType.FOLLOW_UP);
        booking.setStatus(AppointmentStatus.CONFIRMED);
        booking.setRequestedAt(new Date(90_000_000L));
        booking.setNotes("Bring results");

        Map<Integer, List<AppointmentBooking>> bookings = new LinkedHashMap<Integer, List<AppointmentBooking>>();
        bookings.put(bookedSchedule.getId(), Collections.singletonList(booking));
        bookings.put(emptySchedule.getId(), Collections.<AppointmentBooking>emptyList());

        byte[] report = AppointmentDashboardExcel.create(
                Arrays.asList(bookedSummary, emptySummary), bookings);

        assertTrue(report.length > 0);
        Workbook workbook = new HSSFWorkbook(new ByteArrayInputStream(report));
        Sheet sheet = workbook.getSheet("Appointments");
        assertEquals("Date", sheet.getRow(0).getCell(0).getStringCellValue());
        assertEquals("Appointment status", sheet.getRow(0).getCell(6).getStringCellValue());

        Row bookedRow = sheet.getRow(1);
        assertEquals(bookedSchedule.getScheduleDate(), bookedRow.getCell(0).getDateCellValue());
        assertEquals("Dental Clinic", bookedRow.getCell(1).getStringCellValue());
        assertEquals("Aline Uwase", bookedRow.getCell(2).getStringCellValue());
        assertEquals("RW-1234", bookedRow.getCell(3).getStringCellValue());
        assertEquals("HIV", bookedRow.getCell(4).getStringCellValue());
        assertEquals("Follow-Up", bookedRow.getCell(5).getStringCellValue());
        assertEquals("Confirmed", bookedRow.getCell(6).getStringCellValue());
        assertEquals("Bring results", bookedRow.getCell(8).getStringCellValue());
        assertEquals(1, bookedRow.getCell(9).getNumericCellValue());
        assertEquals(20, bookedRow.getCell(10).getNumericCellValue());
        assertEquals(19, bookedRow.getCell(11).getNumericCellValue());
        assertEquals("Open", bookedRow.getCell(12).getStringCellValue());

        Row emptyRow = sheet.getRow(2);
        assertEquals("", emptyRow.getCell(2).getStringCellValue());
        assertEquals(0, emptyRow.getCell(9).getNumericCellValue());
        assertEquals("Closed", emptyRow.getCell(12).getStringCellValue());
    }

    private AppointmentSchedule schedule(Integer id, Location servicePoint, Date date,
                                         int maximumPatients, boolean active) {
        AppointmentSchedule schedule = new AppointmentSchedule();
        schedule.setId(id);
        schedule.setServicePoint(servicePoint);
        schedule.setScheduleDate(date);
        schedule.setMaximumPatients(maximumPatients);
        schedule.setActive(active);
        return schedule;
    }
}
