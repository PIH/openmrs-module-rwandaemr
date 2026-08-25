package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
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
import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmissionSearchCriteria;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.paymentclearance.PaymentClearanceLogEntry;
import org.openmrs.module.rwandaemr.paymentclearance.PaymentClearanceLogRepository;
import org.openmrs.module.rwandaemr.paymentclearance.PaymentClearanceResult;
import org.openmrs.module.rwandaemr.paymentclearance.PaymentClearanceService;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
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

	public static final String PRIVILEGE_VIEW = "Billing RHIP Voucher - View Submission Page";
	public static final String PRIVILEGE_SEND = "Billing RHIP Voucher - Send";
	public static final String PRIVILEGE_CONFIRM = "Billing RHIP Voucher - Confirm";
	public static final String PRIVILEGE_RETRY = "Billing RHIP Voucher - Retry";
	public static final String PRIVILEGE_VIEW_HISTORY = "Billing RHIP Voucher - View History";
	public static final String PRIVILEGE_CLEARANCE = "Billing Payment Clearance - Send SMS";
	private static final String PRIVILEGE_BILLING_ADMIN = "Billing Configuration - View Billing Admin";
	private static final String DATE_PATTERN = "yyyy-MM-dd";
	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 100;

	public String get(PageModel model,
	                  UiUtils ui,
	                  UiSessionContext sessionContext,
	                  @SpringBean("mohBillingService") BillingService billingService,
	                  @SpringBean PaymentClearanceLogRepository paymentClearanceLogRepository,
	                  @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDateParam,
	                  @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDateParam,
	                  @RequestParam(value = "dischargeDate", required = false) String dischargeDateParam,
	                  @RequestParam(value = "status", required = false) String statusParam,
	                  @RequestParam(value = "insuranceId", required = false) String insuranceIdParam,
	                  @RequestParam(value = "query", required = false) String queryParam,
	                  @RequestParam(value = "sortBy", required = false) String sortByParam,
	                  @RequestParam(value = "sortDirection", required = false) String sortDirectionParam,
	                  @RequestParam(value = "page", required = false) Integer pageParam,
	                  @RequestParam(value = "pageSize", required = false) Integer pageSizeParam) {
		if (!Context.hasPrivilege(PRIVILEGE_VIEW) && !Context.hasPrivilege("Billing Configuration - View Billing Admin")) {
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
		Integer selectedInsuranceId = parseInteger(insuranceIdParam);
		String query = trimToNull(queryParam);
		String sortBy = defaultIfBlank(sortByParam, "dischargeDate");
		String sortDirection = defaultIfBlank(sortDirectionParam, "desc");
		int page = pageParam == null || pageParam <= 0 ? 1 : pageParam;
		int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSizeParam, MAX_PAGE_SIZE);

		RhipVoucherSubmissionSearchCriteria criteria = new RhipVoucherSubmissionSearchCriteria();
		criteria.setDischargeStartDate(getStartOfDay(dischargeStartDate));
		criteria.setDischargeEndDate(getEndOfDay(dischargeEndDate));
		criteria.setStatus(status);
		criteria.setInsuranceId(selectedInsuranceId);
		criteria.setQuery(query);
		criteria.setSortBy(sortBy);
		criteria.setSortDirection(sortDirection);

		int totalCount = billingService.countRhipVoucherSubmissionGlobalBills(criteria);
		int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
		if (page > totalPages) {
			page = totalPages;
		}
		List<GlobalBill> globalBills = billingService.getRhipVoucherSubmissionGlobalBills(criteria,
				(page - 1) * pageSize, pageSize);

		Map<Integer, List<Consommation>> consommationsByGlobalBillId = buildConsommationsByGlobalBillId(globalBills);
		model.addAttribute("rows", buildRows(globalBills, billingService, consommationsByGlobalBillId,
				buildLatestClearanceByGlobalBillId(globalBills, paymentClearanceLogRepository)));
		model.addAttribute("historyByGlobalBillId", buildHistoryByGlobalBillId(globalBills, billingService));
		model.addAttribute("consommationsByGlobalBillId", consommationsByGlobalBillId);
		model.addAttribute("insuranceOptions", getInsuranceOptions());
		model.addAttribute("insuranceId", selectedInsuranceId == null ? "" : selectedInsuranceId.toString());
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
		model.addAttribute("filterQueryString", buildFilterQueryString(dischargeStartDateText, dischargeEndDateText,
				selectedInsuranceId == null ? null : selectedInsuranceId.toString(), status, query, sortBy,
				sortDirection, pageSize));
		model.addAttribute("canSend", Context.hasPrivilege(PRIVILEGE_SEND));
		model.addAttribute("canConfirm", canConfirmVoucher());
		model.addAttribute("canRetry", Context.hasPrivilege(PRIVILEGE_RETRY));
		model.addAttribute("canViewHistory", Context.hasPrivilege(PRIVILEGE_VIEW_HISTORY));
		model.addAttribute("canSendClearance", canSendClearance());
		return null;
	}

	public String post(UiUtils ui,
	                   UiSessionContext sessionContext,
	                   @SpringBean("rhipVoucherService") RhipVoucherService voucherService,
	                   @SpringBean("paymentClearanceService") PaymentClearanceService paymentClearanceService,
	                   @RequestParam("globalBillId") Integer globalBillId,
	                   @RequestParam(value = "action", required = false) String action,
	                   @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDate,
	                   @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDate,
	                   @RequestParam(value = "dischargeDate", required = false) String dischargeDate,
	                   @RequestParam(value = "status", required = false) String status,
	                   @RequestParam(value = "insuranceId", required = false) String insuranceId,
	                   @RequestParam(value = "query", required = false) String query,
	                   @RequestParam(value = "sortBy", required = false) String sortBy,
	                   @RequestParam(value = "sortDirection", required = false) String sortDirection,
	                   @RequestParam(value = "page", required = false) Integer page,
	                   @RequestParam(value = "pageSize", required = false) Integer pageSize) {
		boolean confirmAction = "confirm".equalsIgnoreCase(action);
		boolean retryAction = "retry".equalsIgnoreCase(action);
		boolean clearanceAction = "clearance".equalsIgnoreCase(action);
		if (clearanceAction && !canSendClearance()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to send payment clearance SMS.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
		if (confirmAction && !canConfirmVoucher()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to confirm RHIP vouchers.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
		if (retryAction && !Context.hasPrivilege(PRIVILEGE_RETRY)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to retry RHIP vouchers.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
		if (!confirmAction && !retryAction && !clearanceAction && !Context.hasPrivilege(PRIVILEGE_SEND)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to send RHIP vouchers.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}

		GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
		if (clearanceAction) {
			try {
				PaymentClearanceResult result = paymentClearanceService.sendClearanceSms(globalBill);
				if (result.isSuccess()) {
					sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
							"Payment clearance SMS queued successfully to " + result.getPhoneNumber() + ".");
				}
				else {
					sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
							StringUtils.defaultIfBlank(result.getMessage(), "Payment clearance SMS failed."));
				}
			}
			catch (Exception e) {
				sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
						StringUtils.defaultIfBlank(e.getMessage(), "Payment clearance SMS failed."));
			}
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId,
					status, query, sortBy, sortDirection, page, pageSize);
		}
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
		} else {
			String defaultError = confirmAction ? "RHIP voucher confirmation failed." : "RHIP voucher submission failed.";
			String error = submission == null ? defaultError : submission.getErrorMessage();
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					StringUtils.defaultIfBlank(error, defaultError));
		}
		return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, insuranceId, status,
				query, sortBy, sortDirection, page, pageSize);
	}

	private boolean canConfirmVoucher() {
		return Context.hasPrivilege(PRIVILEGE_CONFIRM)
				|| Context.hasPrivilege(PRIVILEGE_SEND)
				|| Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN);
	}

	private boolean canSendClearance() {
		return Context.hasPrivilege(PRIVILEGE_CLEARANCE) || Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN);
	}

	private List<RhipVoucherSubmissionRow> buildRows(List<GlobalBill> globalBills, BillingService billingService,
	                                                 Map<Integer, List<Consommation>> consommationsByGlobalBillId,
	                                                 Map<Integer, PaymentClearanceLogEntry> clearanceByGlobalBillId) {
		List<RhipVoucherSubmissionRow> rows = new ArrayList<RhipVoucherSubmissionRow>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			RhipVoucherSubmission latest = billingService.getLatestRhipVoucherSubmission(globalBill);
			RhipVoucherSubmission successful = billingService.getSuccessfulRhipVoucherSubmission(globalBill);
			Integer globalBillId = globalBill == null ? null : globalBill.getGlobalBillId();
			List<Consommation> consommations = globalBillId == null ? Collections.<Consommation>emptyList()
					: consommationsByGlobalBillId.get(globalBillId);
			PaymentClearanceLogEntry clearanceLog = globalBillId == null ? null : clearanceByGlobalBillId.get(globalBillId);
			rows.add(new RhipVoucherSubmissionRow(globalBill, latest, successful, consommations, clearanceLog));
		}
		return rows;
	}

	private Map<Integer, PaymentClearanceLogEntry> buildLatestClearanceByGlobalBillId(List<GlobalBill> globalBills,
	                                                                                 PaymentClearanceLogRepository repository) {
		List<Integer> globalBillIds = new ArrayList<Integer>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			if (globalBill != null && globalBill.getGlobalBillId() != null) {
				globalBillIds.add(globalBill.getGlobalBillId());
			}
		}
		return repository.getLatestByGlobalBillIds(globalBillIds);
	}

	private Map<Integer, List<RhipVoucherSubmission>> buildHistoryByGlobalBillId(List<GlobalBill> globalBills, BillingService billingService) {
		Map<Integer, List<RhipVoucherSubmission>> ret = new LinkedHashMap<Integer, List<RhipVoucherSubmission>>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			ret.put(globalBill.getGlobalBillId(), billingService.getRhipVoucherSubmissionsByGlobalBill(globalBill));
		}
		return ret;
	}

	private Map<Integer, List<Consommation>> buildConsommationsByGlobalBillId(List<GlobalBill> globalBills) {
		Map<Integer, List<Consommation>> ret = new HashMap<Integer, List<Consommation>>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			ret.put(globalBill.getGlobalBillId(), ConsommationUtil.getConsommationsByGlobalBill(globalBill));
		}
		return ret;
	}

	private String redirect(UiUtils ui, String dischargeStartDate, String dischargeEndDate, String insuranceId,
	                        String status, String query, String sortBy, String sortDirection, Integer page,
	                        Integer pageSize) {
		StringBuilder params = new StringBuilder();
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

	private String buildFilterQueryString(String dischargeStartDate, String dischargeEndDate, String insuranceId,
	                                      String status, String query, String sortBy, String sortDirection,
	                                      int pageSize) {
		StringBuilder ret = new StringBuilder();
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

	private List<Insurance> getInsuranceOptions() {
		List<Insurance> insurances = new ArrayList<Insurance>(InsuranceUtil.getInsurances(true));
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

	public static class RhipVoucherSubmissionRow {
		private final GlobalBill globalBill;
		private final RhipVoucherSubmission latestSubmission;
		private final RhipVoucherSubmission successfulSubmission;
		private final List<Consommation> consommations;
		private final PaymentClearanceLogEntry latestClearanceLog;

		public RhipVoucherSubmissionRow(GlobalBill globalBill, RhipVoucherSubmission latestSubmission,
		                                RhipVoucherSubmission successfulSubmission, List<Consommation> consommations,
		                                PaymentClearanceLogEntry latestClearanceLog) {
			this.globalBill = globalBill;
			this.latestSubmission = latestSubmission;
			this.successfulSubmission = successfulSubmission;
			this.consommations = consommations == null ? Collections.<Consommation>emptyList() : consommations;
			this.latestClearanceLog = latestClearanceLog;
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
				if (consommation != null && consommation.getPatientBill() != null) {
					count++;
				}
			}
			return count;
		}

		public int getUnpaidPatientBillCount() {
			int count = 0;
			for (Consommation consommation : consommations) {
				if (consommation != null && consommation.getPatientBill() != null && !consommation.getPatientBill().isPaid()) {
					count++;
				}
			}
			return count;
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

		public String getClearanceStatus() {
			return latestClearanceLog == null ? "" : StringUtils.defaultIfBlank(latestClearanceLog.getStatus(), "");
		}

		public String getClearanceMessage() {
			if (latestClearanceLog == null) {
				return "";
			}
			if (StringUtils.isNotBlank(latestClearanceLog.getErrorMessage())) {
				return latestClearanceLog.getErrorMessage();
			}
			if (StringUtils.isNotBlank(latestClearanceLog.getPhoneNumber())) {
				return "SMS " + StringUtils.defaultIfBlank(latestClearanceLog.getStatus(), "sent") + " to "
						+ latestClearanceLog.getPhoneNumber();
			}
			return StringUtils.defaultIfBlank(latestClearanceLog.getStatus(), "");
		}

		public Date getClearanceDate() {
			return latestClearanceLog == null ? null : latestClearanceLog.getDateCreated();
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
