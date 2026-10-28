package org.openmrs.module.rwandaemr.page.controller.pathology;

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
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;

final class PathologyQueueExcel {

	static final String CONTENT_TYPE = "application/vnd.ms-excel";

	private static final String[] HEADERS = {
			"No", "Accession Number", "Patient ID", "Patient Name", "Age", "Sex", "Hospital of Origin", "Order Type",
			"Specimens", "Other Tests Requested", "Requesting Physician", "Time Requested", "Payment Status",
			"Time of Payment", "Sample Status", "Status Changed", "Rejection Reason", "Order Number(s)", "Urgency",
			"Additional Information"
	};

	private static final int[] COLUMN_WIDTHS = {
			6, 16, 16, 28, 7, 6, 26, 40, 36, 30, 26, 18, 16, 18, 18, 18, 30, 24, 10, 36
	};

	private PathologyQueueExcel() {
	}

	static byte[] create(List<PathologyQueueRow> rows) {
		Workbook workbook = new HSSFWorkbook();
		Sheet sheet = workbook.createSheet("Pathology queue");
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
		for (PathologyQueueRow queueRow : rows) {
			Row row = sheet.createRow(rowNumber);
			row.createCell(0).setCellValue(rowNumber);
			setText(row, 1, queueRow.getAccessionNumber(), null);
			setText(row, 2, queueRow.getPatientIdentifier(), null);
			setText(row, 3, queueRow.getPatientName(), null);
			if (queueRow.getAge() != null) {
				row.createCell(4).setCellValue(queueRow.getAge());
			}
			setText(row, 5, queueRow.getSex(), null);
			setText(row, 6, queueRow.getHospitalOfOrigin(), null);
			setText(row, 7, String.join(", ", queueRow.getTests()), wrapStyle);
			setText(row, 8, String.join("; ", queueRow.getSpecimens()), wrapStyle);
			setText(row, 9, queueRow.getOtherTestsRequested(), wrapStyle);
			setText(row, 10, queueRow.getRequestingPhysician(), null);
			setDate(row, 11, queueRow.getTimeRequested(), dateStyle);
			setText(row, 12, queueRow.getPaymentStatus() == null ? null : queueRow.getPaymentStatus().getLabel(), null);
			setDate(row, 13, queueRow.getTimeOfPayment(), dateStyle);
			setText(row, 14, queueRow.getStatusLabel(), null);
			setDate(row, 15, queueRow.getStatusChangedAt(), dateStyle);
			setText(row, 16, queueRow.getRejectionReason(), wrapStyle);
			setText(row, 17, String.join(", ", queueRow.getOrderNumbers()), null);
			setText(row, 18, queueRow.isUrgent() ? "Urgent" : "Routine", null);
			setText(row, 19, queueRow.getAdditionalInformation(), wrapStyle);
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
			throw new IllegalStateException("Unable to create pathology queue Excel file", e);
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
