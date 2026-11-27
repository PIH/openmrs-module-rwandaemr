package org.openmrs.module.rwandaemr.billing.verification;

/**
 * The bills of a period at a step of the verification, and what happened when they were all submitted together: sent
 * to the insurer by the facility, or answered by the insurer
 */
public class BulkSendResult {

	private int ready;
	private int notReady;
	private int noItems;
	private int sentToInsurer;
	private int closed;
	private int sentToFacility;
	private int refused;

	/**
	 * @return the bills whose every item the facility has decided, so can be sent
	 */
	public int getReady() {
		return ready;
	}

	void addReady() {
		ready++;
	}

	/**
	 * @return the bills with items the facility has not decided yet, which stay at the facility review
	 */
	public int getNotReady() {
		return notReady;
	}

	void addNotReady() {
		notReady++;
	}

	/**
	 * @return the bills without items to verify, which stay at the facility review
	 */
	public int getNoItems() {
		return noItems;
	}

	void addNoItems() {
		noItems++;
	}

	/**
	 * @return the bills sent to the insurer
	 */
	public int getSentToInsurer() {
		return sentToInsurer;
	}

	void addSentToInsurer() {
		sentToInsurer++;
	}

	/**
	 * @return the bills closed because the facility rejected every item, so the insurer had nothing to review
	 */
	public int getClosed() {
		return closed;
	}

	void addClosed() {
		closed++;
	}

	/**
	 * @return the bills the insurer sent back to the facility, to respond to the items it rejected
	 */
	public int getSentToFacility() {
		return sentToFacility;
	}

	void addSentToFacility() {
		sentToFacility++;
	}

	/**
	 * @return the bills that could not be submitted, for example because they are of an insurance the user does not
	 * verify for
	 */
	public int getRefused() {
		return refused;
	}

	void addRefused() {
		refused++;
	}
}
