package org.openmrs.module.rwandaemr.billing.verification;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.DISPUTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.FACILITY_ACCEPTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.FACILITY_REJECTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.FINAL_ACCEPTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.FINAL_REJECTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.INSURER_ACCEPTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.INSURER_REJECTED;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.PENDING;
import static org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus.REJECTION_ACCEPTED;

/**
 * Where a global bill is in its verification, and who acts on its items at that step.  A step is over when whoever
 * acts at it submits their decisions; the insurer can only start once the facility has sent the bill to it.
 */
public enum BillVerificationStage {

	FACILITY_REVIEW("Facility review", Actor.FACILITY, FACILITY_ACCEPTED, FACILITY_REJECTED,
			Arrays.asList(PENDING, FACILITY_ACCEPTED, FACILITY_REJECTED)),
	INSURER_REVIEW("Insurer review", Actor.INSURER, INSURER_ACCEPTED, INSURER_REJECTED,
			Arrays.asList(FACILITY_ACCEPTED, INSURER_ACCEPTED, INSURER_REJECTED)),
	FACILITY_RESPONSE("Facility response", Actor.FACILITY, REJECTION_ACCEPTED, DISPUTED,
			Arrays.asList(INSURER_REJECTED, REJECTION_ACCEPTED, DISPUTED)),
	FINAL_REVIEW("Final review", Actor.INSURER, FINAL_ACCEPTED, FINAL_REJECTED,
			Arrays.asList(DISPUTED, FINAL_ACCEPTED, FINAL_REJECTED)),
	CLOSED("Closed", null, null, null, Collections.<BillItemVerificationStatus>emptyList());

	public enum Actor {
		FACILITY, INSURER
	}

	private final String label;
	private final Actor actor;
	private final BillItemVerificationStatus acceptStatus;
	private final BillItemVerificationStatus rejectStatus;
	private final List<BillItemVerificationStatus> reviewableStatuses;

	BillVerificationStage(String label, Actor actor, BillItemVerificationStatus acceptStatus,
						  BillItemVerificationStatus rejectStatus, List<BillItemVerificationStatus> reviewableStatuses) {
		this.label = label;
		this.actor = actor;
		this.acceptStatus = acceptStatus;
		this.rejectStatus = rejectStatus;
		this.reviewableStatuses = reviewableStatuses;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * @return who acts on the items at this step, or null once the verification is over
	 */
	public Actor getActor() {
		return actor;
	}

	/**
	 * @return the status of an item accepted at this step.  When the facility responds to the insurer, accepting
	 * means accepting the rejection.
	 */
	public BillItemVerificationStatus getAcceptStatus() {
		return acceptStatus;
	}

	/**
	 * @return the status of an item rejected at this step.  When the facility responds to the insurer, rejecting
	 * means disputing the rejection.
	 */
	public BillItemVerificationStatus getRejectStatus() {
		return rejectStatus;
	}

	/**
	 * @return what accepting an item means at this step
	 */
	public String getAcceptLabel() {
		return this == FACILITY_RESPONSE ? "Accept rejection" : "Accept";
	}

	/**
	 * @return what rejecting an item means at this step
	 */
	public String getRejectLabel() {
		return this == FACILITY_RESPONSE ? "Dispute" : "Reject";
	}

	/**
	 * @return what submitting this step does
	 */
	public String getSubmitLabel() {
		switch (this) {
			case FACILITY_REVIEW:
				return "Send to insurer";
			case INSURER_REVIEW:
				return "Submit review";
			case FACILITY_RESPONSE:
				return "Submit response";
			case FINAL_REVIEW:
				return "Submit final decision";
			default:
				return "";
		}
	}

	/**
	 * @return true if an item with the given status can be decided at this step, including changing a decision
	 * made at this step before it is submitted
	 */
	public boolean isReviewable(BillItemVerificationStatus status) {
		return reviewableStatuses.contains(status);
	}

	/**
	 * @return true if an item with the given status still needs a decision before this step can be submitted
	 */
	public boolean isAwaitingDecision(BillItemVerificationStatus status) {
		return isReviewable(status) && status != acceptStatus && status != rejectStatus;
	}
}
