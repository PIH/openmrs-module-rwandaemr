package org.openmrs.module.rwandaemr.billing.verification;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.lang3.time.DateUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the items of global bills, in four steps:
 * <ol>
 *     <li>the facility accepts or rejects each item, then sends the bill to the insurer</li>
 *     <li>the insurer accepts or rejects each item the facility accepted</li>
 *     <li>the facility accepts each rejection of the insurer, or disputes it</li>
 *     <li>after discussing each disputed item with the facility, the insurer accepts or rejects it for good</li>
 * </ol>
 * A step is over when whoever acts at it submits their decisions; the bill skips the steps it does not need.  Each
 * rejection and each dispute needs a reason, chosen from the reasons given before or typed as a new one.
 */
@Component
public class BillVerificationService {

	public static final String PRIVILEGE_FACILITY = "Billing RHIP Verification - Facility";
	public static final String PRIVILEGE_INSURER = "Billing RHIP Verification - Insurer";

	/**
	 * The user property with the insurance an insurer's verifier works for.  A verifier only sees and verifies the
	 * bills of that insurance.
	 */
	public static final String USER_PROPERTY_INSURANCE_ID = "rwandaemr.verifierInsuranceId";

	/**
	 * The global property with the insurances whose bills are verified: insurance ids and categories such as
	 * "category:MUTUELLE".  Only the MUTUELLE insurances when blank.
	 */
	public static final String GLOBAL_PROPERTY_INSURANCES = "rwandaemr.billVerification.insurances";

	static final int MAX_REASON_LENGTH = 255;
	static final int MAX_NOTE_LENGTH = 1000;

	private final Log log = LogFactory.getLog(getClass());

	private final BillVerificationRepository repository;

	public BillVerificationService(@Autowired BillVerificationRepository repository) {
		this.repository = repository;
	}

	/**
	 * @return the step the bill is at
	 */
	@Transactional(readOnly = true)
	public BillVerificationStage getStage(Integer globalBillId) {
		return getStages(Collections.singletonList(globalBillId)).get(globalBillId);
	}

	/**
	 * @return the step each of the given bills is at, by global bill id
	 */
	@Transactional(readOnly = true)
	public Map<Integer, BillVerificationStage> getStages(Collection<Integer> globalBillIds) {
		Map<Integer, BillVerificationStage> ret = new HashMap<Integer, BillVerificationStage>();
		Map<Integer, BillVerificationStage> saved = repository.getStages(globalBillIds);
		for (Integer globalBillId : globalBillIds) {
			BillVerificationStage stage = saved.get(globalBillId);
			ret.put(globalBillId, stage == null ? BillVerificationStage.FACILITY_REVIEW : stage);
		}
		return ret;
	}

	/**
	 * @return the latest decision on each item of the given bills that has one, by patient service bill id.  Items
	 * missing from the result are pending.
	 */
	@Transactional(readOnly = true)
	public Map<Integer, BillItemVerification> getItems(Collection<Integer> globalBillIds) {
		return repository.getItems(globalBillIds);
	}

	/**
	 * @return the number of entries in the audit trail of each of the given bills that has any
	 */
	@Transactional(readOnly = true)
	public Map<Integer, Integer> getEventCounts(Collection<Integer> globalBillIds) {
		return repository.getEventCounts(globalBillIds);
	}

	/**
	 * @return the audit trail of the bill, oldest first
	 */
	@Transactional(readOnly = true)
	public List<VerificationEvent> getEvents(Integer globalBillId) {
		return repository.getEvents(globalBillId);
	}

	/**
	 * @return the reasons of the given type given before, to choose from
	 */
	@Transactional(readOnly = true)
	public List<VerificationReason> getReasons(VerificationReason.Type type) {
		return repository.getReasons(type);
	}

	/**
	 * @return the insurances the current user verifies bills for, from the {@link #USER_PROPERTY_INSURANCE_ID} user
	 * property: one insurance id, or several separated by commas.  Empty if the user is not limited to some insurances.
	 * A value that isn't an insurance id is ignored, and a property with no valid id limits the user to no insurance,
	 * so a mistake hides bills rather than showing all of them.
	 */
	public List<Integer> getVerifierInsuranceIds() {
		User user = getAuthenticatedUser();
		String value = user == null ? null : user.getUserProperty(USER_PROPERTY_INSURANCE_ID);
		if (StringUtils.isBlank(value)) {
			return Collections.emptyList();
		}
		List<Integer> ret = new ArrayList<Integer>();
		for (String part : value.split("[,;\\s]+")) {
			if (StringUtils.isBlank(part)) {
				continue;
			}
			try {
				Integer insuranceId = Integer.valueOf(part.trim());
				if (!ret.contains(insuranceId)) {
					ret.add(insuranceId);
				}
			}
			catch (NumberFormatException e) {
				log.warn("User property " + USER_PROPERTY_INSURANCE_ID + " of " + user.getUsername()
						+ " has a value that is not an insurance id, which is ignored: " + part);
			}
		}
		if (ret.isEmpty()) {
			ret.add(-1);
		}
		return ret;
	}

	/**
	 * @return true if the current user only sees the bills of some insurances: they are linked to insurances, or they
	 * are an insurer's verifier, who must be
	 */
	public boolean isLimitedToInsurances() {
		return !getVerifierInsuranceIds().isEmpty() || isInsurerOnly();
	}

	/**
	 * @return true if the current user is an insurer's verifier and nothing more, so must only see the bills of the
	 * insurance they work for
	 */
	public boolean isInsurerOnly() {
		return hasPrivilege(PRIVILEGE_INSURER) && !hasPrivilege(PRIVILEGE_FACILITY);
	}

	/**
	 * @return true if the current user can see the bills of the given insurance: a verifier linked to an insurance
	 * only sees the bills of that insurance, and an insurer's verifier linked to none sees no bills
	 */
	public boolean canSeeBillsOf(Integer insuranceId) {
		List<Integer> verifierInsuranceIds = getVerifierInsuranceIds();
		if (!verifierInsuranceIds.isEmpty()) {
			return verifierInsuranceIds.contains(insuranceId);
		}
		return !isInsurerOnly();
	}

	/**
	 * @return the insurances whose bills are verified
	 */
	public VerificationScope getScope() {
		return new VerificationScope(getScopeSetting());
	}

	/**
	 * @return true if the bills of the insurance with the given id and category are verified
	 */
	public boolean appliesTo(Integer insuranceId, String insuranceCategory) {
		return getScope().includes(insuranceId, insuranceCategory);
	}

	/**
	 * @return true if the bill is of an insurance whose bills are verified
	 */
	@Transactional(readOnly = true)
	public boolean appliesToBill(Integer globalBillId) {
		return globalBillId != null && getVerifiedBills(Collections.singletonList(globalBillId)).contains(globalBillId);
	}

	/**
	 * @return those of the given bills that are of an insurance whose bills are verified
	 */
	@Transactional(readOnly = true)
	public List<Integer> getVerifiedBills(Collection<Integer> globalBillIds) {
		List<Integer> ret = new ArrayList<Integer>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		VerificationScope scope = getScope();
		for (Map.Entry<Integer, Object[]> entry : repository.getInsurances(globalBillIds).entrySet()) {
			if (scope.includes((Integer) entry.getValue()[0], (String) entry.getValue()[1])) {
				ret.add(entry.getKey());
			}
		}
		Collections.sort(ret);
		return ret;
	}

	/**
	 * @return true if the current user can act on the items of the bill now
	 */
	@Transactional(readOnly = true)
	public boolean canAct(Integer globalBillId) {
		return canActAt(getStage(globalBillId), repository.getInsuranceId(globalBillId));
	}

	/**
	 * @return true if the current user can act on the items of a bill of the given insurance at the given step
	 */
	public boolean canActAt(BillVerificationStage stage, Integer insuranceId) {
		if (stage.getActor() == BillVerificationStage.Actor.FACILITY) {
			return hasPrivilege(PRIVILEGE_FACILITY);
		}
		if (stage.getActor() == BillVerificationStage.Actor.INSURER) {
			return hasPrivilege(PRIVILEGE_INSURER) && canSeeBillsOf(insuranceId);
		}
		return false;
	}

	/**
	 * Accepts or rejects an item at the step its bill is at.  When the facility responds to the insurer, accepting means
	 * accepting the rejection of the insurer, and rejecting means disputing it.  A decision can be changed until the
	 * step is submitted.
	 * @param reasonId the reason chosen, for a rejection or a dispute
	 * @param otherReason a reason typed instead of choosing one; it is saved, and offered from then on
	 * @param note anything else to say about the decision
	 * @return the decision
	 */
	@Transactional
	public BillItemVerification decide(Integer globalBillId, Integer patientServiceBillId, boolean accept, Integer reasonId,
									   String otherReason, String note) {
		BillVerificationStage stage = getStage(globalBillId);
		checkCanAct(globalBillId, stage);
		if (!repository.getActiveItemIds(globalBillId).contains(patientServiceBillId)) {
			throw new APIException("The item is not on this bill, or it was voided.");
		}
		BillItemVerification item = repository.getItems(Collections.singletonList(globalBillId)).get(patientServiceBillId);
		if (item == null) {
			item = new BillItemVerification();
			item.setPatientServiceBillId(patientServiceBillId);
			item.setGlobalBillId(globalBillId);
		}
		if (!stage.isReviewable(item.getStatus())) {
			throw new APIException("This item is " + item.getStatus().getLabel().toLowerCase()
					+ ", so it can't be decided during the " + stage.getLabel().toLowerCase() + ".");
		}
		BillItemVerificationStatus status = accept ? stage.getAcceptStatus() : stage.getRejectStatus();
		VerificationReason reason = status.isReasonRequired() ? getOrCreateReason(status.getReasonType(), reasonId, otherReason) : null;
		save(item, status, reason, note);
		return item;
	}

	/**
	 * Accepts or rejects, with the same reason, every item that still needs a decision at the step the bill is at.
	 * Items already decided at this step keep their decision.
	 * @return the number of items decided
	 */
	@Transactional
	public int decideAll(Integer globalBillId, boolean accept, Integer reasonId, String otherReason, String note) {
		BillVerificationStage stage = getStage(globalBillId);
		checkCanAct(globalBillId, stage);
		Map<Integer, BillItemVerification> items = repository.getItems(Collections.singletonList(globalBillId));
		List<Integer> awaiting = new java.util.ArrayList<Integer>();
		for (Integer itemId : repository.getActiveItemIds(globalBillId)) {
			if (stage.isAwaitingDecision(getStatus(items, itemId))) {
				awaiting.add(itemId);
			}
		}
		if (awaiting.isEmpty()) {
			throw new APIException("Every item already has a decision at the " + stage.getLabel().toLowerCase() + ".");
		}
		BillItemVerificationStatus status = accept ? stage.getAcceptStatus() : stage.getRejectStatus();
		VerificationReason reason = status.isReasonRequired() ? getOrCreateReason(status.getReasonType(), reasonId, otherReason) : null;
		for (Integer itemId : awaiting) {
			BillItemVerification item = items.get(itemId);
			if (item == null) {
				item = new BillItemVerification();
				item.setPatientServiceBillId(itemId);
				item.setGlobalBillId(globalBillId);
			}
			save(item, status, reason, note);
		}
		return awaiting.size();
	}

	private void save(BillItemVerification item, BillItemVerificationStatus status, VerificationReason reason, String note) {
		item.setStatus(status);
		item.setReasonId(reason == null ? null : reason.getReasonId());
		item.setReasonName(reason == null ? null : reason.getName());
		item.setNote(limit(StringUtils.trimToNull(note), MAX_NOTE_LENGTH, "The note"));
		repository.saveItem(item);
	}

	/**
	 * Ends the step the bill is at, once every item to decide at it has a decision, and moves the bill to the next
	 * step it needs
	 * @return the step the bill moved to
	 */
	@Transactional
	public BillVerificationStage submit(Integer globalBillId) {
		BillVerificationStage stage = getStage(globalBillId);
		checkCanAct(globalBillId, stage);
		List<Integer> itemIds = repository.getActiveItemIds(globalBillId);
		if (itemIds.isEmpty()) {
			throw new APIException("This bill has no items to verify.");
		}
		Map<Integer, BillItemVerification> items = repository.getItems(Collections.singletonList(globalBillId));
		int awaiting = 0;
		boolean anyNeedsNextStep = false;
		for (Integer itemId : itemIds) {
			BillItemVerificationStatus status = getStatus(items, itemId);
			if (stage.isAwaitingDecision(status)) {
				awaiting++;
			}
			if (needsNextStep(stage, status)) {
				anyNeedsNextStep = true;
			}
		}
		if (awaiting > 0) {
			throw new APIException(awaiting + (awaiting == 1 ? " item still needs" : " items still need")
					+ " a decision before the " + stage.getLabel().toLowerCase() + " can be submitted.");
		}
		BillVerificationStage next = anyNeedsNextStep ? nextStage(stage) : BillVerificationStage.CLOSED;
		repository.saveStage(globalBillId, next);
		log.info("Verification of global bill " + globalBillId + " moved from " + stage + " to " + next);
		return next;
	}

	/**
	 * @return how many of the given bills at the facility review are ready to send to the insurer, and how many are not
	 */
	@Transactional(readOnly = true)
	public BulkSendResult getBulkSendSummary(Collection<Integer> globalBillIds) {
		BulkSendResult ret = new BulkSendResult();
		for (Integer globalBillId : getReadyToSend(globalBillIds, ret)) {
			ret.addReady();
		}
		return ret;
	}

	/**
	 * Sends to the insurer every one of the given bills that is at the facility review and whose every item the
	 * facility has decided.  The other bills stay where they are.
	 * @return how many bills were sent, closed, or left at the facility review
	 */
	@Transactional
	public BulkSendResult sendAllToInsurer(Collection<Integer> globalBillIds) {
		if (!hasPrivilege(PRIVILEGE_FACILITY)) {
			throw new APIAuthenticationException("Only facility staff can send bills to the insurer.");
		}
		BulkSendResult ret = new BulkSendResult();
		for (Integer globalBillId : getReadyToSend(globalBillIds, ret)) {
			ret.addReady();
			if (submit(globalBillId) == BillVerificationStage.CLOSED) {
				ret.addClosed();
			}
			else {
				ret.addSentToInsurer();
			}
		}
		log.info("Sent " + ret.getSentToInsurer() + " bills to the insurer and closed " + ret.getClosed()
				+ "; " + ret.getNotReady() + " still have items to decide");
		return ret;
	}

	/**
	 * @return the given bills at the facility review whose every item the facility has decided.  Counts the bills at
	 * the facility review that are not ready in the given result.
	 */
	private List<Integer> getReadyToSend(Collection<Integer> globalBillIds, BulkSendResult result) {
		List<Integer> ret = new java.util.ArrayList<Integer>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		List<Integer> atFacilityReview = new java.util.ArrayList<Integer>();
		for (Map.Entry<Integer, BillVerificationStage> entry : getStages(getVerifiedBills(globalBillIds)).entrySet()) {
			if (entry.getValue() == BillVerificationStage.FACILITY_REVIEW) {
				atFacilityReview.add(entry.getKey());
			}
		}
		Map<Integer, int[]> counts = repository.getFacilityReviewItemCounts(atFacilityReview);
		for (Integer globalBillId : atFacilityReview) {
			int[] count = counts.get(globalBillId);
			if (count == null || count[0] == 0) {
				result.addNoItems();
			}
			else if (count[1] > 0) {
				result.addNotReady();
			}
			else {
				ret.add(globalBillId);
			}
		}
		Collections.sort(ret);
		return ret;
	}

	/**
	 * @return how many of the given bills waiting for the insurer, at the insurer review or the final decision, have
	 * every item decided, so the insurer can submit them together
	 */
	@Transactional(readOnly = true)
	public BulkSendResult getInsurerBulkSummary(Collection<Integer> globalBillIds) {
		BulkSendResult ret = new BulkSendResult();
		for (Integer globalBillId : getReadyForInsurer(globalBillIds, ret)) {
			ret.addReady();
		}
		return ret;
	}

	/**
	 * Submits the review of every one of the given bills that is waiting for the insurer, at the insurer review or the
	 * final decision, and whose every item the insurer has decided.  A bill with rejected items goes back to the
	 * facility, the others are closed.  The other bills stay where they are.
	 * @return how many bills went back to the facility, were closed, or still have items to decide
	 */
	@Transactional
	public BulkSendResult submitAllInsurerReviews(Collection<Integer> globalBillIds) {
		if (!hasPrivilege(PRIVILEGE_INSURER)) {
			throw new APIAuthenticationException("Only the insurer's verifier can submit the reviews of the insurer.");
		}
		BulkSendResult ret = new BulkSendResult();
		for (Integer globalBillId : getReadyForInsurer(globalBillIds, ret)) {
			ret.addReady();
			try {
				if (submit(globalBillId) == BillVerificationStage.CLOSED) {
					ret.addClosed();
				}
				else {
					ret.addSentToFacility();
				}
			}
			catch (APIException e) {
				// For example a bill of an insurance the user does not verify for: it stays where it is
				log.debug("Review of global bill " + globalBillId + " not submitted: " + e.getMessage());
				ret.addRefused();
			}
		}
		log.info("Submitted the insurer review of " + (ret.getClosed() + ret.getSentToFacility()) + " bills: "
				+ ret.getClosed() + " closed, " + ret.getSentToFacility() + " sent back to the facility; "
				+ ret.getNotReady() + " still have items to decide, " + ret.getRefused() + " refused");
		return ret;
	}

	/**
	 * @return the given bills waiting for the insurer whose every item to decide at their step has a decision.  Counts
	 * the bills waiting for the insurer that are not ready in the given result.
	 */
	private List<Integer> getReadyForInsurer(Collection<Integer> globalBillIds, BulkSendResult result) {
		List<Integer> ret = new java.util.ArrayList<Integer>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		Map<Integer, BillVerificationStage> waiting = new HashMap<Integer, BillVerificationStage>();
		for (Map.Entry<Integer, BillVerificationStage> entry : getStages(getVerifiedBills(globalBillIds)).entrySet()) {
			if (entry.getValue().getActor() == BillVerificationStage.Actor.INSURER) {
				waiting.put(entry.getKey(), entry.getValue());
			}
		}
		Map<Integer, Map<BillItemVerificationStatus, Integer>> counts = repository.getItemStatusCounts(waiting.keySet());
		for (Map.Entry<Integer, BillVerificationStage> entry : waiting.entrySet()) {
			Map<BillItemVerificationStatus, Integer> statusCounts = counts.get(entry.getKey());
			if (statusCounts == null || statusCounts.isEmpty()) {
				result.addNoItems();
				continue;
			}
			int awaiting = 0;
			for (Map.Entry<BillItemVerificationStatus, Integer> count : statusCounts.entrySet()) {
				if (entry.getValue().isAwaitingDecision(count.getKey())) {
					awaiting += count.getValue();
				}
			}
			if (awaiting > 0) {
				result.addNotReady();
			}
			else {
				ret.add(entry.getKey());
			}
		}
		Collections.sort(ret);
		return ret;
	}

	/**
	 * @return true if an item with the given status at the end of the given step needs the step after it
	 */
	private boolean needsNextStep(BillVerificationStage stage, BillItemVerificationStatus status) {
		switch (stage) {
			case FACILITY_REVIEW:
				return status == BillItemVerificationStatus.FACILITY_ACCEPTED;
			case INSURER_REVIEW:
				return status == BillItemVerificationStatus.INSURER_REJECTED;
			case FACILITY_RESPONSE:
				return status == BillItemVerificationStatus.DISPUTED;
			default:
				return false;
		}
	}

	private BillVerificationStage nextStage(BillVerificationStage stage) {
		switch (stage) {
			case FACILITY_REVIEW:
				return BillVerificationStage.INSURER_REVIEW;
			case INSURER_REVIEW:
				return BillVerificationStage.FACILITY_RESPONSE;
			case FACILITY_RESPONSE:
				return BillVerificationStage.FINAL_REVIEW;
			default:
				return BillVerificationStage.CLOSED;
		}
	}

	private BillItemVerificationStatus getStatus(Map<Integer, BillItemVerification> items, Integer itemId) {
		BillItemVerification item = items.get(itemId);
		return item == null ? BillItemVerificationStatus.PENDING : item.getStatus();
	}

	/**
	 * @return the reason chosen, or the reason typed: an existing reason with the same name, or a new one
	 */
	private VerificationReason getOrCreateReason(VerificationReason.Type type, Integer reasonId, String otherReason) {
		String typed = StringUtils.trimToNull(otherReason);
		if (typed != null) {
			typed = limit(typed.replaceAll("\\s+", " "), MAX_REASON_LENGTH, "The reason");
			VerificationReason reason = repository.getReasonByName(type, typed);
			if (reason == null) {
				reason = new VerificationReason(null, type, typed, false);
				repository.saveReason(reason);
			}
			return reason;
		}
		if (reasonId == null) {
			throw new APIException(type == VerificationReason.Type.DISPUTE
					? "Choose or type the reason for disputing the rejection." : "Choose or type the reason for rejecting the item.");
		}
		VerificationReason reason = repository.getReason(reasonId);
		if (reason == null || reason.isRetired() || reason.getType() != type) {
			throw new APIException("The reason chosen is not available. Choose another one or type it.");
		}
		return reason;
	}

	/**
	 * Checks that the current user can act at the given step of the bill: that the step is not over, that the user
	 * acts at it, and that an insurer's verifier works for the insurance of the bill
	 */
	private void checkCanAct(Integer globalBillId, BillVerificationStage stage) {
		if (!appliesToBill(globalBillId)) {
			throw new APIException("The bills of this insurance are not verified.");
		}
		if (stage.getActor() == null) {
			throw new APIException("The verification of this bill is closed.");
		}
		if (stage.getActor() == BillVerificationStage.Actor.FACILITY) {
			if (!hasPrivilege(PRIVILEGE_FACILITY)) {
				throw new APIAuthenticationException("Only facility staff can act during the " + stage.getLabel().toLowerCase() + ".");
			}
			return;
		}
		if (!hasPrivilege(PRIVILEGE_INSURER)) {
			throw new APIAuthenticationException("Only the insurer's verifier can act during the " + stage.getLabel().toLowerCase() + ".");
		}
		List<Integer> verifierInsuranceIds = getVerifierInsuranceIds();
		if (verifierInsuranceIds.isEmpty() && isInsurerOnly()) {
			throw new APIAuthenticationException("Your account is not linked to an insurance. Ask an administrator to set "
					+ USER_PROPERTY_INSURANCE_ID + " on your account.");
		}
		if (!verifierInsuranceIds.isEmpty() && !verifierInsuranceIds.contains(repository.getInsuranceId(globalBillId))) {
			throw new APIAuthenticationException("This bill is for an insurance you don't verify for.");
		}
	}

	private String limit(String value, int maxLength, String what) {
		if (value != null && value.length() > maxLength) {
			throw new APIException(what + " is too long: use at most " + maxLength + " characters.");
		}
		return value;
	}

	protected String getScopeSetting() {
		return Context.getAdministrationService().getGlobalProperty(GLOBAL_PROPERTY_INSURANCES);
	}

	protected boolean hasPrivilege(String privilege) {
		return Context.hasPrivilege(privilege);
	}

	protected User getAuthenticatedUser() {
		User user = Context.getAuthenticatedUser();
		if (user == null || user.getUserId() == null) {
			return user;
		}
		// The user kept in the session is the one that logged in: read it again, so a change an administrator makes to
		// the insurances of a verifier applies without them logging out
		try {
			Context.addProxyPrivilege(PrivilegeConstants.GET_USERS);
			User stored = Context.getUserService().getUser(user.getUserId());
			return stored == null ? user : stored;
		}
		catch (RuntimeException e) {
			log.debug("Unable to read the current user again", e);
			return user;
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.GET_USERS);
		}
	}

	/**
	 * @return the start of the day of the given date, or null.  A bill covers whole days: what happened before the
	 * admission was opened that day, like registration, vitals and consultation, is part of the patient's journey.
	 */
	public static Date startOfDay(Date date) {
		return date == null ? null : DateUtils.truncate(date, Calendar.DATE);
	}

	/**
	 * @return the last millisecond of the day of the given date, or null
	 */
	public static Date endOfDay(Date date) {
		return date == null ? null : new Date(DateUtils.ceiling(date, Calendar.DATE).getTime() - 1);
	}
}
