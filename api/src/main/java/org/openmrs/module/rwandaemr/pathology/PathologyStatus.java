package org.openmrs.module.rwandaemr.pathology;

import org.openmrs.Order;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where a pathology case is in the lab.  Each status sets the fulfiller status of the case's orders, which other parts of
 * the EMR, such as the patient's orders, show.
 */
public enum PathologyStatus {

	ORDERED("Ordered", null),
	RECEIVED("Received", Order.FulfillerStatus.RECEIVED),
	GROSSED("Grossed", Order.FulfillerStatus.IN_PROGRESS),
	PROCESSED("Processed", Order.FulfillerStatus.IN_PROGRESS),
	ADDITIONAL_STAINS("Additional stains", Order.FulfillerStatus.IN_PROGRESS),
	SIGNED_OUT("Signed out", Order.FulfillerStatus.COMPLETED),
	REJECTED("Rejected", Order.FulfillerStatus.DECLINED);

	private final String label;
	private final Order.FulfillerStatus fulfillerStatus;

	PathologyStatus(String label, Order.FulfillerStatus fulfillerStatus) {
		this.label = label;
		this.fulfillerStatus = fulfillerStatus;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * @return the fulfiller status of the case's orders while the case has this status, or null if the lab has not
	 * received the specimen yet
	 */
	public Order.FulfillerStatus getFulfillerStatus() {
		return fulfillerStatus;
	}

	/**
	 * @return the statuses a case can move to from this one.  Steps a specimen does not need can be skipped, such as
	 * grossing for a bone marrow aspirate, but a case never moves back.  A specimen can only be rejected before the lab
	 * starts working on it.
	 */
	public List<PathologyStatus> getNextStatuses() {
		switch (this) {
			case ORDERED:
				return Arrays.asList(RECEIVED, REJECTED);
			case RECEIVED:
				return Arrays.asList(GROSSED, PROCESSED, REJECTED);
			case GROSSED:
				return Collections.singletonList(PROCESSED);
			case PROCESSED:
				return Arrays.asList(ADDITIONAL_STAINS, SIGNED_OUT);
			case ADDITIONAL_STAINS:
				return Collections.singletonList(SIGNED_OUT);
			default:
				return Collections.emptyList();
		}
	}

	/**
	 * @return true if the lab has the specimen and has not signed out its report yet
	 */
	public boolean isInLab() {
		return this == RECEIVED || this == GROSSED || this == PROCESSED || this == ADDITIONAL_STAINS;
	}

	public boolean canMoveTo(PathologyStatus status) {
		return getNextStatuses().contains(status);
	}
}
