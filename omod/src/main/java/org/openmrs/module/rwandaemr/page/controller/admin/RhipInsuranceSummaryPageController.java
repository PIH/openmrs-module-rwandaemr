package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.billing.InsuranceFilterOptions;
import org.openmrs.module.rwandaemr.billing.summary.RhipInsuranceSummaryService;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * The RHIP insurance summary: for each insurance, the closed global bills admitted in a period with their total,
 * patient due and insurance due, as in the bill report.
 */
public class RhipInsuranceSummaryPageController {

	static final String DATE_PATTERN = "yyyy-MM-dd";

	public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
	                  @SpringBean RhipInsuranceSummaryService summaryService,
	                  @RequestParam(value = "startDate", required = false) String startDateParam,
	                  @RequestParam(value = "endDate", required = false) String endDateParam,
	                  @RequestParam(value = "insuranceId", required = false) String insuranceIdParam) {
		if (!summaryService.canView()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to view the RHIP insurance summary.");
			return "redirect:" + ui.pageLink("coreapps", "systemadministration/systemAdministration");
		}
		Period period = Period.of(startDateParam, endDateParam);
		String insuranceId = selectedInsurance(insuranceIdParam, summaryService);
		RhipInsuranceSummaryService.Summary summary = summaryService.getSummary(period.start, period.end, insuranceId);

		if (summary.isLimited() && summary.getInsuranceOptions().isEmpty()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"Your account is not linked to an insurance, so no vouchers are shown. Ask an administrator to set "
							+ "rwandaemr.verifierInsuranceId on your account.");
		}
		model.addAttribute("summary", summary);
		model.addAttribute("startDate", period.startText);
		model.addAttribute("endDate", period.endText);
		model.addAttribute("insuranceId", insuranceId == null ? "" : insuranceId);
		// The bill report takes its dates as dd/MM/yyyy
		model.addAttribute("billReportStartDate", new SimpleDateFormat("dd/MM/yyyy").format(period.start));
		model.addAttribute("billReportEndDate", new SimpleDateFormat("dd/MM/yyyy").format(period.end));
		return null;
	}

	/**
	 * @return the value of the insurance chosen (an insurance id, or category:MUTUELLE) if the user can see it, else
	 * null for all those they can see
	 */
	public static String selectedInsurance(String insuranceIdParam, RhipInsuranceSummaryService summaryService) {
		InsuranceFilterOptions.Option option = InsuranceFilterOptions.find(summaryService.getFilterOptions(), insuranceIdParam);
		return option == null ? null : option.getValue();
	}

	/**
	 * A period of admission, by default from the first of this month to today
	 */
	public static class Period {
		public final Date start;
		public final Date end;
		public final String startText;
		public final String endText;

		private Period(Date start, Date end) {
			if (end.before(start)) {
				Date swap = start;
				start = end;
				end = swap;
			}
			this.start = startOfDay(start);
			this.end = endOfDay(end);
			this.startText = new SimpleDateFormat(DATE_PATTERN).format(start);
			this.endText = new SimpleDateFormat(DATE_PATTERN).format(end);
		}

		public static Period of(String startText, String endText) {
			Calendar firstOfMonth = Calendar.getInstance();
			firstOfMonth.set(Calendar.DAY_OF_MONTH, 1);
			Date start = parse(startText, firstOfMonth.getTime());
			Date end = parse(endText, new Date());
			return new Period(start, end);
		}

		private static Date parse(String text, Date defaultDate) {
			if (StringUtils.isBlank(text)) {
				return defaultDate;
			}
			try {
				SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
				format.setLenient(false);
				return format.parse(text.trim());
			}
			catch (ParseException e) {
				return defaultDate;
			}
		}

		private static Date startOfDay(Date date) {
			Calendar c = Calendar.getInstance();
			c.setTime(date);
			c.set(Calendar.HOUR_OF_DAY, 0);
			c.set(Calendar.MINUTE, 0);
			c.set(Calendar.SECOND, 0);
			c.set(Calendar.MILLISECOND, 0);
			return c.getTime();
		}

		private static Date endOfDay(Date date) {
			Calendar c = Calendar.getInstance();
			c.setTime(startOfDay(date));
			c.add(Calendar.DATE, 1);
			c.add(Calendar.MILLISECOND, -1);
			return c.getTime();
		}
	}
}
