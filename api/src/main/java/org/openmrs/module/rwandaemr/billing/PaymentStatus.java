package org.openmrs.module.rwandaemr.billing;

/**
 * Whether what was billed for an order has been paid
 */
public enum PaymentStatus {
	PAID("Paid"),
	PARTIALLY_PAID("Partially paid"),
	NOT_PAID("Not paid"),
	NOT_BILLED("Not billed");

	private final String label;

	PaymentStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
