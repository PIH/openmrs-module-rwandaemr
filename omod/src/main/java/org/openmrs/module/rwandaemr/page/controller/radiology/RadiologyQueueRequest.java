package org.openmrs.module.rwandaemr.page.controller.radiology;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The date range and filters requested for the radiology queue, shared by the page, its Excel export and the data
 * endpoint used to refresh it
 */
public class RadiologyQueueRequest {

	public static final int MAX_DAYS = 93;

	private final String startDate;
	private final String endDate;
	private final String modality;
	private final String pacsStatus;
	private final String paymentStatus;
	private final String processed;
	private final String query;
	private boolean statOnly;
	private String error;
	private Date fromDate;
	private Date toDate;

	public RadiologyQueueRequest(String startDate, String endDate, String modality, String pacsStatus,
								 String paymentStatus, String processed, String query) {
		String today = LocalDate.now().toString();
		this.startDate = StringUtils.isBlank(startDate) ? today : startDate.trim();
		this.endDate = StringUtils.isBlank(endDate) ? this.startDate : endDate.trim();
		this.modality = StringUtils.trimToEmpty(modality);
		this.pacsStatus = StringUtils.trimToEmpty(pacsStatus);
		this.paymentStatus = StringUtils.trimToEmpty(paymentStatus);
		this.processed = StringUtils.trimToEmpty(processed);
		this.query = StringUtils.trimToEmpty(query);
		try {
			LocalDate start = LocalDate.parse(this.startDate);
			LocalDate end = LocalDate.parse(this.endDate);
			if (end.isBefore(start)) {
				error = "End date must be on or after start date";
			}
			else if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
				error = "Choose a date range of at most " + MAX_DAYS + " days";
			}
			else {
				fromDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
				toDate = new Date(Date.from(end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()).getTime() - 1);
			}
		}
		catch (DateTimeParseException e) {
			error = "Start date and end date must be valid dates (yyyy-MM-dd)";
		}
	}

	/**
	 * Limits the rows to STAT requests
	 */
	public void setStatOnly(boolean statOnly) {
		this.statOnly = statOnly;
	}

	/**
	 * @return the rows that match the modality, status and text filters
	 */
	public List<RadiologyQueueRow> filter(List<RadiologyQueueRow> rows) {
		List<RadiologyQueueRow> ret = new ArrayList<RadiologyQueueRow>();
		String text = query.toLowerCase(Locale.ENGLISH);
		for (RadiologyQueueRow row : rows) {
			if (statOnly && !row.isStat()) {
				continue;
			}
			if (StringUtils.isNotEmpty(modality) && !modality.equalsIgnoreCase(row.getModality())) {
				continue;
			}
			if (StringUtils.isNotEmpty(pacsStatus) && (row.getPacsStatus() == null
					|| !pacsStatus.equalsIgnoreCase(row.getPacsStatus().name()))) {
				continue;
			}
			if (StringUtils.isNotEmpty(paymentStatus) && (row.getPaymentStatus() == null
					|| !paymentStatus.equalsIgnoreCase(row.getPaymentStatus().name()))) {
				continue;
			}
			if ("YES".equalsIgnoreCase(processed) && !row.isProcessed()
					|| "NO".equalsIgnoreCase(processed) && row.isProcessed()
					|| "READY".equalsIgnoreCase(processed) && !row.isReadyForExam()) {
				continue;
			}
			if (StringUtils.isNotEmpty(text)) {
				String searchable = (StringUtils.defaultString(row.getPatientIdentifier()) + " "
						+ StringUtils.defaultString(row.getPatientName()) + " "
						+ StringUtils.join(row.getOrderNumbers(), " ") + " "
						+ StringUtils.defaultString(row.getTypeOfStudy())).toLowerCase(Locale.ENGLISH);
				if (!searchable.contains(text)) {
					continue;
				}
			}
			ret.add(row);
		}
		return ret;
	}

	public boolean isValid() {
		return error == null;
	}

	public String getError() {
		return error;
	}

	public Date getFromDate() {
		return fromDate;
	}

	public Date getToDate() {
		return toDate;
	}

	public String getStartDate() {
		return startDate;
	}

	public String getEndDate() {
		return endDate;
	}

	public String getModality() {
		return modality;
	}

	public String getPacsStatus() {
		return pacsStatus;
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public String getProcessed() {
		return processed;
	}

	public String getQuery() {
		return query;
	}
}
