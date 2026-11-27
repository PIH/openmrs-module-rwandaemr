package org.openmrs.module.rwandaemr.rest;

import org.openmrs.api.APIAuthenticationException;
import org.openmrs.module.rwandaemr.billing.summary.InsuranceSummaryRow;
import org.openmrs.module.rwandaemr.billing.summary.RhipInsuranceSummaryService;
import org.openmrs.module.rwandaemr.page.controller.admin.RhipInsuranceSummaryPageController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;

/**
 * Downloads the RHIP insurance summary as a CSV file that opens in Excel, with the same figures as the page
 */
@Controller
public class RhipInsuranceSummaryExportController {

	@Autowired
	RhipInsuranceSummaryService summaryService;

	@RequestMapping(value = "/rest/v1/rwandaemr/rhipInsuranceSummary/export", method = RequestMethod.GET)
	public void export(@RequestParam(value = "startDate", required = false) String startDate,
	                   @RequestParam(value = "endDate", required = false) String endDate,
	                   @RequestParam(value = "insuranceId", required = false) String insuranceId,
	                   HttpServletResponse response) throws IOException {
		if (!summaryService.canView()) {
			throw new APIAuthenticationException("You do not have permission to view the RHIP insurance summary.");
		}
		RhipInsuranceSummaryPageController.Period period = RhipInsuranceSummaryPageController.Period.of(startDate, endDate);
		RhipInsuranceSummaryService.Summary summary = summaryService.getSummary(period.start, period.end,
				RhipInsuranceSummaryPageController.selectedInsurance(insuranceId, summaryService));

		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"rhip-insurance-summary-"
				+ period.startText + "-to-" + period.endText + ".csv\"");
		PrintWriter out = response.getWriter();
		// A byte order mark, so that Excel reads the file as UTF-8
		out.write('﻿');
		out.println(csv("Admitted from", period.startText) + "," + csv("to", period.endText));
		out.println(String.join(",", "Insurance", "Category", "Vouchers", "Confirmed", "Sent", "Failed", "Not sent",
				"Total amount", "Patient due", "Insurance due", "Verification not started", "Verification in review",
				"Verification closed", "Rejected", "Bill report total", "Bill report difference"));
		for (InsuranceSummaryRow row : summary.getRows()) {
			out.println(line(row));
		}
		out.println(line(summary.getTotal()));
		out.flush();
	}

	private String line(InsuranceSummaryRow row) {
		return String.join(",", csv(row.getName()), csv(row.getCategory()), String.valueOf(row.getVouchers()),
				String.valueOf(row.getConfirmed()), String.valueOf(row.getSent()), String.valueOf(row.getFailed()),
				String.valueOf(row.getNotSent()), amount(row.getTotal()), amount(row.getPatientDue()),
				amount(row.getInsuranceDue()), verified(row, row.getVerificationNotStarted()),
				verified(row, row.getVerificationInReview()), verified(row, row.getVerificationClosed()),
				row.isVerified() ? amount(row.getRejected()) : "", amount(row.getBillReportTotal()),
				amount(row.getBillReportDifference()));
	}

	/**
	 * @return the count, or "Not verified" for an insurance whose bills are not verified
	 */
	private String verified(InsuranceSummaryRow row, int count) {
		return row.isVerified() ? String.valueOf(count) : "Not verified";
	}

	private String amount(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private String csv(String... values) {
		StringBuilder ret = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				ret.append(',');
			}
			String value = values[i] == null ? "" : values[i];
			// Quote every text value, and stop Excel from reading a value as a formula
			if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
				value = "'" + value;
			}
			ret.append('"').append(value.replace("\"", "\"\"")).append('"');
		}
		return ret.toString();
	}
}
