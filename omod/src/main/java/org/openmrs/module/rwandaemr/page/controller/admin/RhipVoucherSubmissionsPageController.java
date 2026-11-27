package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.mohbilling.businesslogic.ConsommationUtil;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.businesslogic.InsuranceUtil;
import org.openmrs.module.mohbilling.integration.insurance.RhipVoucherService;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.model.PatientServiceBill;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmissionSearchCriteria;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerification;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.BillItemGroups;
import org.openmrs.module.rwandaemr.billing.BillJourneyScope;
import org.openmrs.module.rwandaemr.billing.VisitTransferLookup;
import org.openmrs.module.rwandaemr.billing.VisitTransfers;
import org.openmrs.module.rwandaemr.billing.InsuranceFilterOptions;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationService;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationStage;
import org.openmrs.module.rwandaemr.billing.verification.BulkSendResult;
import org.openmrs.module.rwandaemr.billing.verification.VerificationReason;
import org.openmrs.module.rwandaemr.billing.verification.VerificationScope;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RhipVoucherSubmissionsPageController {

	private static final Log log = LogFactory.getLog(RhipVoucherSubmissionsPageController.class);
	public static final String PRIVILEGE_VIEW = "Billing RHIP Voucher - View Submission Page";
	public static final String PRIVILEGE_SEND = "Billing RHIP Voucher - Send";
	public static final String PRIVILEGE_CONFIRM = "Billing RHIP Voucher - Confirm";
	public static final String PRIVILEGE_RETRY = "Billing RHIP Voucher - Retry";
	public static final String PRIVILEGE_VIEW_HISTORY = "Billing RHIP Voucher - View History";
	private static final String PRIVILEGE_BILLING_ADMIN = "Billing Configuration - View Billing Admin";
	private static final String DATE_PATTERN = "yyyy-MM-dd";
	private static final String SQL_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_PATIENT_JOURNEY_LOGS = 50;

	/** The Transfer app's privileges to list transfers and to read a transfer's PDF */
	private static final String TRANSFER_PRIVILEGE_LIST = "Task: transferapp.listTransfers";
	private static final String TRANSFER_PRIVILEGE_PAST = "View: transferapp.pasttransfers";

	public String get(PageModel model,
	                  UiUtils ui,
	                  UiSessionContext sessionContext,
	                  @SpringBean("mohBillingService") BillingService billingService,
	                  @SpringBean BillVerificationService verificationService,
	                  @SpringBean("dbSessionFactory") DbSessionFactory dbSessionFactory,
	                  @RequestParam(value = "open", required = false) Integer openGlobalBillId,
	                  @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDateParam,
	                  @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDateParam,
	                  @RequestParam(value = "dischargeDate", required = false) String dischargeDateParam,
	                  @RequestParam(value = "dateType", required = false) String dateTypeParam,
	                  @RequestParam(value = "status", required = false) String statusParam,
	                  @RequestParam(value = "insuranceId", required = false) String insuranceIdParam,
	                  @RequestParam(value = "query", required = false) String queryParam,
	                  @RequestParam(value = "sortBy", required = false) String sortByParam,
	                  @RequestParam(value = "sortDirection", required = false) String sortDirectionParam,
	                  @RequestParam(value = "page", required = false) Integer pageParam,
	                  @RequestParam(value = "pageSize", required = false) Integer pageSizeParam) {
		if (!canViewPage()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to view RHIP voucher submissions.");
			return "redirect:" + ui.pageLink("coreapps", "systemadministration/systemAdministration");
		}

		String today = new SimpleDateFormat(DATE_PATTERN).format(new Date());
		String defaultDischargeDateText = defaultIfBlank(dischargeDateParam, today);
		String dischargeStartDateText = defaultIfBlank(dischargeStartDateParam, defaultDischargeDateText);
		String dischargeEndDateText = defaultIfBlank(dischargeEndDateParam, dischargeStartDateText);
		Date dischargeStartDate = parseDate(dischargeStartDateText);
		Date dischargeEndDate = parseDate(dischargeEndDateText);
		if (dischargeStartDate == null) {
			dischargeStartDate = new Date();
			dischargeStartDateText = today;
		}
		if (dischargeEndDate == null) {
			dischargeEndDate = dischargeStartDate;
			dischargeEndDateText = dischargeStartDateText;
		}
		if (dischargeEndDate.before(dischargeStartDate)) {
			Date originalStartDate = dischargeStartDate;
			String originalStartDateText = dischargeStartDateText;
			dischargeStartDate = dischargeEndDate;
			dischargeStartDateText = dischargeEndDateText;
			dischargeEndDate = originalStartDate;
			dischargeEndDateText = originalStartDateText;
		}
		String status = defaultIfBlank(statusParam, "ALL").toUpperCase();
		// An insurer's verifier only sees the bills of the insurances they work for, one choice at a time.  Every
		// insurance of the MUTUELLE category is one choice, as in the bill report.
		List<Integer> verifierInsuranceIds = verificationService.getVerifierInsuranceIds();
		boolean insuranceLimited = verificationService.isLimitedToInsurances();
		boolean verifierWithoutInsurance = insuranceLimited && verifierInsuranceIds.isEmpty();
		List<InsuranceFilterOptions.Option> insuranceOptions = getInsuranceFilterOptions(verificationService);
		InsuranceFilterOptions.Option chosenInsurance = chooseInsurance(insuranceIdParam, insuranceOptions, insuranceLimited);
		String selectedInsuranceValue = chosenInsurance == null ? null : chosenInsurance.getValue();
		boolean insuranceLocked = insuranceLimited && insuranceOptions.size() <= 1;
		String query = trimToNull(queryParam);
		String sortBy = defaultIfBlank(sortByParam, "dischargeDate");
		String sortDirection = defaultIfBlank(sortDirectionParam, "desc");
		int page = pageParam == null || pageParam <= 0 ? 1 : pageParam;
		int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSizeParam, MAX_PAGE_SIZE);

		String dateType = normalizeDateType(dateTypeParam);
		RhipVoucherSubmissionSearchCriteria criteria = new RhipVoucherSubmissionSearchCriteria();
		criteria.setDateType(dateType);
		criteria.setDischargeStartDate(getStartOfDay(dischargeStartDate));
		criteria.setDischargeEndDate(getEndOfDay(dischargeEndDate));
		criteria.setStatus(status);
		applyInsurance(criteria, chosenInsurance, insuranceLimited);
		criteria.setQuery(query);
		criteria.setSortBy(sortBy);
		criteria.setSortDirection(sortDirection);

		int totalCount = verifierWithoutInsurance ? 0 : billingService.countRhipVoucherSubmissionGlobalBills(criteria);
		int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
		if (page > totalPages) {
			page = totalPages;
		}
		List<GlobalBill> globalBills = verifierWithoutInsurance ? Collections.<GlobalBill>emptyList()
				: billingService.getRhipVoucherSubmissionGlobalBills(criteria, (page - 1) * pageSize, pageSize);
		if (verifierWithoutInsurance) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"Your account is not linked to an insurance, so no bills are shown. Ask an administrator to set "
							+ BillVerificationService.USER_PROPERTY_INSURANCE_ID + " on your account.");
		}

		// Facility staff can send every bill of the period whose items they have all decided to the insurer at once
		BulkSendResult bulkSend = null;
		BulkSendResult insurerBulk = null;
		boolean facilityBulk = Context.hasPrivilege(BillVerificationService.PRIVILEGE_FACILITY);
		boolean insurerBulkAllowed = Context.hasPrivilege(BillVerificationService.PRIVILEGE_INSURER);
		if ((facilityBulk || insurerBulkAllowed) && totalCount > 0) {
			// Only the ids of the bills of the period are read, not the bills themselves
			List<Integer> periodBillIds = totalCount <= pageSize ? getGlobalBillIds(globalBills)
					: billingService.getRhipVoucherSubmissionGlobalBillIds(criteria);
			if (facilityBulk) {
				bulkSend = verificationService.getBulkSendSummary(periodBillIds);
			}
			// The insurer can submit together every bill whose every item it decided
			if (insurerBulkAllowed) {
				insurerBulk = verificationService.getInsurerBulkSummary(periodBillIds);
			}
		}
		model.addAttribute("bulkSend", bulkSend);
		model.addAttribute("insurerBulk", insurerBulk);

		Map<Integer, List<Consommation>> consommationsByGlobalBillId = buildConsommationsByGlobalBillId(globalBills);
		addVerification(model, globalBills, consommationsByGlobalBillId, verificationService);
		model.addAttribute("openGlobalBillId", openGlobalBillId);
		model.addAttribute("insuranceLocked", insuranceLocked);
		model.addAttribute("insuranceLimited", insuranceLimited);
		model.addAttribute("insurerOnly", verificationService.isInsurerOnly());
		model.addAttribute("rows", buildRows(globalBills, billingService, consommationsByGlobalBillId));
		model.addAttribute("consommationsByGlobalBillId", consommationsByGlobalBillId);
		// The items of each bill by service category, as on the global bill view
		List<BillItemGroups.Category> itemCategories = BillItemGroups.getConfiguredCategories();
		Map<Integer, List<BillItemGroups.Group>> itemGroups = new HashMap<Integer, List<BillItemGroups.Group>>();
		for (Map.Entry<Integer, List<Consommation>> entry : consommationsByGlobalBillId.entrySet()) {
			itemGroups.put(entry.getKey(), BillItemGroups.build(entry.getValue(), itemCategories));
		}
		model.addAttribute("itemGroupsByGlobalBillId", itemGroups);
		Map<Integer, BillJourneyScope> journeyScopes = new HashMap<Integer, BillJourneyScope>();
		for (GlobalBill globalBill : globalBills) {
			journeyScopes.put(globalBill.getGlobalBillId(), new BillJourneyScope(globalBill));
		}
		model.addAttribute("journeyScopeByGlobalBillId", journeyScopes);
		addTransfers(model, globalBills, journeyScopes, dbSessionFactory);
		model.addAttribute("insuranceOptions", insuranceOptions);
		model.addAttribute("insuranceId", selectedInsuranceValue == null ? "" : selectedInsuranceValue);
		model.addAttribute("dischargeStartDate", dischargeStartDateText);
		model.addAttribute("dischargeEndDate", dischargeEndDateText);
		model.addAttribute("dischargeDate", dischargeStartDateText);
		model.addAttribute("status", status);
		model.addAttribute("query", query == null ? "" : query);
		model.addAttribute("sortBy", sortBy);
		model.addAttribute("sortDirection", sortDirection);
		model.addAttribute("page", page);
		model.addAttribute("pageSize", pageSize);
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("hasPreviousPage", page > 1);
		model.addAttribute("hasNextPage", page < totalPages);
		model.addAttribute("previousPage", page - 1);
		model.addAttribute("nextPage", page + 1);
		model.addAttribute("dateType", dateType);
		model.addAttribute("filterQueryString", buildFilterQueryString(dateType, dischargeStartDateText, dischargeEndDateText,
				selectedInsuranceValue, status, query, sortBy,
				sortDirection, pageSize));
		model.addAttribute("canSend", Context.hasPrivilege(PRIVILEGE_SEND));
		model.addAttribute("canConfirm", canConfirmVoucher());
		model.addAttribute("canRetry", Context.hasPrivilege(PRIVILEGE_RETRY));
		model.addAttribute("canViewHistory", Context.hasPrivilege(PRIVILEGE_VIEW_HISTORY)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_FACILITY)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_INSURER));
		return null;
	}

	public String post(UiUtils ui,
	                   UiSessionContext sessionContext,
	                   @SpringBean("rhipVoucherService") RhipVoucherService voucherService,
	                   @SpringBean BillVerificationService verificationService,
	                   @SpringBean("mohBillingService") BillingService billingService,
	                   @RequestParam(value = "globalBillId", required = false) Integer globalBillId,
	                   @RequestParam(value = "action", required = false) String action,
	                   @RequestParam(value = "itemId", required = false) Integer itemId,
	                   @RequestParam(value = "decision", required = false) String decision,
	                   @RequestParam(value = "reasonId", required = false) String reasonId,
	                   @RequestParam(value = "otherReason", required = false) String otherReason,
	                   @RequestParam(value = "note", required = false) String note,
	                   @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDate,
	                   @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDate,
	                   @RequestParam(value = "dischargeDate", required = false) String dischargeDate,
	                   @RequestParam(value = "dateType", required = false) String dateType,
	                   @RequestParam(value = "status", required = false) String status,
	                   @RequestParam(value = "insuranceId", required = false) String insuranceId,
	                   @RequestParam(value = "query", required = false) String query,
	                   @RequestParam(value = "sortBy", required = false) String sortBy,
	                   @RequestParam(value = "sortDirection", required = false) String sortDirection,
	                   @RequestParam(value = "page", required = false) Integer page,
	                   @RequestParam(value = "pageSize", required = false) Integer pageSize) {
		if ("sendAllToInsurer".equalsIgnoreCase(action)) {
			try {
				RhipVoucherSubmissionSearchCriteria criteria = buildPeriodCriteria(dateType, defaultIfBlank(dischargeStartDate, dischargeDate),
						dischargeEndDate, status, insuranceId, query, verificationService);
				BulkSendResult result = verificationService.sendAllToInsurer(billingService.getRhipVoucherSubmissionGlobalBillIds(criteria));
				StringBuilder message = new StringBuilder();
				message.append(result.getSentToInsurer()).append(result.getSentToInsurer() == 1 ? " bill sent" : " bills sent").append(" to the insurer");
				if (result.getClosed() > 0) {
					message.append(", ").append(result.getClosed()).append(" closed because every item was rejected");
				}
				if (result.getNotReady() > 0) {
					message.append(". ").append(result.getNotReady())
							.append(result.getNotReady() == 1 ? " bill still has" : " bills still have").append(" items to decide");
				}
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, message.append(".").toString());
			}
			catch (APIException e) {
				log.debug("Bulk send to the insurer refused", e);
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
						StringUtils.defaultIfBlank(e.getMessage(), "The bills could not be sent to the insurer."));
			}
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
					query, sortBy, sortDirection, page, pageSize);
		}
		if ("submitAllInsurerReviews".equalsIgnoreCase(action)) {
			try {
				RhipVoucherSubmissionSearchCriteria criteria = buildPeriodCriteria(dateType, defaultIfBlank(dischargeStartDate, dischargeDate),
						dischargeEndDate, status, insuranceId, query, verificationService);
				BulkSendResult result = verificationService.submitAllInsurerReviews(billingService.getRhipVoucherSubmissionGlobalBillIds(criteria));
				StringBuilder message = new StringBuilder();
				int submitted = result.getSentToFacility() + result.getClosed();
				message.append(submitted).append(submitted == 1 ? " review submitted" : " reviews submitted");
				message.append(": ").append(result.getClosed()).append(" closed, ").append(result.getSentToFacility())
						.append(" sent back to the facility for the rejected items");
				if (result.getNotReady() > 0) {
					message.append(". ").append(result.getNotReady())
							.append(result.getNotReady() == 1 ? " bill still has" : " bills still have").append(" items to decide");
				}
				if (result.getRefused() > 0) {
					message.append(". ").append(result.getRefused()).append(" could not be submitted");
				}
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, message.append(".").toString());
			}
			catch (APIException e) {
				log.debug("Bulk submission of the insurer reviews refused", e);
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
						StringUtils.defaultIfBlank(e.getMessage(), "The reviews could not be submitted."));
			}
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
					query, sortBy, sortDirection, page, pageSize);
		}
		if (globalBillId == null) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, "Choose a bill.");
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
					query, sortBy, sortDirection, page, pageSize);
		}
		if ("verifyItem".equalsIgnoreCase(action) || "verifyAll".equalsIgnoreCase(action)
				|| "submitVerification".equalsIgnoreCase(action)) {
			try {
				if ("verifyAll".equalsIgnoreCase(action)) {
					Integer chosenReasonId = parseInteger(reasonId);
					int decided = verificationService.decideAll(globalBillId, "accept".equalsIgnoreCase(decision),
							chosenReasonId, chosenReasonId == null ? otherReason : null, note);
					sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
							decided + (decided == 1 ? " item decided." : " items decided."));
				}
				else if ("verifyItem".equalsIgnoreCase(action)) {
					if (itemId == null) {
						throw new APIException("Choose the item to decide.");
					}
					Integer chosenReasonId = parseInteger(reasonId);
					verificationService.decide(globalBillId, itemId, "accept".equalsIgnoreCase(decision), chosenReasonId,
							chosenReasonId == null ? otherReason : null, note);
				}
				else {
					BillVerificationStage next = verificationService.submit(globalBillId);
					sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
							next == BillVerificationStage.CLOSED ? "Verification closed." : "Sent for the " + next.getLabel().toLowerCase() + ".");
				}
			}
			catch (APIException e) {
				log.debug("Verification of global bill " + globalBillId + " refused", e);
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
						StringUtils.defaultIfBlank(e.getMessage(), "The verification could not be saved."));
			}
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
					query, sortBy, sortDirection, page, pageSize) + (globalBillId == null ? "" : appendOpen(globalBillId, itemId));
		}

		boolean confirmAction = "confirm".equalsIgnoreCase(action);
		boolean retryAction = "retry".equalsIgnoreCase(action);
		if (confirmAction && !canConfirmVoucher()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to confirm RHIP vouchers.");
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
		if (retryAction && !Context.hasPrivilege(PRIVILEGE_RETRY)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to retry RHIP vouchers.");
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
		if (!confirmAction && !retryAction && !Context.hasPrivilege(PRIVILEGE_SEND)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to send RHIP vouchers.");
			return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}

		GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
		RhipVoucherSubmission submission = confirmAction
				? voucherService.closeVoucherForGlobalBillWithAudit(globalBill)
				: voucherService.submitVoucherForGlobalBillWithAudit(globalBill);
		String expectedStatus = confirmAction ? RhipVoucherSubmission.STATUS_CONFIRMED : RhipVoucherSubmission.STATUS_SENT;
		if (submission != null && expectedStatus.equals(submission.getStatus())) {
			String reference = StringUtils.defaultIfBlank(submission.getVoucherReferenceNumber(), submission.getVoucherCode());
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
					(confirmAction ? "RHIP voucher confirmed successfully."
							: "RHIP voucher submitted successfully"
									+ (StringUtils.isBlank(reference) ? "." : ". Reference: " + reference)));
			if (confirmAction && StringUtils.isNotBlank(submission.getErrorMessage())) {
				// MMI closed the bill but left some lines open (remainingUnbilled)
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
						submission.getErrorMessage());
			}
		} else {
			String defaultError = confirmAction ? "RHIP voucher confirmation failed." : "RHIP voucher submission failed.";
			String error = submission == null ? defaultError : submission.getErrorMessage();
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					StringUtils.defaultIfBlank(error, defaultError));
		}
		return redirect(ui, dateType, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
				query, sortBy, sortDirection, page, pageSize);
	}

	/**
	 * @return the search of the bills discharged in the given period, with the same defaults as the page: today when no
	 * date is given, and the dates swapped when the end is before the start
	 */
	private RhipVoucherSubmissionSearchCriteria buildPeriodCriteria(String dateType, String startText, String endText, String status,
	                                                                String insuranceId, String query,
	                                                                BillVerificationService verificationService) {
		Date start = parseDate(defaultIfBlank(startText, new SimpleDateFormat(DATE_PATTERN).format(new Date())));
		if (start == null) {
			start = new Date();
		}
		Date end = parseDate(defaultIfBlank(endText, ""));
		if (end == null) {
			end = start;
		}
		if (end.before(start)) {
			Date swap = start;
			start = end;
			end = swap;
		}
		boolean limited = verificationService.isLimitedToInsurances();
		InsuranceFilterOptions.Option chosenInsurance = chooseInsurance(insuranceId,
				getInsuranceFilterOptions(verificationService), limited);
		RhipVoucherSubmissionSearchCriteria criteria = new RhipVoucherSubmissionSearchCriteria();
		criteria.setDateType(normalizeDateType(dateType));
		criteria.setDischargeStartDate(getStartOfDay(start));
		criteria.setDischargeEndDate(getEndOfDay(end));
		criteria.setStatus(defaultIfBlank(status, "ALL").toUpperCase());
		applyInsurance(criteria, chosenInsurance, limited);
		criteria.setQuery(trimToNull(query));
		return criteria;
	}

	private List<Integer> getGlobalBillIds(List<GlobalBill> globalBills) {
		List<Integer> ret = new ArrayList<Integer>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			if (globalBill != null && globalBill.getGlobalBillId() != null) {
				ret.add(globalBill.getGlobalBillId());
			}
		}
		return ret;
	}

	private boolean canViewPage() {
		return Context.hasPrivilege(PRIVILEGE_VIEW)
				|| Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_FACILITY)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_INSURER);
	}

	/**
	 * @return the query parameter that opens the details of the bill after a redirect, and the anchor of the item
	 * decided or of the bill
	 */
	private String appendOpen(Integer globalBillId, Integer itemId) {
		return "&open=" + globalBillId + (itemId == null ? "#rhip-bill-" + globalBillId : "#rhip-item-" + itemId);
	}

	/**
	 * Adds the verification of the bills to the model: the step each bill is at, the decision on each item, the
	 * totals of each bill, whether the user can act on it, its audit trail, and the reasons to choose from
	 */
	private void addVerification(PageModel model, List<GlobalBill> globalBills,
	                             Map<Integer, List<Consommation>> consommationsByGlobalBillId,
	                             BillVerificationService verificationService) {
		List<Integer> globalBillIds = new ArrayList<Integer>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			globalBillIds.add(globalBill.getGlobalBillId());
		}
		Map<Integer, BillVerificationStage> stages = verificationService.getStages(globalBillIds);
		Map<Integer, BillItemVerification> items = verificationService.getItems(globalBillIds);
		Map<Integer, VerificationSummary> summaries = new HashMap<Integer, VerificationSummary>();
		Map<Integer, Boolean> canAct = new HashMap<Integer, Boolean>();
		// Only the bills of the insurances in rwandaemr.billVerification.insurances are verified
		Map<Integer, Boolean> applies = new HashMap<Integer, Boolean>();
		VerificationScope scope = verificationService.getScope();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			Integer globalBillId = globalBill.getGlobalBillId();
			BillVerificationStage stage = stages.get(globalBillId);
			Insurance insurance = globalBill.getInsurance();
			Integer insuranceId = insurance == null ? null : insurance.getInsuranceId();
			boolean verified = scope.includes(insuranceId, insurance == null ? null : insurance.getCategory());
			applies.put(globalBillId, verified);
			canAct.put(globalBillId, verified && verificationService.canActAt(stage, insuranceId));
			summaries.put(globalBillId, new VerificationSummary(stage, consommationsByGlobalBillId.get(globalBillId), items));
		}
		model.addAttribute("verificationStages", stages);
		model.addAttribute("itemVerifications", items);
		model.addAttribute("verificationSummaries", summaries);
		model.addAttribute("verificationCanAct", canAct);
		model.addAttribute("verificationApplies", applies);
		// The audit trail itself is read when it is opened
		model.addAttribute("verificationEventCounts", verificationService.getEventCounts(globalBillIds));
		model.addAttribute("rejectionReasons", verificationService.getReasons(VerificationReason.Type.REJECTION));
		model.addAttribute("disputeReasons", verificationService.getReasons(VerificationReason.Type.DISPUTE));
		model.addAttribute("pendingItemStatus", BillItemVerificationStatus.PENDING);
	}

	private boolean canConfirmVoucher() {
		return Context.hasPrivilege(PRIVILEGE_CONFIRM)
				|| Context.hasPrivilege(PRIVILEGE_SEND)
				|| Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN);
	}

	private List<RhipVoucherSubmissionRow> buildRows(List<GlobalBill> globalBills, BillingService billingService,
	                                                 Map<Integer, List<Consommation>> consommationsByGlobalBillId) {
		List<RhipVoucherSubmissionRow> rows = new ArrayList<RhipVoucherSubmissionRow>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			RhipVoucherSubmission latest = billingService.getLatestRhipVoucherSubmission(globalBill);
			RhipVoucherSubmission successful = billingService.getSuccessfulRhipVoucherSubmission(globalBill);
			Integer globalBillId = globalBill == null ? null : globalBill.getGlobalBillId();
			List<Consommation> consommations = globalBillId == null ? Collections.<Consommation>emptyList()
					: consommationsByGlobalBillId.get(globalBillId);
			rows.add(new RhipVoucherSubmissionRow(globalBill, latest, successful, consommations));
		}
		return rows;
	}

	private Map<Integer, List<Consommation>> buildConsommationsByGlobalBillId(List<GlobalBill> globalBills) {
		Map<Integer, List<Consommation>> ret = new HashMap<Integer, List<Consommation>>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			ret.put(globalBill.getGlobalBillId(), ConsommationUtil.getConsommationsByGlobalBill(globalBill));
		}
		return ret;
	}

	/**
	 * @return the encounters of the visit of the bill, most recent action first.  Read when the bill is opened.
	 */
	@SuppressWarnings("unchecked")
	public List<PatientJourneyLogRow> getPatientJourneyLogs(GlobalBill globalBill) {
		List<PatientJourneyLogRow> logs = new ArrayList<PatientJourneyLogRow>();
		Patient patient = getPatient(globalBill);
		if (patient == null || patient.getPatientId() == null) {
			return logs;
		}
		// The encounters of the visit of the bill, as in the bill report
		String where = new BillJourneyScope(globalBill).toSqlWhere(patient.getPatientId());
		String sql = "select * from ("
				+ patientJourneyLogSelect(where, "Created", "e.creator", "e.date_created")
				+ " union all "
				+ patientJourneyLogSelect(where + " and e.date_changed is not null", "Updated", "e.changed_by", "e.date_changed")
				+ " union all "
				+ patientJourneyLogSelect(where + " and e.voided = 1 and e.date_voided is not null", "Voided", "e.voided_by", "e.date_voided")
				+ ") patient_journey_logs order by action_datetime desc limit " + MAX_PATIENT_JOURNEY_LOGS;

		// The query is built from ids and dates only. Verifiers see the journey without needing SQL access of their own.
		List<List<Object>> rows;
		try {
			Context.addProxyPrivilege(PrivilegeConstants.SQL_LEVEL_ACCESS);
			rows = Context.getAdministrationService().executeSQL(sql, true);
		}
		finally {
			Context.removeProxyPrivilege(PrivilegeConstants.SQL_LEVEL_ACCESS);
		}
		for (List<Object> row : rows) {
			logs.add(new PatientJourneyLogRow(
					integerValue(value(row, 0)),
					stringValue(value(row, 1)),
					stringValue(value(row, 2)),
					stringValue(value(row, 3)),
					stringValue(value(row, 4)),
					stringValue(value(row, 5)),
					dateValue(value(row, 6)),
					dateValue(value(row, 7))));
		}
		return logs;
	}

	private String patientJourneyLogSelect(String where, String action, String userColumn, String dateColumn) {
		return "select e.encounter_id, coalesce(et.name, ''), coalesce(f.name, ''), coalesce(l.name, ''), '"
				+ action + "', coalesce(u.username, ''), e.encounter_datetime, " + dateColumn + " as action_datetime"
				+ " from encounter e"
				+ " left join encounter_type et on et.encounter_type_id = e.encounter_type"
				+ " left join form f on f.form_id = e.form_id"
				+ " left join location l on l.location_id = e.location_id"
				+ " left join users u on u.user_id = " + userColumn
				+ " where " + where;
	}

	/**
	 * Adds the transfers of the visit of each bill that is verified (rwandaemr.billVerification.insurances), and
	 * whether the user can open the Transfer app's forms
	 */
	@SuppressWarnings("unchecked")
	private void addTransfers(PageModel model, List<GlobalBill> globalBills, Map<Integer, BillJourneyScope> journeyScopes,
	                          DbSessionFactory dbSessionFactory) {
		Map<Integer, VisitTransfers> transfers = new HashMap<Integer, VisitTransfers>();
		Map<Integer, Boolean> applies = (Map<Integer, Boolean>) model.get("verificationApplies");
		VisitTransferLookup lookup = new VisitTransferLookup(VisitTransferLookup.runner(dbSessionFactory), getTransferIdConceptId());
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			Integer globalBillId = globalBill.getGlobalBillId();
			Patient patient = getPatient(globalBill);
			if (applies == null || !Boolean.TRUE.equals(applies.get(globalBillId)) || patient == null) {
				continue;
			}
			VisitTransfers visitTransfers = lookup.getTransfers(patient.getPatientId(), journeyScopes.get(globalBillId));
			if (visitTransfers != null) {
				transfers.put(globalBillId, visitTransfers);
			}
		}
		model.addAttribute("transfersByGlobalBillId", transfers);
		model.addAttribute("canPreviewTransfer", Context.hasPrivilege(TRANSFER_PRIVILEGE_LIST)
				|| Context.hasPrivilege(TRANSFER_PRIVILEGE_PAST));
	}

	/**
	 * @return the concept of the Transfer ID question at registration, as the Transfer app finds it
	 */
	private Integer getTransferIdConceptId() {
		String uuid = StringUtils.trimToNull(Context.getAdministrationService().getGlobalProperty(
				VisitTransferLookup.GP_RECEIVED_TRANSFER_CONCEPT, VisitTransferLookup.DEFAULT_RECEIVED_TRANSFER_CONCEPT));
		if (uuid == null) {
			uuid = StringUtils.trimToNull(Context.getAdministrationService().getGlobalProperty(
					VisitTransferLookup.GP_RWANDAEMR_TRANSFER_ID_CONCEPT));
		}
		Concept concept = uuid == null ? null : Context.getConceptService().getConceptByUuid(uuid);
		return concept == null ? null : concept.getConceptId();
	}

	private Patient getPatient(GlobalBill globalBill) {
		if (globalBill == null || globalBill.getAdmission() == null
				|| globalBill.getAdmission().getInsurancePolicy() == null) {
			return null;
		}
		return globalBill.getAdmission().getInsurancePolicy().getOwner();
	}

	private Object value(List<Object> row, int index) {
		return row == null || row.size() <= index ? null : row.get(index);
	}

	private Integer integerValue(Object value) {
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value.toString());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private String stringValue(Object value) {
		return value == null ? "" : value.toString();
	}

	private Date dateValue(Object value) {
		if (value instanceof Date) {
			return (Date) value;
		}
		String text = trimToNull(value == null ? null : value.toString());
		if (text == null) {
			return null;
		}
		String[] patterns = { "yyyy-MM-dd HH:mm:ss.S", SQL_DATE_TIME_PATTERN, DATE_PATTERN };
		for (String pattern : patterns) {
			try {
				return new SimpleDateFormat(pattern).parse(text);
			}
			catch (ParseException e) {
				// Try the next database timestamp format.
			}
		}
		log.debug("Unable to parse patient journey log date value: " + text);
		return null;
	}

	private String redirect(UiUtils ui, String dateType, String dischargeStartDate, String dischargeEndDate,
	                        String insuranceId, String status, String query, String sortBy, String sortDirection,
	                        Integer page, Integer pageSize) {
		StringBuilder params = new StringBuilder();
		appendQueryParam(params, "dateType", normalizeDateType(dateType));
		appendQueryParam(params, "dischargeStartDate", dischargeStartDate);
		appendQueryParam(params, "dischargeEndDate", dischargeEndDate);
		appendQueryParam(params, "insuranceId", insuranceId);
		appendQueryParam(params, "status", status);
		appendQueryParam(params, "query", query);
		appendQueryParam(params, "sortBy", sortBy);
		appendQueryParam(params, "sortDirection", sortDirection);
		appendQueryParam(params, "page", page == null ? null : page.toString());
		appendQueryParam(params, "pageSize", pageSize == null ? null : pageSize.toString());
		String pageLink = ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions");
		return "redirect:" + pageLink + (params.length() == 0 ? "" : "?" + params);
	}

	private String buildFilterQueryString(String dateType, String dischargeStartDate, String dischargeEndDate,
	                                      String insuranceId, String status, String query, String sortBy,
	                                      String sortDirection, int pageSize) {
		StringBuilder ret = new StringBuilder();
		appendQueryParam(ret, "dateType", dateType);
		appendQueryParam(ret, "dischargeStartDate", dischargeStartDate);
		appendQueryParam(ret, "dischargeEndDate", dischargeEndDate);
		appendQueryParam(ret, "insuranceId", insuranceId);
		appendQueryParam(ret, "status", status);
		appendQueryParam(ret, "query", query);
		appendQueryParam(ret, "sortBy", sortBy);
		appendQueryParam(ret, "sortDirection", sortDirection);
		appendQueryParam(ret, "pageSize", String.valueOf(pageSize));
		return ret.toString();
	}

	/**
	 * @return which date the period is on: the admission date, as in the bill report and the insurance summary, or
	 * else the discharge date
	 */
	static String normalizeDateType(String dateType) {
		return RhipVoucherSubmissionSearchCriteria.DATE_TYPE_ADMISSION.equalsIgnoreCase(StringUtils.trimToNull(dateType))
				? RhipVoucherSubmissionSearchCriteria.DATE_TYPE_ADMISSION
				: RhipVoucherSubmissionSearchCriteria.DATE_TYPE_DISCHARGE;
	}

	/**
	 * @return what the user can choose from: MUTUELLE for every insurance of that category, then every other insurance
	 * they can see
	 */
	private List<InsuranceFilterOptions.Option> getInsuranceFilterOptions(BillVerificationService verificationService) {
		return InsuranceFilterOptions.build(getInsuranceOptions(
				verificationService.isLimitedToInsurances() ? verificationService.getVerifierInsuranceIds() : null));
	}

	/**
	 * @return the choice of insurance asked for, if the user can see it.  A user limited to some insurances always has
	 * one: the first they can see when they asked for none, or for one they can't see.  Null for all insurances.
	 */
	private InsuranceFilterOptions.Option chooseInsurance(String value, List<InsuranceFilterOptions.Option> options,
	                                                      boolean limited) {
		InsuranceFilterOptions.Option chosen = InsuranceFilterOptions.find(options, value);
		if (chosen == null && limited && !options.isEmpty()) {
			chosen = options.get(0);
		}
		return chosen;
	}

	/**
	 * Limits the search to the chosen insurance, or every insurance of the chosen category.  A user limited to some
	 * insurances who can see none of them gets no bills.
	 */
	private void applyInsurance(RhipVoucherSubmissionSearchCriteria criteria, InsuranceFilterOptions.Option chosen,
	                            boolean limited) {
		if (chosen == null) {
			if (limited) {
				criteria.setInsuranceId(-1);
			}
			return;
		}
		if (chosen.isCategory()) {
			criteria.setInsuranceIds(chosen.getInsuranceIds());
		}
		else {
			criteria.setInsuranceId(chosen.getInsuranceId());
		}
	}

	/**
	 * @param allowedInsuranceIds the insurances the user can see, or null if they can see all of them
	 */
	private List<Insurance> getInsuranceOptions(List<Integer> allowedInsuranceIds) {
		List<Insurance> insurances = new ArrayList<Insurance>();
		for (Insurance insurance : InsuranceUtil.getInsurances(true)) {
			if (insurance != null && (allowedInsuranceIds == null || allowedInsuranceIds.contains(insurance.getInsuranceId()))) {
				insurances.add(insurance);
			}
		}
		Collections.sort(insurances, new Comparator<Insurance>() {
			@Override
			public int compare(Insurance left, Insurance right) {
				String leftName = left == null || left.getName() == null ? "" : left.getName();
				String rightName = right == null || right.getName() == null ? "" : right.getName();
				return leftName.compareToIgnoreCase(rightName);
			}
		});
		return insurances;
	}

	private Integer parseInteger(String value) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			return null;
		}
		try {
			return Integer.valueOf(trimmed);
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private void appendQueryParam(StringBuilder builder, String name, String value) {
		String trimmed = trimToNull(value);
		if (trimmed == null) {
			return;
		}
		if (builder.length() > 0) {
			builder.append("&");
		}
		try {
			builder.append(name).append("=").append(URLEncoder.encode(trimmed, "UTF-8"));
		} catch (Exception e) {
			builder.append(name).append("=").append(trimmed);
		}
	}

	private Date parseDate(String value) {
		try {
			return new SimpleDateFormat(DATE_PATTERN).parse(value);
		}
		catch (ParseException e) {
			return null;
		}
	}

	private Date getStartOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}

	private Date getEndOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);
		return calendar.getTime();
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() == 0 ? null : trimmed;
	}

	private String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.isBlank(value) ? defaultValue : value.trim();
	}

	/**
	 * The totals of the verification of a bill: how much is accepted, rejected or still to decide, and how many items
	 * still need a decision at the step the bill is at
	 */
	public static class VerificationSummary {

		private final BillVerificationStage stage;
		private int itemCount;
		private int awaitingCount;
		private int rejectedCount;
		private BigDecimal acceptedAmount = BigDecimal.ZERO;
		private BigDecimal rejectedAmount = BigDecimal.ZERO;
		private BigDecimal pendingAmount = BigDecimal.ZERO;

		public VerificationSummary(BillVerificationStage stage, List<Consommation> consommations,
		                           Map<Integer, BillItemVerification> items) {
			this.stage = stage;
			for (Consommation consommation : consommations == null ? Collections.<Consommation>emptyList() : consommations) {
				if (consommation == null || Boolean.TRUE.equals(consommation.getVoided()) || consommation.getBillItems() == null) {
					continue;
				}
				for (PatientServiceBill item : consommation.getBillItems()) {
					if (item == null || Boolean.TRUE.equals(item.getVoided())) {
						continue;
					}
					BillItemVerification verification = items.get(item.getPatientServiceBillId());
					BillItemVerificationStatus status = verification == null ? BillItemVerificationStatus.PENDING : verification.getStatus();
					BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
					itemCount++;
					if (stage.isAwaitingDecision(status)) {
						awaitingCount++;
					}
					switch (status.getOutcome()) {
						case ACCEPTED:
							acceptedAmount = acceptedAmount.add(amount);
							break;
						case REJECTED:
							rejectedAmount = rejectedAmount.add(amount);
							rejectedCount++;
							break;
						default:
							pendingAmount = pendingAmount.add(amount);
					}
				}
			}
		}

		public BillVerificationStage getStage() {
			return stage;
		}

		public int getItemCount() {
			return itemCount;
		}

		public int getAwaitingCount() {
			return awaitingCount;
		}

		public int getRejectedCount() {
			return rejectedCount;
		}

		public BigDecimal getAcceptedAmount() {
			return acceptedAmount;
		}

		public BigDecimal getRejectedAmount() {
			return rejectedAmount;
		}

		public BigDecimal getPendingAmount() {
			return pendingAmount;
		}

		/**
		 * @return the class of the chip of the step: grey before the insurer, blue with the insurer, orange while the
		 * facility answers, green or orange once closed depending on whether anything was rejected
		 */
		public String getChipClass() {
			switch (stage) {
				case FACILITY_REVIEW:
					return "NOT_SENT";
				case INSURER_REVIEW:
				case FINAL_REVIEW:
					return "QUEUED";
				case FACILITY_RESPONSE:
					return "PROCESSING";
				default:
					return rejectedCount > 0 ? "PROCESSING" : "CONFIRMED";
			}
		}
	}

	public static class PatientJourneyLogRow {
		private final Integer encounterId;
		private final String encounterType;
		private final String formName;
		private final String locationName;
		private final String action;
		private final String username;
		private final Date encounterDatetime;
		private final Date actionDatetime;

		public PatientJourneyLogRow(Integer encounterId, String encounterType, String formName, String locationName,
		                            String action, String username, Date encounterDatetime, Date actionDatetime) {
			this.encounterId = encounterId;
			this.encounterType = encounterType;
			this.formName = formName;
			this.locationName = locationName;
			this.action = action;
			this.username = username;
			this.encounterDatetime = encounterDatetime;
			this.actionDatetime = actionDatetime;
		}

		public Integer getEncounterId() {
			return encounterId;
		}

		public String getEncounterType() {
			return encounterType;
		}

		public String getFormName() {
			return formName;
		}

		public String getLocationName() {
			return locationName;
		}

		public String getAction() {
			return action;
		}

		public String getUsername() {
			return username;
		}

		public Date getEncounterDatetime() {
			return encounterDatetime;
		}

		public Date getActionDatetime() {
			return actionDatetime;
		}
	}

	public static class RhipVoucherSubmissionRow {
		private final GlobalBill globalBill;
		private final RhipVoucherSubmission latestSubmission;
		private final RhipVoucherSubmission successfulSubmission;
		private final List<Consommation> consommations;

		public RhipVoucherSubmissionRow(GlobalBill globalBill, RhipVoucherSubmission latestSubmission,
		                                RhipVoucherSubmission successfulSubmission, List<Consommation> consommations) {
			this.globalBill = globalBill;
			this.latestSubmission = latestSubmission;
			this.successfulSubmission = successfulSubmission;
			this.consommations = consommations == null ? Collections.<Consommation>emptyList() : consommations;
		}

		public GlobalBill getGlobalBill() {
			return globalBill;
		}

		public RhipVoucherSubmission getLatestSubmission() {
			return latestSubmission;
		}

		public String getEffectiveStatus() {
			if (latestSubmission != null && RhipVoucherSubmission.STATUS_CONFIRMED.equals(latestSubmission.getStatus())) {
				return RhipVoucherSubmission.STATUS_CONFIRMED;
			}
			if (successfulSubmission != null || StringUtils.isNotBlank(globalBill.getRhipVoucherCode())
					|| StringUtils.isNotBlank(globalBill.getRhipVoucherReferenceNumber())) {
				return RhipVoucherSubmission.STATUS_SENT;
			}
			return latestSubmission == null ? RhipVoucherSubmission.STATUS_NOT_SENT : latestSubmission.getStatus();
		}

		public String getDisplayStatus() {
			String status = getEffectiveStatus();
			if (RhipVoucherSubmission.STATUS_SENT.equals(status)) {
				return isMmiInsurance() ? "Created" : "Sent";
			}
			if (RhipVoucherSubmission.STATUS_CONFIRMED.equals(status)) {
				return "Confirmed";
			}
			if (RhipVoucherSubmission.STATUS_FAILED.equals(status)) {
				return "Failed";
			}
			if (RhipVoucherSubmission.STATUS_PROCESSING.equals(status)) {
				return "Processing";
			}
			return "Not Sent";
		}

		public Date getLastSubmissionDate() {
			return latestSubmission == null ? null : latestSubmission.getDateSubmitted();
		}

		public String getSubmissionMessage() {
			return latestSubmission == null ? "" : StringUtils.defaultIfBlank(latestSubmission.getErrorMessage(), "");
		}

		public boolean isAllPatientBillsPaid() {
			return getPatientBillCount() > 0 && getUnpaidPatientBillCount() == 0;
		}

		public int getPatientBillCount() {
			int count = 0;
			for (Consommation consommation : consommations) {
				if (hasActiveBillItem(consommation) && consommation.getPatientBill() != null) {
					count++;
				}
			}
			return count;
		}

		public int getUnpaidPatientBillCount() {
			int count = 0;
			for (Consommation consommation : consommations) {
				if (hasActiveBillItem(consommation) && consommation.getPatientBill() != null
						&& !consommation.getPatientBill().isPaid()) {
					count++;
				}
			}
			return count;
		}

		private boolean hasActiveBillItem(Consommation consommation) {
			if (consommation == null || consommation.getBillItems() == null) {
				return false;
			}
			for (PatientServiceBill item : consommation.getBillItems()) {
				if (item != null && !Boolean.TRUE.equals(item.getVoided())) {
					return true;
				}
			}
			return false;
		}

		public String getPaymentStatusLabel() {
			if (getPatientBillCount() == 0) {
				return "No patient bills";
			}
			return isAllPatientBillsPaid() ? "Paid" : getUnpaidPatientBillCount() + " unpaid";
		}

		public String getPaymentStatusClass() {
			if (getPatientBillCount() == 0) {
				return "NOT_SENT";
			}
			return isAllPatientBillsPaid() ? "CONFIRMED" : "FAILED";
		}

		public String getVoucherReference() {
			if (successfulSubmission != null) {
				return StringUtils.defaultIfBlank(successfulSubmission.getVoucherReferenceNumber(), successfulSubmission.getVoucherCode());
			}
			return StringUtils.defaultIfBlank(globalBill.getRhipVoucherReferenceNumber(), globalBill.getRhipVoucherCode());
		}

		public String getPatientName() {
			Patient patient = getPatient();
			return patient == null || patient.getPersonName() == null ? "" : patient.getPersonName().toString();
		}

		public String getPatientIdentifier() {
			Patient patient = getPatient();
			if (patient == null || patient.getIdentifiers() == null || patient.getIdentifiers().isEmpty()) {
				return "";
			}
			PatientIdentifier identifier = patient.getPatientIdentifier();
			return identifier == null ? "" : identifier.getIdentifier();
		}

		public String getInsuranceName() {
			Insurance insurance = globalBill.getInsurance();
			return insurance == null ? "" : insurance.getName();
		}

		public String getInsuranceCardNumber() {
			InsurancePolicy policy = getInsurancePolicy();
			return policy == null ? "" : policy.getInsuranceCardNo();
		}

		public BigDecimal getPatientContribution() {
			BigDecimal amount = globalBill.getGlobalAmount() == null ? BigDecimal.ZERO : globalBill.getGlobalAmount();
			InsurancePolicy policy = getInsurancePolicy();
			if (policy == null || policy.getInsurance() == null || policy.getInsurance().getCurrentRate() == null
					|| policy.getInsurance().getCurrentRate().getRate() == null) {
				return BigDecimal.ZERO;
			}
			BigDecimal patientRate = BigDecimal.valueOf(100).subtract(
					BigDecimal.valueOf(policy.getInsurance().getCurrentRate().getRate()));
			return amount.multiply(patientRate).divide(BigDecimal.valueOf(100));
		}

		public BigDecimal getInsuranceContribution() {
			BigDecimal amount = globalBill.getGlobalAmount() == null ? BigDecimal.ZERO : globalBill.getGlobalAmount();
			return amount.subtract(getPatientContribution());
		}

		private Patient getPatient() {
			InsurancePolicy policy = getInsurancePolicy();
			return policy == null ? null : policy.getOwner();
		}

		private InsurancePolicy getInsurancePolicy() {
			Admission admission = globalBill == null ? null : globalBill.getAdmission();
			return admission == null ? null : admission.getInsurancePolicy();
		}

		public boolean isMmiInsurance() {
			Insurance insurance = globalBill == null ? null : globalBill.getInsurance();
			if (insurance == null) {
				InsurancePolicy policy = getInsurancePolicy();
				insurance = policy == null ? null : policy.getInsurance();
			}
			if (insurance == null) {
				return false;
			}
			return "MMI".equalsIgnoreCase(StringUtils.trimToEmpty(insurance.getCategory()))
					|| StringUtils.containsIgnoreCase(StringUtils.trimToEmpty(insurance.getName()), "MMI");
		}
	}
}
