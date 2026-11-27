package org.openmrs.module.rwandaemr.billing.verification;

import java.util.Date;

/**
 * How far a lab test got, without its results: when it was ordered, when the lab recorded the specimen, and when the
 * results were entered, each with who did it.  This is what a verifier needs to know whether a billed test was done.
 */
public class LabTestProgress {

	public enum Status {
		ORDERED("Ordered, no specimen yet"),
		RECEIVED("Specimen recorded, no result yet"),
		COMPLETED("Completed"),
		NOT_PERFORMED("Not performed"),
		CANCELLED("Cancelled");

		private final String label;

		Status(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	private Integer orderId;
	private String orderNumber;
	private String testName;
	private String urgency;
	private Date orderedAt;
	private String orderedBy;
	private Date specimenRecordedAt;
	private String specimenRecordedBy;
	private Date resultsEnteredAt;
	private String resultsEnteredBy;
	private String fulfillerStatus;
	private String notPerformedReason;
	private boolean cancelled;

	/**
	 * @return how far the test got.  A result entered means it was done, even if the lab did not record the
	 * specimen; a reason for not doing the test means it was not done.
	 */
	public Status getStatus() {
		if (notPerformedReason != null || "EXCEPTION".equals(fulfillerStatus) || "DECLINED".equals(fulfillerStatus)) {
			return Status.NOT_PERFORMED;
		}
		if (resultsEnteredAt != null || "COMPLETED".equals(fulfillerStatus)) {
			return Status.COMPLETED;
		}
		if (cancelled) {
			return Status.CANCELLED;
		}
		if (specimenRecordedAt != null || "RECEIVED".equals(fulfillerStatus) || "IN_PROGRESS".equals(fulfillerStatus)) {
			return Status.RECEIVED;
		}
		return Status.ORDERED;
	}

	public Integer getOrderId() {
		return orderId;
	}

	public void setOrderId(Integer orderId) {
		this.orderId = orderId;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}

	public String getTestName() {
		return testName;
	}

	public void setTestName(String testName) {
		this.testName = testName;
	}

	public String getUrgency() {
		return urgency;
	}

	public void setUrgency(String urgency) {
		this.urgency = urgency;
	}

	public Date getOrderedAt() {
		return orderedAt;
	}

	public void setOrderedAt(Date orderedAt) {
		this.orderedAt = orderedAt;
	}

	public String getOrderedBy() {
		return orderedBy;
	}

	public void setOrderedBy(String orderedBy) {
		this.orderedBy = orderedBy;
	}

	public Date getSpecimenRecordedAt() {
		return specimenRecordedAt;
	}

	public void setSpecimenRecordedAt(Date specimenRecordedAt) {
		this.specimenRecordedAt = specimenRecordedAt;
	}

	public String getSpecimenRecordedBy() {
		return specimenRecordedBy;
	}

	public void setSpecimenRecordedBy(String specimenRecordedBy) {
		this.specimenRecordedBy = specimenRecordedBy;
	}

	public Date getResultsEnteredAt() {
		return resultsEnteredAt;
	}

	public void setResultsEnteredAt(Date resultsEnteredAt) {
		this.resultsEnteredAt = resultsEnteredAt;
	}

	public String getResultsEnteredBy() {
		return resultsEnteredBy;
	}

	public void setResultsEnteredBy(String resultsEnteredBy) {
		this.resultsEnteredBy = resultsEnteredBy;
	}

	public String getFulfillerStatus() {
		return fulfillerStatus;
	}

	public void setFulfillerStatus(String fulfillerStatus) {
		this.fulfillerStatus = fulfillerStatus;
	}

	public String getNotPerformedReason() {
		return notPerformedReason;
	}

	public void setNotPerformedReason(String notPerformedReason) {
		this.notPerformedReason = notPerformedReason;
	}

	/**
	 * @return true if the order was discontinued or voided
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	public void setCancelled(boolean cancelled) {
		this.cancelled = cancelled;
	}
}
