package org.openmrs.module.rwandaemr.page.controller.radiology;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow;

final class RadiologyQueueExcel {

	static final String CONTENT_TYPE = "application/vnd.ms-excel";

	private static final String[] HEADERS = {
			"S.No", "Patient ID", "Patient Name", "Age", "Sex", "Modality", "Type of Study", "Clinical History",
			"Requesting Physician", "Requesting Department", "Time Requested", "PACS Status", "Processed",
			"Payment Status", "Time of Payment", "Order Number(s)", "Urgency", "Ready for Exam"
	};

	private static final int[] COLUMN_WIDTHS = {
			7, 16, 28, 7, 6, 10, 40, 40, 26, 24, 18, 16, 15, 16, 18, 24, 10, 14
	};

	private RadiologyQueueExcel() {
	}

	static byte[] create(List<RadiologyQueueRow> rows) {
		Workbook workbook = new HSSFWorkbook();
		Sheet sheet = workbook.createSheet("Radiology queue");
		CellStyle dateStyle = workbook.createCellStyle();
		dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
		CellStyle wrapStyle = workbook.createCellStyle();
		wrapStyle.setWrapText(true);

		Row header = sheet.createRow(0);
		for (int column = 0; column < HEADERS.length; column++) {
			header.createCell(column).setCellValue(HEADERS[column]);
			sheet.setColumnWidth(column, COLUMN_WIDTHS[column] * 256);
		}

		int rowNumber = 1;
		for (RadiologyQueueRow queueRow : rows) {
			Row row = sheet.createRow(rowNumber);
			row.createCell(0).setCellValue(rowNumber);
			setText(row, 1, queueRow.getPatientIdentifier(), null);
			setText(row, 2, queueRow.getPatientName(), null);
			if (queueRow.getAge() != null) {
				row.createCell(3).setCellValue(queueRow.getAge());
			}
			setText(row, 4, queueRow.getSex(), null);
			setText(row, 5, queueRow.getModality(), null);
			setText(row, 6, queueRow.getTypeOfStudy(), wrapStyle);
			setText(row, 7, queueRow.getClinicalHistory(), wrapStyle);
			setText(row, 8, queueRow.getRequestingPhysician(), null);
			setText(row, 9, queueRow.getRequestingDepartment(), null);
			setDate(row, 10, queueRow.getTimeRequested(), dateStyle);
			setText(row, 11, queueRow.getPacsStatus() == null ? null : queueRow.getPacsStatus().getLabel(), null);
			setText(row, 12, queueRow.getProcessedLabel(), null);
			setText(row, 13, queueRow.getPaymentStatus() == null ? null : queueRow.getPaymentStatus().getLabel(), null);
			setDate(row, 14, queueRow.getTimeOfPayment(), dateStyle);
			setText(row, 15, String.join(", ", queueRow.getOrderNumbers()), null);
			setText(row, 16, queueRow.isStat() ? "STAT" : "Routine", null);
			setText(row, 17, queueRow.isReadyForExam() ? "Yes" : "No", null);
			rowNumber++;
		}

		sheet.createFreezePane(0, 1);
		sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowNumber - 1), 0, HEADERS.length - 1));

		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			workbook.write(output);
			return output.toByteArray();
		}
		catch (IOException e) {
			throw new IllegalStateException("Unable to create radiology queue Excel file", e);
		}
	}

	private static void setText(Row row, int column, String value, CellStyle style) {
		Cell cell = row.createCell(column);
		cell.setCellValue(value == null ? "" : value);
		if (style != null) {
			cell.setCellStyle(style);
		}
	}

	private static void setDate(Row row, int column, Date value, CellStyle style) {
		Cell cell = row.createCell(column);
		if (value != null) {
			cell.setCellValue(value);
			cell.setCellStyle(style);
		}
	}
}
