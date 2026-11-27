package org.openmrs.module.rwandaemr.billing.verification;

/**
 * A reason for rejecting a bill item, or for disputing the rejection of one.  The list starts empty: each reason
 * typed as "Other" is saved and offered from then on.
 */
public class VerificationReason {

	public enum Type {
		REJECTION, DISPUTE
	}

	private Integer reasonId;
	private Type type;
	private String name;
	private boolean retired;

	public VerificationReason() {
	}

	public VerificationReason(Integer reasonId, Type type, String name, boolean retired) {
		this.reasonId = reasonId;
		this.type = type;
		this.name = name;
		this.retired = retired;
	}

	public Integer getReasonId() {
		return reasonId;
	}

	public void setReasonId(Integer reasonId) {
		this.reasonId = reasonId;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isRetired() {
		return retired;
	}

	public void setRetired(boolean retired) {
		this.retired = retired;
	}
}
