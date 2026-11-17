package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.sms.SmsLogRepository;
import org.openmrs.module.rwandaemr.sms.SmsLogSearchCriteria;
import org.openmrs.module.rwandaemr.sms.SmsLogSearchResult;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Lists the SMS sent by RwandaEMR (lab result notifications and payment clearance), with filters and paging
 */
@Controller
public class SmsLogsPageController {

	private static final String PRIVILEGE_SYSTEM_ADMINISTRATION = "App: coreapps.systemAdministration";
	private static final String PRIVILEGE_BILLING_ADMIN = "Billing Configuration - View Billing Admin";
	private static final String DATE_PATTERN = "yyyy-MM-dd";
	private static final int DEFAULT_PAGE_SIZE = 25;
	private static final int MAX_PAGE_SIZE = 200;

	public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
	                  @SpringBean("smsLogRepository") SmsLogRepository smsLogRepository,
	                  @RequestParam(value = "startDate", required = false) String startDateParam,
	                  @RequestParam(value = "endDate", required = false) String endDateParam,
	                  @RequestParam(value = "sourceType", required = false) String sourceTypeParam,
	                  @RequestParam(value = "status", required = false) String statusParam,
	                  @RequestParam(value = "responseCode", required = false) String responseCodeParam,
	                  @RequestParam(value = "query", required = false) String queryParam,
	                  @RequestParam(value = "page", required = false) Integer pageParam,
	                  @RequestParam(value = "pageSize", required = false) Integer pageSizeParam) {

		if (!canViewSmsLogs()) {
			sessionContext.getSession().setAttribute("emr.errorMessage", "You do not have permission to view SMS logs.");
			return "redirect:" + ui.pageLink("coreapps", "systemadministration/systemAdministration");
		}

		Date startDate = parseDate(startDateParam);
		Date endDate = parseDate(endDateParam);
		if (endDate != null) {
			endDate = endOfDay(endDate);
		}
		if (startDate != null && endDate != null && endDate.before(startDate)) {
			Date originalStartDate = startDate;
			String originalStartDateParam = startDateParam;
			startDate = startOfDay(endDate);
			startDateParam = endDateParam;
			endDate = endOfDay(originalStartDate);
			endDateParam = originalStartDateParam;
		}

		String sourceType = normalizeSourceType(sourceTypeParam);
		String status = normalizeStatus(statusParam);
		Integer responseCode = parseInteger(responseCodeParam);
		String query = StringUtils.trimToNull(queryParam);
		int page = (pageParam == null || pageParam <= 0) ? 1 : pageParam;
		int pageSize = (pageSizeParam == null || pageSizeParam <= 0) ? DEFAULT_PAGE_SIZE : Math.min(pageSizeParam, MAX_PAGE_SIZE);

		SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
		criteria.setStartDate(startDate == null ? null : startOfDay(startDate));
		criteria.setEndDate(endDate);
		criteria.setSourceType(sourceType);
		criteria.setStatus(status);
		criteria.setResponseCode(responseCode);
		criteria.setQuery(query);

		SmsLogSearchResult result = smsLogRepository.search(criteria, page, pageSize);
		int totalPages = result.getTotalCount() == 0 ? 1 : (int) Math.ceil((double) result.getTotalCount() / pageSize);
		if (page > totalPages) {
			page = totalPages;
			result = smsLogRepository.search(criteria, page, pageSize);
		}

		model.addAttribute("logs", result.getLogs());
		model.addAttribute("totalCount", result.getTotalCount());
		model.addAttribute("page", page);
		model.addAttribute("pageSize", pageSize);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("hasPreviousPage", page > 1);
		model.addAttribute("hasNextPage", page < totalPages);
		model.addAttribute("previousPage", Math.max(page - 1, 1));
		model.addAttribute("nextPage", Math.min(page + 1, totalPages));
		model.addAttribute("startDate", StringUtils.defaultString(startDateParam));
		model.addAttribute("endDate", StringUtils.defaultString(endDateParam));
		model.addAttribute("sourceType", sourceType);
		model.addAttribute("status", status);
		model.addAttribute("responseCode", responseCode == null ? "" : responseCode.toString());
		model.addAttribute("query", StringUtils.defaultString(query));
		model.addAttribute("filterQueryString",
				buildFilterQueryString(startDateParam, endDateParam, sourceType, status, responseCode, query, pageSize));
		return null;
	}

	private boolean canViewSmsLogs() {
		return Context.hasPrivilege(PRIVILEGE_SYSTEM_ADMINISTRATION) || Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN);
	}

	private String normalizeSourceType(String sourceType) {
		String normalized = StringUtils.defaultIfBlank(sourceType, SmsLogRepository.SOURCE_ALL).trim().toUpperCase();
		if (SmsLogRepository.SOURCE_LAB_RESULT.equals(normalized) || SmsLogRepository.SOURCE_PAYMENT_CLEARANCE.equals(normalized)) {
			return normalized;
		}
		return SmsLogRepository.SOURCE_ALL;
	}

	private String normalizeStatus(String status) {
		return StringUtils.defaultIfBlank(status, SmsLogRepository.STATUS_ALL).trim().toUpperCase();
	}

	private Date parseDate(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
			format.setLenient(false);
			return format.parse(value.trim());
		}
		catch (ParseException e) {
			return null;
		}
	}

	private Date startOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}

	private Date endOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 23);
		calendar.set(Calendar.MINUTE, 59);
		calendar.set(Calendar.SECOND, 59);
		calendar.set(Calendar.MILLISECOND, 999);
		return calendar.getTime();
	}

	private Integer parseInteger(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private String buildFilterQueryString(String startDate, String endDate, String sourceType, String status,
	                                      Integer responseCode, String query, int pageSize) {
		List<String> params = new ArrayList<>();
		addParam(params, "startDate", startDate);
		addParam(params, "endDate", endDate);
		addParam(params, "sourceType", sourceType);
		addParam(params, "status", status);
		addParam(params, "responseCode", responseCode == null ? "" : responseCode.toString());
		addParam(params, "query", query);
		addParam(params, "pageSize", String.valueOf(pageSize));
		return StringUtils.join(params, "&");
	}

	private void addParam(List<String> params, String name, String value) {
		params.add(encode(name) + "=" + encode(StringUtils.defaultString(value)));
	}

	private String encode(String value) {
		try {
			return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8");
		}
		catch (UnsupportedEncodingException e) {
			throw new IllegalStateException("UTF-8 is not supported", e);
		}
	}
}
