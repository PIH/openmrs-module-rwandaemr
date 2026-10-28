package org.openmrs.module.rwandaemr.page.controller.pathology;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The date range and filters requested for the pathology queue, shared by the page, its Excel export and the data
 * endpoint used to refresh it
 */
public class PathologyQueueRequest {

	public static final int MAX_DAYS = 93;

	/**
	 * Specimens can reach the lab days after they are ordered, so the queue shows the last week by default
	 */
	public static final int DEFAULT_DAYS = 7;

	public static final String CANCELLED = "CANCELLED";

	private final String startDate;
	private final String endDate;
	private final String status;
	private final String paymentStatus;
	private final String test;
	private final String query;
	private final boolean includeCasesInLab;
	private boolean urgentOnly;
	private String error;
	private Date fromDate;
	private Date toDate;

	public PathologyQueueRequest(String startDate, String endDate, String status, String paymentStatus, String test,
								 String query, String inLab) {
		LocalDate today = LocalDate.now();
		this.endDate = StringUtils.isBlank(endDate) ? today.toString() : endDate.trim();
		this.startDate = StringUtils.isBlank(startDate) ? today.minusDays(DEFAULT_DAYS).toString() : startDate.trim();
		this.status = StringUtils.trimToEmpty(status);
		this.paymentStatus = StringUtils.trimToEmpty(paymentStatus);
		this.test = StringUtils.trimToEmpty(test);
		this.query = StringUtils.trimToEmpty(query);
		this.includeCasesInLab = !"NO".equalsIgnoreCase(StringUtils.trimToEmpty(inLab));
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
	 * Limits the rows to urgent requests
	 */
	public void setUrgentOnly(boolean urgentOnly) {
		this.urgentOnly = urgentOnly;
	}

	/**
	 * @return the rows that match the status, payment, test and text filters
	 */
	public List<PathologyQueueRow> filter(List<PathologyQueueRow> rows) {
		List<PathologyQueueRow> ret = new ArrayList<PathologyQueueRow>();
		String text = query.toLowerCase(Locale.ENGLISH);
		for (PathologyQueueRow row : rows) {
			if (urgentOnly && (!row.isUrgent() || row.isCancelled())) {
				continue;
			}
			String rowStatus = row.isCancelled() ? CANCELLED : row.getStatus().name();
			if (StringUtils.isNotEmpty(status) && !status.equalsIgnoreCase(rowStatus)) {
				continue;
			}
			if (StringUtils.isNotEmpty(paymentStatus) && (row.getPaymentStatus() == null
					|| !paymentStatus.equalsIgnoreCase(row.getPaymentStatus().name()))) {
				continue;
			}
			if (StringUtils.isNotEmpty(test) && !row.getTests().contains(test)) {
				continue;
			}
			if (StringUtils.isNotEmpty(text)) {
				String searchable = (StringUtils.defaultString(row.getAccessionNumber()) + " "
						+ StringUtils.defaultString(row.getPatientIdentifier()) + " "
						+ StringUtils.defaultString(row.getPatientName()) + " "
						+ StringUtils.join(row.getOrderNumbers(), " ") + " "
						+ StringUtils.defaultString(row.getHospitalOfOrigin())).toLowerCase(Locale.ENGLISH);
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

	public String getStatus() {
		return status;
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public String getTest() {
		return test;
	}

	public String getQuery() {
		return query;
	}

	public boolean isIncludeCasesInLab() {
		return includeCasesInLab;
	}
}
