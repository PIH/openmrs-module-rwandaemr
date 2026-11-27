package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.businesslogic.ConsommationUtil;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.PatientServiceBill;
import org.openmrs.module.rwandaemr.billing.BillJourneyScope;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerification;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationService;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationStage;
import org.openmrs.module.rwandaemr.billing.verification.VerificationEvent;
import org.openmrs.module.rwandaemr.billing.verification.VerificationReason;
import org.openmrs.module.rwandaemr.page.controller.admin.RhipVoucherSubmissionsPageController;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the RHIP voucher page asks for without reloading: deciding on items, which returns the new state of the bill
 * so the page updates it in place, and the audit trail and patient journey of a bill when they are opened.
 */
@Controller
public class BillVerificationRestController {

	private final Log log = LogFactory.getLog(getClass());

	private static final List<String> VIEW_PRIVILEGES = Arrays.asList(
			"Billing RHIP Voucher - View History",
			"Billing Configuration - View Billing Admin",
			BillVerificationService.PRIVILEGE_FACILITY,
			BillVerificationService.PRIVILEGE_INSURER);

	@Autowired
	BillVerificationService verificationService;

	/**
	 * Accepts or rejects one item, or every item still to decide when no item is given
	 * @return the bill after the decision
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/billVerification/decide", method = RequestMethod.POST)
	@ResponseBody
	public Object decide(@RequestParam("globalBillId") Integer globalBillId,
	                     @RequestParam(value = "itemId", required = false) Integer itemId,
	                     @RequestParam("decision") String decision,
	                     @RequestParam(value = "reasonId", required = false) String reasonId,
	                     @RequestParam(value = "otherReason", required = false) String otherReason,
	                     @RequestParam(value = "note", required = false) String note) {
		GlobalBill globalBill = getVisibleBill(globalBillId);
		boolean accept = "accept".equalsIgnoreCase(decision);
		Integer chosenReasonId = parseInteger(reasonId);
		String typedReason = chosenReasonId == null ? otherReason : null;
		String message;
		if (itemId == null) {
			int decided = verificationService.decideAll(globalBillId, accept, chosenReasonId, typedReason, note);
			message = decided + (decided == 1 ? " item decided." : " items decided.");
		}
		else {
			verificationService.decide(globalBillId, itemId, accept, chosenReasonId, typedReason, note);
			message = "Decision saved.";
		}
		SimpleObject ret = toBillState(globalBill);
		ret.put("success", true);
		ret.put("message", message);
		return ret;
	}

	/**
	 * @return the audit trail of the bill, newest first
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/billVerification/history", method = RequestMethod.GET)
	@ResponseBody
	public Object getHistory(@RequestParam("globalBillId") Integer globalBillId) {
		checkCanView();
		getVisibleBill(globalBillId);
		if (!verificationService.appliesToBill(globalBillId)) {
			throw new APIException("The bills of this insurance are not verified.");
		}
		List<VerificationEvent> events = new ArrayList<VerificationEvent>(verificationService.getEvents(globalBillId));
		Collections.reverse(events);
		List<SimpleObject> ret = new ArrayList<SimpleObject>();
		for (VerificationEvent event : events) {
			SimpleObject o = new SimpleObject();
			o.put("date", event.getDate());
			o.put("username", StringUtils.defaultString(event.getUsername()));
			o.put("itemId", event.getPatientServiceBillId());
			o.put("itemStatus", event.getItemStatus() == null ? "" : event.getItemStatus().getLabel());
			o.put("reason", StringUtils.defaultString(event.getReasonName()));
			o.put("note", StringUtils.defaultString(event.getNote()));
			o.put("stage", event.getStage() == null ? "" : event.getStage().name());
			o.put("stageLabel", event.getStage() == null ? "" : event.getStage().getLabel());
			ret.add(o);
		}
		SimpleObject response = new SimpleObject();
		response.put("globalBillId", globalBillId);
		response.put("events", ret);
		return response;
	}

	/**
	 * @return the encounters of the visit of the bill, most recent action first
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/billVerification/journey", method = RequestMethod.GET)
	@ResponseBody
	public Object getJourney(@RequestParam("globalBillId") Integer globalBillId) {
		checkCanView();
		GlobalBill globalBill = getVisibleBill(globalBillId);
		BillJourneyScope scope = new BillJourneyScope(globalBill);
		List<SimpleObject> rows = new ArrayList<SimpleObject>();
		for (RhipVoucherSubmissionsPageController.PatientJourneyLogRow row
				: new RhipVoucherSubmissionsPageController().getPatientJourneyLogs(globalBill)) {
			SimpleObject o = new SimpleObject();
			o.put("encounterId", row.getEncounterId());
			o.put("action", row.getAction());
			o.put("encounterType", row.getEncounterType());
			o.put("form", row.getFormName());
			o.put("location", row.getLocationName());
			o.put("username", row.getUsername());
			o.put("encounterDatetime", row.getEncounterDatetime());
			o.put("actionDatetime", row.getActionDatetime());
			rows.add(o);
		}
		SimpleObject ret = new SimpleObject();
		ret.put("globalBillId", globalBillId);
		ret.put("visitId", scope.getVisitId());
		ret.put("start", scope.getStart());
		ret.put("end", scope.getEnd());
		ret.put("rows", rows);
		return ret;
	}

	/**
	 * @return the verification of the bill as the page shows it: its step, its counts and amounts, and each item
	 */
	private SimpleObject toBillState(GlobalBill globalBill) {
		Integer globalBillId = globalBill.getGlobalBillId();
		BillVerificationStage stage = verificationService.getStage(globalBillId);
		Map<Integer, BillItemVerification> verifications = verificationService.getItems(Collections.singletonList(globalBillId));
		List<Consommation> consommations = ConsommationUtil.getConsommationsByGlobalBill(globalBill);
		RhipVoucherSubmissionsPageController.VerificationSummary summary =
				new RhipVoucherSubmissionsPageController.VerificationSummary(stage, consommations, verifications);
		Integer insuranceId = globalBill.getInsurance() == null ? null : globalBill.getInsurance().getInsuranceId();
		boolean canAct = verificationService.canActAt(stage, insuranceId);

		List<SimpleObject> items = new ArrayList<SimpleObject>();
		for (Consommation consommation : consommations == null ? Collections.<Consommation>emptyList() : consommations) {
			if (consommation == null || Boolean.TRUE.equals(consommation.getVoided()) || consommation.getBillItems() == null) {
				continue;
			}
			for (PatientServiceBill item : consommation.getBillItems()) {
				if (item == null || Boolean.TRUE.equals(item.getVoided())) {
					continue;
				}
				BillItemVerification verification = verifications.get(item.getPatientServiceBillId());
				BillItemVerificationStatus status = verification == null ? BillItemVerificationStatus.PENDING : verification.getStatus();
				SimpleObject o = new SimpleObject();
				o.put("itemId", item.getPatientServiceBillId());
				o.put("status", status.name());
				o.put("statusLabel", status.getLabel());
				o.put("outcome", status.getOutcome().name());
				o.put("accepted", status == stage.getAcceptStatus());
				o.put("rejected", status == stage.getRejectStatus());
				o.put("reviewable", canAct && stage.isReviewable(status));
				o.put("reason", verification == null ? "" : StringUtils.defaultString(verification.getReasonName()));
				o.put("note", verification == null ? "" : StringUtils.defaultString(verification.getNote()));
				items.add(o);
			}
		}

		SimpleObject ret = new SimpleObject();
		ret.put("globalBillId", globalBillId);
		ret.put("stage", stage.name());
		ret.put("stageLabel", stage.getLabel());
		ret.put("chipClass", summary.getChipClass());
		ret.put("canAct", canAct);
		ret.put("itemCount", summary.getItemCount());
		ret.put("awaitingCount", summary.getAwaitingCount());
		ret.put("rejectedCount", summary.getRejectedCount());
		ret.put("acceptedAmount", format(summary.getAcceptedAmount()));
		ret.put("rejectedAmount", format(summary.getRejectedAmount()));
		ret.put("pendingAmount", format(summary.getPendingAmount()));
		Integer eventCount = verificationService.getEventCounts(Collections.singletonList(globalBillId)).get(globalBillId);
		ret.put("eventCount", eventCount == null ? 0 : eventCount);
		ret.put("items", items);
		Map<String, List<SimpleObject>> reasons = new LinkedHashMap<String, List<SimpleObject>>();
		for (VerificationReason.Type type : VerificationReason.Type.values()) {
			List<SimpleObject> list = new ArrayList<SimpleObject>();
			for (VerificationReason reason : verificationService.getReasons(type)) {
				SimpleObject o = new SimpleObject();
				o.put("id", reason.getReasonId());
				o.put("name", reason.getName());
				list.add(o);
			}
			reasons.put(type.name(), list);
		}
		ret.put("reasons", reasons);
		return ret;
	}

	private GlobalBill getVisibleBill(Integer globalBillId) {
		GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
		if (globalBill == null) {
			throw new IllegalArgumentException("Global bill " + globalBillId + " not found");
		}
		if (!verificationService.canSeeBillsOf(globalBill.getInsurance() == null ? null : globalBill.getInsurance().getInsuranceId())) {
			throw new APIAuthenticationException("This bill is for an insurance you don't verify for.");
		}
		return globalBill;
	}

	private void checkCanView() {
		for (String privilege : VIEW_PRIVILEGES) {
			if (Context.hasPrivilege(privilege)) {
				return;
			}
		}
		throw new APIAuthenticationException("You do not have permission to view the verification of this bill.");
	}

	private String format(BigDecimal amount) {
		return NumberFormat.getInstance(Context.getLocale()).format(amount == null ? BigDecimal.ZERO : amount);
	}

	private Integer parseInteger(String value) {
		try {
			return StringUtils.isBlank(value) ? null : Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	@ExceptionHandler(APIAuthenticationException.class)
	@ResponseBody
	public ResponseEntity<?> handleAuthenticationException(APIAuthenticationException e) {
		log.warn("Bill verification refused: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
	}

	@ExceptionHandler({ APIException.class, IllegalArgumentException.class })
	@ResponseBody
	public ResponseEntity<?> handleBadRequest(RuntimeException e) {
		log.warn("Bill verification not saved: " + e.getMessage());
		return ResponseEntity.badRequest().body(error(e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ResponseEntity<?> handleError(Exception e) {
		log.error("Unable to save or read the verification of a bill", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(error("The verification could not be saved: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName())));
	}

	private Map<String, Object> error(String message) {
		Map<String, Object> ret = new LinkedHashMap<String, Object>();
		ret.put("success", false);
		ret.put("message", message);
		return ret;
	}
}
