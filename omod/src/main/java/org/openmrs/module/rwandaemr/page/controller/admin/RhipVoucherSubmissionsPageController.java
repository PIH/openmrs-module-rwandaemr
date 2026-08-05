package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.mohbilling.businesslogic.ConsommationUtil;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.integration.insurance.RhipVoucherService;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmissionSearchCriteria;
import org.openmrs.module.mohbilling.service.BillingService;
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
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RhipVoucherSubmissionsPageController {

	public static final String PRIVILEGE_VIEW = "Billing RHIP Voucher - View Submission Page";
	public static final String PRIVILEGE_SEND = "Billing RHIP Voucher - Send";
	public static final String PRIVILEGE_RETRY = "Billing RHIP Voucher - Retry";
	public static final String PRIVILEGE_VIEW_HISTORY = "Billing RHIP Voucher - View History";
	private static final String DATE_PATTERN = "yyyy-MM-dd";
	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 100;

	public String get(PageModel model,
	                  UiUtils ui,
	                  UiSessionContext sessionContext,
	                  @SpringBean("mohBillingService") BillingService billingService,
	                  @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDateParam,
	                  @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDateParam,
	                  @RequestParam(value = "dischargeDate", required = false) String dischargeDateParam,
	                  @RequestParam(value = "status", required = false) String statusParam,
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
		String status = defaultIfBlank(statusParam, RhipVoucherSubmission.STATUS_NOT_SENT).toUpperCase();
		String query = trimToNull(queryParam);
		String sortBy = defaultIfBlank(sortByParam, "dischargeDate");
		String sortDirection = defaultIfBlank(sortDirectionParam, "desc");
		int page = pageParam == null || pageParam <= 0 ? 1 : pageParam;
		int pageSize = pageSizeParam == null || pageSizeParam <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSizeParam, MAX_PAGE_SIZE);

		RhipVoucherSubmissionSearchCriteria criteria = new RhipVoucherSubmissionSearchCriteria();
		criteria.setDischargeStartDate(getStartOfDay(dischargeStartDate));
		criteria.setDischargeEndDate(getEndOfDay(dischargeEndDate));
		criteria.setStatus(status);
		criteria.setQuery(query);
		criteria.setSortBy(sortBy);
		criteria.setSortDirection(sortDirection);

		int totalCount = billingService.countRhipVoucherSubmissionGlobalBills(criteria);
		int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
		if (page > totalPages) {
			page = totalPages;
		}
		List<GlobalBill> globalBills = billingService.getRhipVoucherSubmissionGlobalBills(criteria, (page - 1) * pageSize, pageSize);

		model.addAttribute("rows", buildRows(globalBills, billingService));
		model.addAttribute("historyByGlobalBillId", buildHistoryByGlobalBillId(globalBills, billingService));
		model.addAttribute("consommationsByGlobalBillId", buildConsommationsByGlobalBillId(globalBills));
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
		model.addAttribute("filterQueryString", buildFilterQueryString(dischargeStartDateText, dischargeEndDateText, status, query, sortBy, sortDirection, pageSize));
		model.addAttribute("canSend", Context.hasPrivilege(PRIVILEGE_SEND));
		model.addAttribute("canRetry", Context.hasPrivilege(PRIVILEGE_RETRY));
		model.addAttribute("canViewHistory", Context.hasPrivilege(PRIVILEGE_VIEW_HISTORY));
		return null;
	}

	public String post(UiUtils ui,
	                   UiSessionContext sessionContext,
	                   @SpringBean("rhipVoucherService") RhipVoucherService voucherService,
	                   @RequestParam("globalBillId") Integer globalBillId,
	                   @RequestParam(value = "action", required = false) String action,
	                   @RequestParam(value = "dischargeStartDate", required = false) String dischargeStartDate,
	                   @RequestParam(value = "dischargeEndDate", required = false) String dischargeEndDate,
	                   @RequestParam(value = "dischargeDate", required = false) String dischargeDate,
	                   @RequestParam(value = "status", required = false) String status,
	                   @RequestParam(value = "query", required = false) String query,
	                   @RequestParam(value = "sortBy", required = false) String sortBy,
	                   @RequestParam(value = "sortDirection", required = false) String sortDirection,
	                   @RequestParam(value = "page", required = false) Integer page,
	                   @RequestParam(value = "pageSize", required = false) Integer pageSize) {
		if ("retry".equalsIgnoreCase(action) && !Context.hasPrivilege(PRIVILEGE_RETRY)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to retry RHIP vouchers.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, status, query, sortBy, sortDirection, page, pageSize);
		}
		if (!"retry".equalsIgnoreCase(action) && !Context.hasPrivilege(PRIVILEGE_SEND)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to send RHIP vouchers.");
			return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, status, query, sortBy, sortDirection, page, pageSize);
		}

		GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
		RhipVoucherSubmission submission = voucherService.submitVoucherForGlobalBillWithAudit(globalBill);
		if (submission != null && RhipVoucherSubmission.STATUS_SENT.equals(submission.getStatus())) {
			String reference = StringUtils.defaultIfBlank(submission.getVoucherReferenceNumber(), submission.getVoucherCode());
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
					"RHIP voucher submitted successfully" + (StringUtils.isBlank(reference) ? "." : ". Reference: " + reference));
		} else {
			String error = submission == null ? "RHIP voucher submission failed." : submission.getErrorMessage();
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					StringUtils.defaultIfBlank(error, "RHIP voucher submission failed."));
		}
		return redirect(ui, defaultIfBlank(dischargeStartDate, dischargeDate), dischargeEndDate, status, query, sortBy, sortDirection, page, pageSize);
	}

	private List<RhipVoucherSubmissionRow> buildRows(List<GlobalBill> globalBills, BillingService billingService) {
		List<RhipVoucherSubmissionRow> rows = new ArrayList<RhipVoucherSubmissionRow>();
		for (GlobalBill globalBill : globalBills == null ? Collections.<GlobalBill>emptyList() : globalBills) {
			RhipVoucherSubmission latest = billingService.getLatestRhipVoucherSubmission(globalBill);
			RhipVoucherSubmission successful = billingService.getSuccessfulRhipVoucherSubmission(globalBill);
			rows.add(new RhipVoucherSubmissionRow(globalBill, latest, successful));
		}
		return rows;
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

	private String redirect(UiUtils ui, String dischargeStartDate, String dischargeEndDate, String status, String query, String sortBy,
	                        String sortDirection, Integer page, Integer pageSize) {
		StringBuilder params = new StringBuilder();
		appendQueryParam(params, "dischargeStartDate", dischargeStartDate);
		appendQueryParam(params, "dischargeEndDate", dischargeEndDate);
		appendQueryParam(params, "status", status);
		appendQueryParam(params, "query", query);
		appendQueryParam(params, "sortBy", sortBy);
		appendQueryParam(params, "sortDirection", sortDirection);
		appendQueryParam(params, "page", page == null ? null : page.toString());
		appendQueryParam(params, "pageSize", pageSize == null ? null : pageSize.toString());
		String pageLink = ui.pageLink("rwandaemr", "admin/rhipVoucherSubmissions");
		return "redirect:" + pageLink + (params.length() == 0 ? "" : "?" + params);
	}

	private String buildFilterQueryString(String dischargeStartDate, String dischargeEndDate, String status, String query, String sortBy,
	                                      String sortDirection, int pageSize) {
		StringBuilder ret = new StringBuilder();
		appendQueryParam(ret, "dischargeStartDate", dischargeStartDate);
		appendQueryParam(ret, "dischargeEndDate", dischargeEndDate);
		appendQueryParam(ret, "status", status);
		appendQueryParam(ret, "query", query);
		appendQueryParam(ret, "sortBy", sortBy);
		appendQueryParam(ret, "sortDirection", sortDirection);
		appendQueryParam(ret, "pageSize", String.valueOf(pageSize));
		return ret.toString();
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

		public RhipVoucherSubmissionRow(GlobalBill globalBill, RhipVoucherSubmission latestSubmission,
		                                RhipVoucherSubmission successfulSubmission) {
			this.globalBill = globalBill;
			this.latestSubmission = latestSubmission;
			this.successfulSubmission = successfulSubmission;
		}

		public GlobalBill getGlobalBill() {
			return globalBill;
		}

		public RhipVoucherSubmission getLatestSubmission() {
			return latestSubmission;
		}

		public String getEffectiveStatus() {
			if (successfulSubmission != null || StringUtils.isNotBlank(globalBill.getRhipVoucherCode())
					|| StringUtils.isNotBlank(globalBill.getRhipVoucherReferenceNumber())) {
				return RhipVoucherSubmission.STATUS_SENT;
			}
			return latestSubmission == null ? RhipVoucherSubmission.STATUS_NOT_SENT : latestSubmission.getStatus();
		}

		public String getDisplayStatus() {
			String status = getEffectiveStatus();
			if (RhipVoucherSubmission.STATUS_SENT.equals(status)) {
				return "Sent";
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
	}
}
