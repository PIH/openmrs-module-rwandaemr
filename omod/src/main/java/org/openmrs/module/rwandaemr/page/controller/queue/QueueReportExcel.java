package org.openmrs.module.rwandaemr.page.controller.queue;

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
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

final class QueueReportExcel {

    static final String CONTENT_TYPE = "application/vnd.ms-excel";

    private static final String[] HEADERS = {
            "Queue #", "Patient", "Service point", "Priority", "Status", "Waiting time", "Arrival", "Completed"
    };

    private static final int[] COLUMN_WIDTHS = {
            22, 30, 28, 16, 18, 18, 20, 20
    };

    private QueueReportExcel() {
    }

    static byte[] create(List<QueueEntry> entries, Map<Integer, String> waitingTimeByEntryId) {
        List<QueueEntry> reportEntries = entries == null ? Collections.<QueueEntry>emptyList() : entries;
        Map<Integer, String> waitingTimes = waitingTimeByEntryId == null
                ? Collections.<Integer, String>emptyMap() : waitingTimeByEntryId;

        Workbook workbook = new HSSFWorkbook();
        Sheet sheet = workbook.createSheet("Queue report");
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            header.createCell(column).setCellValue(HEADERS[column]);
            sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
        }

        int rowNumber = 1;
        for (QueueEntry entry : reportEntries) {
            Row row = sheet.createRow(rowNumber++);
            setText(row, 0, entry.getQueueNumber());
            setText(row, 1, patientName(entry));
            setText(row, 2, entry.getServicePoint() == null ? null : entry.getServicePoint().getName());
            setText(row, 3, label(entry.getPriority()));
            setText(row, 4, label(entry.getStatus()));
            setText(row, 5, waitingTimes.get(entry.getId()));
            setDate(row, 6, entry.getArrivalTime(), dateStyle);
            setDate(row, 7, entry.getCompletedTime(), dateStyle);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, HEADERS.length - 1));

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to create queue report Excel file", e);
        }
    }

    private static void setText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static void setDate(Row row, int column, Date value, CellStyle dateStyle) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(dateStyle);
        }
    }

    private static String patientName(QueueEntry entry) {
        if (entry.getPatient() == null || entry.getPatient().getPersonName() == null) {
            return "";
        }
        return entry.getPatient().getPersonName().getFullName();
    }

    private static String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String label = value.name().toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
