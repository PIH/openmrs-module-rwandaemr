package org.openmrs.module.rwandaemr.billing.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BillVerificationServiceTest {

	private static final Integer BILL = 100;
	private static final Integer RSSB = 1;
	private static final Integer MMI = 2;
	private static final Integer CONSULTATION = 1001;
	private static final Integer BLOOD_CULTURE = 1002;
	private static final Integer XRAY = 1003;

	private InMemoryRepository repository;
	private Set<String> privileges;
	private User user;
	private BillVerificationService service;
	private String scopeSetting = RSSB + "," + MMI;

	@BeforeEach
	public void setUp() {
		repository = new InMemoryRepository();
		repository.activeItemIds.put(BILL, new ArrayList<Integer>(Arrays.asList(CONSULTATION, BLOOD_CULTURE, XRAY)));
		repository.insuranceIds.put(BILL, RSSB);
		privileges = new HashSet<String>();
		user = new User(7);
		service = new BillVerificationService(repository) {
			@Override
			protected boolean hasPrivilege(String privilege) {
				return privileges.contains(privilege);
			}

			@Override
			protected User getAuthenticatedUser() {
				return user;
			}

			@Override
			protected String getScopeSetting() {
				return scopeSetting;
			}
		};
	}

	@Test
	public void shouldNotVerifyTheBillsOfOtherInsurances() {
		Integer otherBill = 300;
		repository.activeItemIds.put(otherBill, new ArrayList<Integer>(Arrays.asList(3001)));
		repository.insuranceIds.put(otherBill, 3);
		actAsFacility();

		assertThat(service.appliesToBill(BILL), is(true));
		assertThat(service.appliesToBill(otherBill), is(false));
		assertThrows(APIException.class, () -> service.decide(otherBill, 3001, true, null, null, null));
		assertThrows(APIException.class, () -> service.submit(otherBill));

		service.decideAll(BILL, true, null, null, null);
		BulkSendResult summary = service.getBulkSendSummary(Arrays.asList(BILL, otherBill));
		assertThat(summary.getReady(), equalTo(1));
		assertThat(summary.getNotReady(), equalTo(0));
		assertThat(summary.getNoItems(), equalTo(0));
	}

	@Test
	public void shouldVerifyTheBillsOfAnInsuranceCategory() {
		Integer mutuelleBill = 400;
		repository.insuranceIds.put(mutuelleBill, 40);
		repository.categories.put(40, " Mutuelle ");
		scopeSetting = "category:MUTUELLE";

		assertThat(service.appliesToBill(mutuelleBill), is(true));
		assertThat(service.appliesToBill(BILL), is(false));
		assertThat(service.getVerifiedBills(Arrays.asList(BILL, mutuelleBill)), contains(mutuelleBill));
	}

	@Test
	public void shouldStartAtTheFacilityReview() {
		assertThat(service.getStage(BILL), equalTo(BillVerificationStage.FACILITY_REVIEW));
	}

	@Test
	public void shouldLetTheFacilityAcceptAndRejectItemsWithAReason() {
		actAsFacility();
		VerificationReason notPerformed = repository.addReason(VerificationReason.Type.REJECTION, "Test not performed");

		service.decide(BILL, CONSULTATION, true, null, null, null);
		BillItemVerification rejected = service.decide(BILL, BLOOD_CULTURE, false, notPerformed.getReasonId(), null, " No specimen ");

		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.FACILITY_ACCEPTED));
		assertThat(rejected.getStatus(), equalTo(BillItemVerificationStatus.FACILITY_REJECTED));
		assertThat(rejected.getReasonName(), equalTo("Test not performed"));
		assertThat(rejected.getNote(), equalTo("No specimen"));
		assertThat(repository.events, hasSize(2));
	}

	@Test
	public void shouldRequireAReasonToReject() {
		actAsFacility();

		APIException e = assertThrows(APIException.class, () -> service.decide(BILL, XRAY, false, null, "  ", null));

		assertThat(e.getMessage(), containsString("reason"));
		assertThat(statusOf(XRAY), equalTo(BillItemVerificationStatus.PENDING));
	}

	@Test
	public void shouldSaveAReasonTypedAsOtherAndReuseIt() {
		actAsFacility();

		BillItemVerification first = service.decide(BILL, XRAY, false, null, "No  report in PACS", null);
		BillItemVerification second = service.decide(BILL, BLOOD_CULTURE, false, null, "no report in pacs", null);

		assertThat(service.getReasons(VerificationReason.Type.REJECTION), hasSize(1));
		assertThat(first.getReasonName(), equalTo("No report in PACS"));
		assertThat(second.getReasonId(), equalTo(first.getReasonId()));
		assertThat(service.getReasons(VerificationReason.Type.DISPUTE), hasSize(0));
	}

	@Test
	public void shouldNotAcceptAReasonOfTheWrongType() {
		actAsFacility();
		VerificationReason disputeReason = repository.addReason(VerificationReason.Type.DISPUTE, "Result in paper register");

		assertThrows(APIException.class, () -> service.decide(BILL, XRAY, false, disputeReason.getReasonId(), null, null));
	}

	@Test
	public void shouldNotDecideItemsOfAnotherBill() {
		actAsFacility();

		assertThrows(APIException.class, () -> service.decide(BILL, 9999, true, null, null, null));
	}

	@Test
	public void shouldNotLetTheInsurerActBeforeTheFacilitySendsTheBill() {
		actAsInsurer(RSSB);

		assertThrows(APIAuthenticationException.class, () -> service.decide(BILL, CONSULTATION, true, null, null, null));
		assertFalse(service.canAct(BILL));
	}

	@Test
	public void shouldNotSubmitTheFacilityReviewUntilEveryItemIsDecided() {
		actAsFacility();
		service.decide(BILL, CONSULTATION, true, null, null, null);

		APIException e = assertThrows(APIException.class, () -> service.submit(BILL));

		assertThat(e.getMessage(), containsString("2 items still need a decision"));
		assertThat(service.getStage(BILL), equalTo(BillVerificationStage.FACILITY_REVIEW));
	}

	@Test
	public void shouldSendTheItemsTheFacilityAcceptedToTheInsurer() {
		facilityReview();

		assertThat(service.getStage(BILL), equalTo(BillVerificationStage.INSURER_REVIEW));
		assertThat(repository.events.get(repository.events.size() - 1).stage, equalTo(BillVerificationStage.INSURER_REVIEW));
	}

	@Test
	public void shouldCloseTheBillWhenTheFacilityRejectsEveryItem() {
		actAsFacility();
		for (Integer item : Arrays.asList(CONSULTATION, BLOOD_CULTURE, XRAY)) {
			service.decide(BILL, item, false, null, "Billed by mistake", null);
		}

		assertThat(service.submit(BILL), equalTo(BillVerificationStage.CLOSED));
	}

	@Test
	public void shouldNotLetTheInsurerDecideItemsTheFacilityRejected() {
		facilityReview();
		actAsInsurer(RSSB);

		assertThrows(APIException.class, () -> service.decide(BILL, XRAY, true, null, null, null));
	}

	@Test
	public void shouldOnlyLetTheInsurerVerifyBillsOfItsInsurance() {
		facilityReview();
		actAsInsurer(MMI);

		assertThrows(APIAuthenticationException.class, () -> service.decide(BILL, CONSULTATION, true, null, null, null));
	}

	@Test
	public void shouldLetAVerifierOfSeveralInsurancesVerifyTheBillsOfEachOfThem() {
		facilityReview();
		actAsInsurer(MMI + ", " + RSSB);

		assertThat(service.getVerifierInsuranceIds(), contains(MMI, RSSB));
		assertThat(service.canSeeBillsOf(RSSB), is(true));
		assertThat(service.canSeeBillsOf(MMI), is(true));
		assertThat(service.canSeeBillsOf(999), is(false));
		service.decide(BILL, CONSULTATION, true, null, null, null);
		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.INSURER_ACCEPTED));
	}

	@Test
	public void shouldIgnoreAnInsuranceIdThatIsNotANumber() {
		actAsInsurer(RSSB + ",rssb");

		assertThat(service.getVerifierInsuranceIds(), contains(RSSB));
	}

	@Test
	public void shouldShowNoBillsWhenNoInsuranceIdIsValid() {
		actAsInsurer("RSSB");

		assertThat(service.isLimitedToInsurances(), is(true));
		assertThat(service.canSeeBillsOf(RSSB), is(false));
	}

	@Test
	public void shouldLimitAFacilityUserLinkedToAnInsurance() {
		actAsFacility();
		user.setUserProperty(BillVerificationService.USER_PROPERTY_INSURANCE_ID, MMI.toString());

		assertThat(service.isLimitedToInsurances(), is(true));
		assertThat(service.canSeeBillsOf(RSSB), is(false));
	}

	@Test
	public void shouldNotLetAnInsurerVerifierWithoutAnInsuranceAct() {
		facilityReview();
		actAsInsurer((Integer) null);

		APIAuthenticationException e = assertThrows(APIAuthenticationException.class,
				() -> service.decide(BILL, CONSULTATION, true, null, null, null));
		assertThat(e.getMessage(), containsString(BillVerificationService.USER_PROPERTY_INSURANCE_ID));
	}

	@Test
	public void shouldCloseTheBillWhenTheInsurerAcceptsEverything() {
		facilityReview();
		actAsInsurer(RSSB);
		service.decide(BILL, CONSULTATION, true, null, null, null);
		service.decide(BILL, BLOOD_CULTURE, true, null, null, null);

		assertThat(service.submit(BILL), equalTo(BillVerificationStage.CLOSED));
		assertThrows(APIException.class, () -> service.decide(BILL, CONSULTATION, false, null, "Changed my mind", null));
	}

	@Test
	public void shouldLetTheFacilityAcceptOrDisputeTheRejectionsOfTheInsurer() {
		insurerRejectsBothItems();
		actAsFacility();

		assertThrows(APIException.class, () -> service.decide(BILL, CONSULTATION, false, null, null, null));
		service.decide(BILL, CONSULTATION, true, null, null, null);
		BillItemVerification disputed = service.decide(BILL, BLOOD_CULTURE, false, null, "Result is in the paper register", null);

		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.REJECTION_ACCEPTED));
		assertThat(disputed.getStatus(), equalTo(BillItemVerificationStatus.DISPUTED));
		assertThat(service.getReasons(VerificationReason.Type.DISPUTE), hasSize(1));
		assertThat(service.submit(BILL), equalTo(BillVerificationStage.FINAL_REVIEW));
	}

	@Test
	public void shouldCloseTheBillWhenTheFacilityAcceptsEveryRejection() {
		insurerRejectsBothItems();
		actAsFacility();
		service.decide(BILL, CONSULTATION, true, null, null, null);
		service.decide(BILL, BLOOD_CULTURE, true, null, null, null);

		assertThat(service.submit(BILL), equalTo(BillVerificationStage.CLOSED));
		assertThat(statusOf(BLOOD_CULTURE).getOutcome(), equalTo(BillItemVerificationStatus.Outcome.REJECTED));
	}

	@Test
	public void shouldLetTheInsurerAcceptOrRejectDisputedItemsForGood() {
		insurerRejectsBothItems();
		actAsFacility();
		service.decide(BILL, CONSULTATION, false, null, "Consultation note signed", null);
		service.decide(BILL, BLOOD_CULTURE, false, null, "Result is in the paper register", null);
		service.submit(BILL);

		actAsInsurer(RSSB);
		service.decide(BILL, CONSULTATION, true, null, null, "Agreed after call with billing");
		BillItemVerification upheld = service.decide(BILL, BLOOD_CULTURE, false, null, "Test not performed", null);

		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.FINAL_ACCEPTED));
		assertThat(statusOf(CONSULTATION).getOutcome(), equalTo(BillItemVerificationStatus.Outcome.ACCEPTED));
		assertThat(upheld.getStatus(), equalTo(BillItemVerificationStatus.FINAL_REJECTED));
		assertThat(service.submit(BILL), equalTo(BillVerificationStage.CLOSED));
	}

	@Test
	public void shouldClearTheReasonWhenARejectionIsChangedToAnAcceptance() {
		actAsFacility();
		service.decide(BILL, XRAY, false, null, "Duplicate charge", null);

		BillItemVerification accepted = service.decide(BILL, XRAY, true, null, null, null);

		assertThat(accepted.getReasonId(), nullValue());
		assertThat(accepted.getReasonName(), nullValue());
	}

	@Test
	public void shouldNotAcceptTooLongAReason() {
		actAsFacility();
		StringBuilder reason = new StringBuilder();
		for (int i = 0; i <= BillVerificationService.MAX_REASON_LENGTH; i++) {
			reason.append('x');
		}

		assertThrows(APIException.class, () -> service.decide(BILL, XRAY, false, null, reason.toString(), null));
		assertTrue(service.getReasons(VerificationReason.Type.REJECTION).isEmpty());
	}

	@Test
	public void shouldAcceptAllItemsStillToDecideAndKeepTheOthers() {
		actAsFacility();
		service.decide(BILL, XRAY, false, null, "No order in EMR", null);

		int decided = service.decideAll(BILL, true, null, null, null);

		assertThat(decided, equalTo(2));
		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.FACILITY_ACCEPTED));
		assertThat(statusOf(BLOOD_CULTURE), equalTo(BillItemVerificationStatus.FACILITY_ACCEPTED));
		assertThat(statusOf(XRAY), equalTo(BillItemVerificationStatus.FACILITY_REJECTED));
	}

	@Test
	public void shouldRejectAllItemsStillToDecideWithOneReason() {
		facilityReview();
		actAsInsurer(RSSB);

		assertThat(service.decideAll(BILL, false, null, "Not covered by policy", null), equalTo(2));

		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.INSURER_REJECTED));
		assertThat(statusOf(BLOOD_CULTURE), equalTo(BillItemVerificationStatus.INSURER_REJECTED));
		assertThat(repository.items.get(CONSULTATION).getReasonId(), equalTo(repository.items.get(BLOOD_CULTURE).getReasonId()));
		assertThat(service.getReasons(VerificationReason.Type.REJECTION), hasSize(2));
	}

	@Test
	public void shouldRequireAReasonToRejectAll() {
		actAsFacility();

		assertThrows(APIException.class, () -> service.decideAll(BILL, false, null, null, null));
		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.PENDING));
	}

	@Test
	public void shouldOnlyDisputeAllTheRejectionsOfTheInsurer() {
		insurerRejectsBothItems();
		actAsFacility();

		assertThat(service.decideAll(BILL, false, null, "Result is in the paper register", null), equalTo(2));
		assertThat(statusOf(CONSULTATION), equalTo(BillItemVerificationStatus.DISPUTED));
		assertThat(statusOf(XRAY), equalTo(BillItemVerificationStatus.FACILITY_REJECTED));
		assertThrows(APIException.class, () -> service.decideAll(BILL, true, null, null, null));
	}

	@Test
	public void shouldSendOnlyTheBillsWhoseEveryItemIsDecided() {
		Integer undecidedBill = 200;
		Integer allRejectedBill = 300;
		Integer emptyBill = 400;
		Integer billAtInsurer = 500;
		repository.activeItemIds.put(undecidedBill, new ArrayList<Integer>(Arrays.asList(2001, 2002)));
		repository.activeItemIds.put(allRejectedBill, new ArrayList<Integer>(Arrays.asList(3001)));
		repository.activeItemIds.put(billAtInsurer, new ArrayList<Integer>(Arrays.asList(5001)));
		repository.stages.put(billAtInsurer, BillVerificationStage.INSURER_REVIEW);
		for (Integer bill : Arrays.asList(undecidedBill, allRejectedBill, emptyBill, billAtInsurer)) {
			repository.insuranceIds.put(bill, RSSB);
		}
		actAsFacility();
		service.decideAll(BILL, true, null, null, null);
		service.decide(undecidedBill, 2001, true, null, null, null);
		service.decide(allRejectedBill, 3001, false, null, "Billed by mistake", null);
		List<Integer> period = Arrays.asList(BILL, undecidedBill, allRejectedBill, emptyBill, billAtInsurer);

		BulkSendResult summary = service.getBulkSendSummary(period);
		assertThat(summary.getReady(), equalTo(2));
		assertThat(summary.getNotReady(), equalTo(1));
		assertThat(summary.getNoItems(), equalTo(1));

		BulkSendResult result = service.sendAllToInsurer(period);

		assertThat(result.getSentToInsurer(), equalTo(1));
		assertThat(result.getClosed(), equalTo(1));
		assertThat(result.getNotReady(), equalTo(1));
		assertThat(service.getStage(BILL), equalTo(BillVerificationStage.INSURER_REVIEW));
		assertThat(service.getStage(allRejectedBill), equalTo(BillVerificationStage.CLOSED));
		assertThat(service.getStage(undecidedBill), equalTo(BillVerificationStage.FACILITY_REVIEW));
		assertThat(service.getStage(emptyBill), equalTo(BillVerificationStage.FACILITY_REVIEW));
		assertThat(service.getStage(billAtInsurer), equalTo(BillVerificationStage.INSURER_REVIEW));
	}

	@Test
	public void shouldNotLetTheInsurerSendBillsToItself() {
		actAsInsurer(RSSB);

		assertThrows(APIAuthenticationException.class, () -> service.sendAllToInsurer(Arrays.asList(BILL)));
	}

	@Test
	public void shouldSubmitOnlyTheInsurerReviewsWhoseEveryItemIsDecided() {
		Integer undecidedBill = 600;
		Integer rejectedBill = 700;
		Integer billAtFacility = 800;
		Integer otherInsuranceBill = 900;
		repository.activeItemIds.put(undecidedBill, new ArrayList<Integer>(Arrays.asList(6001)));
		repository.activeItemIds.put(rejectedBill, new ArrayList<Integer>(Arrays.asList(7001)));
		repository.activeItemIds.put(billAtFacility, new ArrayList<Integer>(Arrays.asList(8001)));
		repository.activeItemIds.put(otherInsuranceBill, new ArrayList<Integer>(Arrays.asList(9001)));
		for (Integer bill : Arrays.asList(undecidedBill, rejectedBill, billAtFacility)) {
			repository.insuranceIds.put(bill, RSSB);
		}
		repository.insuranceIds.put(otherInsuranceBill, MMI);
		facilityReview();
		service.decide(undecidedBill, 6001, true, null, null, null);
		service.submit(undecidedBill);
		service.decide(rejectedBill, 7001, true, null, null, null);
		service.submit(rejectedBill);
		BillItemVerification decidedByOtherInsurer = new BillItemVerification();
		decidedByOtherInsurer.setPatientServiceBillId(9001);
		decidedByOtherInsurer.setGlobalBillId(otherInsuranceBill);
		decidedByOtherInsurer.setStatus(BillItemVerificationStatus.INSURER_ACCEPTED);
		repository.saveItem(decidedByOtherInsurer);
		repository.stages.put(otherInsuranceBill, BillVerificationStage.INSURER_REVIEW);

		actAsInsurer(RSSB);
		service.decideAll(BILL, true, null, null, null);
		service.decide(rejectedBill, 7001, false, null, "Not clinically justified", null);
		List<Integer> period = Arrays.asList(BILL, undecidedBill, rejectedBill, billAtFacility, otherInsuranceBill);

		BulkSendResult summary = service.getInsurerBulkSummary(period);
		assertThat(summary.getReady(), equalTo(3));
		assertThat(summary.getNotReady(), equalTo(1));

		BulkSendResult result = service.submitAllInsurerReviews(period);

		assertThat(result.getClosed(), equalTo(1));
		assertThat(result.getSentToFacility(), equalTo(1));
		assertThat(result.getNotReady(), equalTo(1));
		assertThat(result.getRefused(), equalTo(1));
		assertThat(service.getStage(BILL), equalTo(BillVerificationStage.CLOSED));
		assertThat(service.getStage(rejectedBill), equalTo(BillVerificationStage.FACILITY_RESPONSE));
		assertThat(service.getStage(undecidedBill), equalTo(BillVerificationStage.INSURER_REVIEW));
		assertThat(service.getStage(billAtFacility), equalTo(BillVerificationStage.FACILITY_REVIEW));
		assertThat(service.getStage(otherInsuranceBill), equalTo(BillVerificationStage.INSURER_REVIEW));
	}

	@Test
	public void shouldNotLetTheFacilitySubmitTheInsurerReviews() {
		facilityReview();

		assertThrows(APIAuthenticationException.class, () -> service.submitAllInsurerReviews(Arrays.asList(BILL)));
	}

	/**
	 * The facility accepts the consultation and the blood culture, rejects the X-ray and sends the bill to the insurer
	 */
	private void facilityReview() {
		actAsFacility();
		service.decide(BILL, CONSULTATION, true, null, null, null);
		service.decide(BILL, BLOOD_CULTURE, true, null, null, null);
		service.decide(BILL, XRAY, false, null, "No order in EMR", null);
		service.submit(BILL);
	}

	private void insurerRejectsBothItems() {
		facilityReview();
		actAsInsurer(RSSB);
		service.decide(BILL, CONSULTATION, false, null, "Not clinically justified", null);
		service.decide(BILL, BLOOD_CULTURE, false, null, "Test not performed", null);
		assertThat(service.submit(BILL), equalTo(BillVerificationStage.FACILITY_RESPONSE));
	}

	private void actAsFacility() {
		privileges.clear();
		privileges.add(BillVerificationService.PRIVILEGE_FACILITY);
		user.setUserProperty(BillVerificationService.USER_PROPERTY_INSURANCE_ID, "");
	}

	private void actAsInsurer(Integer insuranceId) {
		actAsInsurer(insuranceId == null ? "" : insuranceId.toString());
	}

	private void actAsInsurer(String insuranceIds) {
		privileges.clear();
		privileges.add(BillVerificationService.PRIVILEGE_INSURER);
		user.setUserProperty(BillVerificationService.USER_PROPERTY_INSURANCE_ID, insuranceIds);
	}

	private BillItemVerificationStatus statusOf(Integer itemId) {
		BillItemVerification item = repository.items.get(itemId);
		return item == null ? BillItemVerificationStatus.PENDING : item.getStatus();
	}

	private static class Event {

		final Integer itemId;
		final String status;
		final BillVerificationStage stage;

		Event(Integer itemId, String status, BillVerificationStage stage) {
			this.itemId = itemId;
			this.status = status;
			this.stage = stage;
		}
	}

	private static class InMemoryRepository extends BillVerificationRepository {

		final Map<Integer, List<Integer>> activeItemIds = new HashMap<Integer, List<Integer>>();
		final Map<Integer, Integer> insuranceIds = new HashMap<Integer, Integer>();
		final Map<Integer, String> categories = new HashMap<Integer, String>();
		final Map<Integer, BillVerificationStage> stages = new HashMap<Integer, BillVerificationStage>();
		final Map<Integer, BillItemVerification> items = new LinkedHashMap<Integer, BillItemVerification>();
		final Map<Integer, VerificationReason> reasons = new LinkedHashMap<Integer, VerificationReason>();
		final List<Event> events = new ArrayList<Event>();

		InMemoryRepository() {
			super(null);
		}

		VerificationReason addReason(VerificationReason.Type type, String name) {
			VerificationReason reason = new VerificationReason(null, type, name, false);
			saveReason(reason);
			return reason;
		}

		@Override
		public Map<Integer, BillVerificationStage> getStages(Collection<Integer> globalBillIds) {
			Map<Integer, BillVerificationStage> ret = new HashMap<Integer, BillVerificationStage>();
			for (Integer id : globalBillIds) {
				if (stages.containsKey(id)) {
					ret.put(id, stages.get(id));
				}
			}
			return ret;
		}

		@Override
		public Map<Integer, BillItemVerification> getItems(Collection<Integer> globalBillIds) {
			Map<Integer, BillItemVerification> ret = new LinkedHashMap<Integer, BillItemVerification>();
			for (BillItemVerification item : items.values()) {
				if (globalBillIds.contains(item.getGlobalBillId())) {
					ret.put(item.getPatientServiceBillId(), copy(item));
				}
			}
			return ret;
		}

		@Override
		public List<Integer> getActiveItemIds(Integer globalBillId) {
			List<Integer> ret = activeItemIds.get(globalBillId);
			return ret == null ? new ArrayList<Integer>() : ret;
		}

		@Override
		public Map<Integer, int[]> getFacilityReviewItemCounts(Collection<Integer> globalBillIds) {
			Map<Integer, int[]> ret = new HashMap<Integer, int[]>();
			for (Integer globalBillId : globalBillIds) {
				List<Integer> itemIds = getActiveItemIds(globalBillId);
				if (itemIds.isEmpty()) {
					continue;
				}
				int undecided = 0;
				for (Integer itemId : itemIds) {
					BillItemVerification item = items.get(itemId);
					if (item == null || (item.getStatus() != BillItemVerificationStatus.FACILITY_ACCEPTED
							&& item.getStatus() != BillItemVerificationStatus.FACILITY_REJECTED)) {
						undecided++;
					}
				}
				ret.put(globalBillId, new int[] { itemIds.size(), undecided });
			}
			return ret;
		}

		@Override
		public Map<Integer, Map<BillItemVerificationStatus, Integer>> getItemStatusCounts(Collection<Integer> globalBillIds) {
			Map<Integer, Map<BillItemVerificationStatus, Integer>> ret = new HashMap<Integer, Map<BillItemVerificationStatus, Integer>>();
			for (Integer globalBillId : globalBillIds) {
				List<Integer> itemIds = getActiveItemIds(globalBillId);
				if (itemIds.isEmpty()) {
					continue;
				}
				Map<BillItemVerificationStatus, Integer> counts = new HashMap<BillItemVerificationStatus, Integer>();
				for (Integer itemId : itemIds) {
					BillItemVerification item = items.get(itemId);
					BillItemVerificationStatus status = item == null ? BillItemVerificationStatus.PENDING : item.getStatus();
					counts.put(status, counts.containsKey(status) ? counts.get(status) + 1 : 1);
				}
				ret.put(globalBillId, counts);
			}
			return ret;
		}

		@Override
		public Map<Integer, Object[]> getInsurances(Collection<Integer> globalBillIds) {
			Map<Integer, Object[]> ret = new HashMap<Integer, Object[]>();
			for (Integer globalBillId : globalBillIds) {
				Integer insuranceId = insuranceIds.get(globalBillId);
				if (insuranceId != null) {
					ret.put(globalBillId, new Object[] { insuranceId, categories.get(insuranceId) });
				}
			}
			return ret;
		}

		@Override
		public Integer getInsuranceId(Integer globalBillId) {
			return insuranceIds.get(globalBillId);
		}

		@Override
		public List<VerificationReason> getReasons(VerificationReason.Type type) {
			List<VerificationReason> ret = new ArrayList<VerificationReason>();
			for (VerificationReason reason : reasons.values()) {
				if (reason.getType() == type && !reason.isRetired()) {
					ret.add(reason);
				}
			}
			return ret;
		}

		@Override
		public VerificationReason getReason(Integer reasonId) {
			return reasons.get(reasonId);
		}

		@Override
		public VerificationReason getReasonByName(VerificationReason.Type type, String name) {
			for (VerificationReason reason : reasons.values()) {
				if (reason.getType() == type && reason.getName().equalsIgnoreCase(name)) {
					return reason;
				}
			}
			return null;
		}

		@Override
		public void saveReason(VerificationReason reason) {
			reason.setReasonId(reasons.size() + 1);
			reasons.put(reason.getReasonId(), reason);
		}

		@Override
		public void saveItem(BillItemVerification item) {
			item.setDateChanged(new Date());
			items.put(item.getPatientServiceBillId(), copy(item));
			events.add(new Event(item.getPatientServiceBillId(), item.getStatus().name(), null));
		}

		@Override
		public void saveStage(Integer globalBillId, BillVerificationStage stage) {
			stages.put(globalBillId, stage);
			events.add(new Event(null, stage.name(), stage));
		}

		private BillItemVerification copy(BillItemVerification item) {
			BillItemVerification ret = new BillItemVerification();
			ret.setPatientServiceBillId(item.getPatientServiceBillId());
			ret.setGlobalBillId(item.getGlobalBillId());
			ret.setStatus(item.getStatus());
			ret.setReasonId(item.getReasonId());
			ret.setReasonName(item.getReasonName());
			ret.setNote(item.getNote());
			ret.setDateChanged(item.getDateChanged());
			return ret;
		}
	}
}
