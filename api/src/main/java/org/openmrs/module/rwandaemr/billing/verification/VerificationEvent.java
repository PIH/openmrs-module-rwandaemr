package org.openmrs.module.rwandaemr.billing.verification;

import java.util.Date;

/**
 * Something done in the verification of a global bill: a decision on one of its items, or the end of a step.  The
 * events of a bill are its audit trail.
 */
public class VerificationEvent {

	private final Integer globalBillId;
	private final Integer patientServiceBillId;
	private final BillItemVerificationStatus itemStatus;
	private final BillVerificationStage stage;
	private final String reasonName;
	private final String note;
	private final Integer userId;
	private final String username;
	private final Date date;

	public VerificationEvent(Integer globalBillId, Integer patientServiceBillId, BillItemVerificationStatus itemStatus,
							 BillVerificationStage stage, String reasonName, String note, Integer userId, String username,
							 Date date) {
		this.globalBillId = globalBillId;
		this.patientServiceBillId = patientServiceBillId;
		this.itemStatus = itemStatus;
		this.stage = stage;
		this.reasonName = reasonName;
		this.note = note;
		this.userId = userId;
		this.username = username;
		this.date = date;
	}

	public Integer getGlobalBillId() {
		return globalBillId;
	}

	/**
	 * @return the item decided, or null if the event is the end of a step
	 */
	public Integer getPatientServiceBillId() {
		return patientServiceBillId;
	}

	/**
	 * @return the status the item was moved to, or null if the event is the end of a step
	 */
	public BillItemVerificationStatus getItemStatus() {
		return itemStatus;
	}

	/**
	 * @return the step the bill was moved to, or null if the event is a decision on an item
	 */
	public BillVerificationStage getStage() {
		return stage;
	}

	public String getReasonName() {
		return reasonName;
	}

	public String getNote() {
		return note;
	}

	public Integer getUserId() {
		return userId;
	}

	public String getUsername() {
		return username;
	}

	public Date getDate() {
		return date;
	}
}
