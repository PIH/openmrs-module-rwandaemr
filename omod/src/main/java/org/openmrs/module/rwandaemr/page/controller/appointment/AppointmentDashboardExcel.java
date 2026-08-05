package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

final class AppointmentDashboardExcel {

    static final String CONTENT_TYPE = "application/vnd.ms-excel";

    private static final String[] HEADERS = {
            "Date", "Service point", "Patient", "Identifier", "Program", "Visit type",
            "Appointment status", "Requested", "Notes", "Booked", "Maximum", "Remaining", "Schedule status"
    };

    private static final int[] COLUMN_WIDTHS = {
            14, 28, 30, 20, 24, 16, 20, 20, 35, 12, 12, 12, 18
    };

    private AppointmentDashboardExcel() {
    }

    static byte[] create(List<AppointmentScheduleSummary> summaries,
                         Map<Integer, List<AppointmentBooking>> bookingsByScheduleId) {
        List<AppointmentScheduleSummary> reportSummaries = summaries == null
                ? Collections.<AppointmentScheduleSummary>emptyList() : summaries;
        Map<Integer, List<AppointmentBooking>> reportBookings = bookingsByScheduleId == null
                ? Collections.<Integer, List<AppointmentBooking>>emptyMap() : bookingsByScheduleId;

        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Appointments");
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        CellStyle dateTimeStyle = workbook.createCellStyle();
        dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            header.createCell(column).setCellValue(HEADERS[column]);
            sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
        }

        int rowNumber = 1;
        for (AppointmentScheduleSummary summary : reportSummaries) {
            AppointmentSchedule schedule = summary.getSchedule();
            if (schedule == null) {
                continue;
            }
            List<AppointmentBooking> bookings = reportBookings.get(schedule.getId());
            if (bookings == null || bookings.isEmpty()) {
                writeRow(sheet.createRow(rowNumber++), summary, null, dateStyle, dateTimeStyle);
            }
            else {
                for (AppointmentBooking booking : bookings) {
                    writeRow(sheet.createRow(rowNumber++), summary, booking, dateStyle, dateTimeStyle);
                }
            }
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, HEADERS.length - 1));

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to create appointment dashboard Excel file", e);
        }
    }

    private static void writeRow(Row row, AppointmentScheduleSummary summary, AppointmentBooking booking,
                                 CellStyle dateStyle, CellStyle dateTimeStyle) {
        AppointmentSchedule schedule = summary.getSchedule();
        setDate(row, 0, schedule.getScheduleDate(), dateStyle);
        setText(row, 1, schedule.getServicePoint() == null ? null : schedule.getServicePoint().getName());
        setText(row, 2, patientName(booking));
        setText(row, 3, patientIdentifier(booking));
        setText(row, 4, booking == null || booking.getProgram() == null ? null : booking.getProgram().getName());
        setText(row, 5, booking == null || booking.getVisitType() == null
                ? null : booking.getVisitType().getDisplayName());
        setText(row, 6, booking == null ? null : label(booking.getStatus()));
        setDate(row, 7, booking == null ? null : booking.getRequestedAt(), dateTimeStyle);
        setText(row, 8, booking == null ? null : booking.getNotes());
        setNumber(row, 9, summary.getBookedPatients());
        setNumber(row, 10, schedule.getMaximumPatients());
        setNumber(row, 11, summary.getRemainingCapacity());
        setText(row, 12, Boolean.TRUE.equals(schedule.getActive()) ? "Open" : "Closed");
    }

    private static void setText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void setNumber(Row row, int column, Number value) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static void setDate(Row row, int column, Date value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private static String patientName(AppointmentBooking booking) {
        if (booking == null || booking.getPatient() == null || booking.getPatient().getPersonName() == null) {
            return "";
        }
        return booking.getPatient().getPersonName().getFullName();
    }

    private static String patientIdentifier(AppointmentBooking booking) {
        if (booking == null || booking.getPatient() == null) {
            return "";
        }
        PatientIdentifier identifier = booking.getPatient().getPatientIdentifier();
        return identifier == null ? "" : identifier.getIdentifier();
    }

    private static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String label = value.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
