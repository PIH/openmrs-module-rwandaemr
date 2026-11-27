package org.openmrs.module.rwandaemr.billing.verification;

import java.util.Date;

/**
 * The latest decision on an item of a global bill, with the reason given for it
 */
public class BillItemVerification {

	private Integer patientServiceBillId;
	private Integer globalBillId;
	private BillItemVerificationStatus status = BillItemVerificationStatus.PENDING;
	private Integer reasonId;
	private String reasonName;
	private String note;
	private Integer changedBy;
	private Date dateChanged;

	public Integer getPatientServiceBillId() {
		return patientServiceBillId;
	}

	public void setPatientServiceBillId(Integer patientServiceBillId) {
		this.patientServiceBillId = patientServiceBillId;
	}

	public Integer getGlobalBillId() {
		return globalBillId;
	}

	public void setGlobalBillId(Integer globalBillId) {
		this.globalBillId = globalBillId;
	}

	public BillItemVerificationStatus getStatus() {
		return status;
	}

	public void setStatus(BillItemVerificationStatus status) {
		this.status = status;
	}

	public Integer getReasonId() {
		return reasonId;
	}

	public void setReasonId(Integer reasonId) {
		this.reasonId = reasonId;
	}

	public String getReasonName() {
		return reasonName;
	}

	public void setReasonName(String reasonName) {
		this.reasonName = reasonName;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	/**
	 * @return the user who made the latest decision
	 */
	public Integer getChangedBy() {
		return changedBy;
	}

	public void setChangedBy(Integer changedBy) {
		this.changedBy = changedBy;
	}

	public Date getDateChanged() {
		return dateChanged;
	}

	public void setDateChanged(Date dateChanged) {
		this.dateChanged = dateChanged;
	}
}
