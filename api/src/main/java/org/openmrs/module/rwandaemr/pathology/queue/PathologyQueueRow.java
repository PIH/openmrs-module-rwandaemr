package org.openmrs.module.rwandaemr.pathology.queue;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One row of the pathology queue: a pathology request, with the tests ordered on its request form
 */
@Getter
@Setter
public class PathologyQueueRow {

	private Integer encounterId;
	private String encounterUuid;
	private Integer caseId;
	private String accessionNumber;
	private PathologyStatus status;
	/**
	 * True when every test of the request was cancelled before the lab received the specimen
	 */
	private boolean cancelled;
	private Date statusChangedAt;
	private String rejectionReason;
	private List<Integer> orderIds = new ArrayList<Integer>();
	private List<String> orderNumbers = new ArrayList<String>();
	private List<String> tests = new ArrayList<String>();
	private boolean urgent;
	/**
	 * The additional information given with the tests on the request form
	 */
	private String additionalInformation;
	private Integer patientId;
	private String patientUuid;
	private String patientIdentifier;
	private String patientName;
	private Integer age;
	private String sex;
	private String hospitalOfOrigin;
	private List<String> specimens = new ArrayList<String>();
	private String otherTestsRequested;
	private String requestingPhysician;
	private String requestingDepartment;
	private Date timeRequested;
	private PaymentStatus paymentStatus;
	private Date timeOfPayment;
	private List<HistoryEntry> history = new ArrayList<HistoryEntry>();

	public String getStatusLabel() {
		return cancelled ? "Cancelled" : status.getLabel();
	}

	/**
	 * A status the case was moved to, by whom and when
	 */
	@Getter
	public static class HistoryEntry {

		private final PathologyStatus status;
		private final String reason;
		private final String by;
		private final Date date;

		public HistoryEntry(PathologyStatus status, String reason, String by, Date date) {
			this.status = status;
			this.reason = reason;
			this.by = by;
			this.date = date;
		}
	}
}
