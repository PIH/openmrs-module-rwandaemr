package org.openmrs.module.rwandaemr.billing.verification;

/**
 * Where an item of a global bill is in its verification.  The facility reviews each item first, then the insurer
 * reviews the items the facility accepted.  The facility answers each rejection of the insurer: it accepts it, or
 * disputes it, and the insurer makes the final decision on each disputed item after discussing it with the facility.
 */
public enum BillItemVerificationStatus {

	PENDING("Pending", Outcome.PENDING),
	FACILITY_ACCEPTED("Accepted by facility", Outcome.PENDING),
	FACILITY_REJECTED("Rejected by facility", Outcome.REJECTED),
	INSURER_ACCEPTED("Accepted by insurer", Outcome.ACCEPTED),
	INSURER_REJECTED("Rejected by insurer", Outcome.PENDING),
	REJECTION_ACCEPTED("Rejection accepted by facility", Outcome.REJECTED),
	DISPUTED("Disputed by facility", Outcome.PENDING),
	FINAL_ACCEPTED("Accepted after dispute", Outcome.ACCEPTED),
	FINAL_REJECTED("Rejected after dispute", Outcome.REJECTED);

	/**
	 * Whether the item will be paid, once the verification is over
	 */
	public enum Outcome {
		PENDING, ACCEPTED, REJECTED
	}

	private final String label;
	private final Outcome outcome;

	BillItemVerificationStatus(String label, Outcome outcome) {
		this.label = label;
		this.outcome = outcome;
	}

	public String getLabel() {
		return label;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	/**
	 * @return true if moving an item to this status needs a reason
	 */
	public boolean isReasonRequired() {
		return this == FACILITY_REJECTED || this == INSURER_REJECTED || this == DISPUTED || this == FINAL_REJECTED;
	}

	/**
	 * @return the kind of reason given when an item moves to this status
	 */
	public VerificationReason.Type getReasonType() {
		return this == DISPUTED ? VerificationReason.Type.DISPUTE : VerificationReason.Type.REJECTION;
	}
}
