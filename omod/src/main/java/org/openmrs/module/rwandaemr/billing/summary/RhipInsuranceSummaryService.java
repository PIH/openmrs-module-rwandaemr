package org.openmrs.module.rwandaemr.billing.summary;

import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.mohbilling.businesslogic.InsuranceUtil;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsuranceRate;
import org.openmrs.module.rwandaemr.billing.InsuranceFilterOptions;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationService;
import org.openmrs.module.rwandaemr.billing.verification.VerificationScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The RHIP insurance summary: for a period of admission, the closed global bills of each insurance with their total,
 * patient due and insurance due, as the bill report computes them.  An insurer's verifier only sees the insurances
 * linked to their account.
 */
@Component
public class RhipInsuranceSummaryService {

	public static final String PRIVILEGE_VIEW_VOUCHERS = "Billing RHIP Voucher - View Submission Page";

	public static final String PRIVILEGE_BILLING_ADMIN = "Billing Configuration - View Billing Admin";

	private final DbSessionFactory dbSessionFactory;
	private final BillVerificationService verificationService;

	public RhipInsuranceSummaryService(@Autowired DbSessionFactory dbSessionFactory,
	                                   @Autowired BillVerificationService verificationService) {
		this.dbSessionFactory = dbSessionFactory;
		this.verificationService = verificationService;
	}

	/**
	 * The summary of a period
	 */
	public static class Summary {
		private final List<InsuranceSummaryRow> rows;
		private final InsuranceSummaryRow total;
		private final List<InsuranceFilterOptions.Option> insuranceOptions;
		private final boolean limited;
		private final boolean billReportAvailable;

		Summary(List<InsuranceSummaryRow> rows, InsuranceSummaryRow total, List<InsuranceFilterOptions.Option> insuranceOptions,
		        boolean limited, boolean billReportAvailable) {
			this.rows = rows;
			this.total = total;
			this.insuranceOptions = insuranceOptions;
			this.limited = limited;
			this.billReportAvailable = billReportAvailable;
		}

		public List<InsuranceSummaryRow> getRows() {
			return rows;
		}

		public InsuranceSummaryRow getTotal() {
			return total;
		}

		/**
		 * @return what the user can choose from: MUTUELLE, then every other insurance they can see
		 */
		public List<InsuranceFilterOptions.Option> getInsuranceOptions() {
			return insuranceOptions;
		}

		/**
		 * @return true if the user only sees some insurances
		 */
		public boolean isLimited() {
			return limited;
		}

		/**
		 * @return true if the bill report data could be read to compare with the vouchers
		 */
		public boolean isBillReportAvailable() {
			return billReportAvailable;
		}

		/**
		 * @return the number of insurances whose vouchers don't add up to the bill report
		 */
		public int getBillReportDifferences() {
			int ret = 0;
			for (InsuranceSummaryRow row : rows) {
				if (row.getBillReportTotal() != null && !row.isMatchingBillReport()) {
					ret++;
				}
			}
			return ret;
		}
	}

	/**
	 * @return true if the current user can see the summary: the same users as the RHIP voucher page
	 */
	public boolean canView() {
		return Context.hasPrivilege(PRIVILEGE_VIEW_VOUCHERS) || Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_FACILITY)
				|| Context.hasPrivilege(BillVerificationService.PRIVILEGE_INSURER);
	}

	/**
	 * @return the insurances the current user can see, by name
	 */
	public List<Insurance> getInsuranceOptions() {
		List<Integer> allowed = verificationService.isLimitedToInsurances() ? verificationService.getVerifierInsuranceIds() : null;
		// Every insurance is shown, the private insurance too, even where the bill report hides it
		List<Insurance> ret = new ArrayList<Insurance>();
		for (Insurance insurance : InsuranceUtil.getInsurances(true)) {
			if (insurance == null || insurance.getInsuranceId() == null) {
				continue;
			}
			if (allowed == null || allowed.contains(insurance.getInsuranceId())) {
				ret.add(insurance);
			}
		}
		Collections.sort(ret, new Comparator<Insurance>() {
			@Override
			public int compare(Insurance left, Insurance right) {
				return String.valueOf(left.getName()).compareToIgnoreCase(String.valueOf(right.getName()));
			}
		});
		return ret;
	}

	/**
	 * @return what the current user can choose from: MUTUELLE for every insurance of that category, then every other
	 * insurance they can see
	 */
	public List<InsuranceFilterOptions.Option> getFilterOptions() {
		return InsuranceFilterOptions.build(getInsuranceOptions());
	}

	/**
	 * @param start the first day of admission, from its start
	 * @param end the last day of admission, to its end
	 * @param filterValue the choice of insurance: an insurance id, category:MUTUELLE, or null for all those the user
	 * can see
	 */
	@Transactional(readOnly = true)
	public Summary getSummary(Date start, Date end, String filterValue) {
		List<Insurance> visible = getInsuranceOptions();
		List<InsuranceFilterOptions.Option> options = InsuranceFilterOptions.build(visible);
		InsuranceFilterOptions.Option chosen = InsuranceFilterOptions.find(options, filterValue);
		Map<Integer, InsuranceSummaryCalculator.Insurance> insurances = new LinkedHashMap<Integer, InsuranceSummaryCalculator.Insurance>();
		for (Insurance insurance : visible) {
			if (chosen == null || chosen.getInsuranceIds().contains(insurance.getInsuranceId())) {
				InsuranceRate rate = insurance.getCurrentRate();
				BigDecimal ratePercent = rate == null || rate.getRate() == null ? BigDecimal.ZERO
						: BigDecimal.valueOf(rate.getRate().doubleValue());
				insurances.put(insurance.getInsuranceId(), new InsuranceSummaryCalculator.Insurance(
						insurance.getInsuranceId(), insurance.getName(), insurance.getCategory(), ratePercent));
			}
		}
		boolean limited = verificationService.isLimitedToInsurances();
		InsuranceSummaryCalculator calculator = new InsuranceSummaryCalculator();
		if (insurances.isEmpty()) {
			List<InsuranceSummaryRow> none = Collections.emptyList();
			return new Summary(none, calculator.total(none), options, limited, false);
		}

		InsuranceSummaryQueries queries = new InsuranceSummaryQueries(dbSessionFactory);
		List<InsuranceSummaryCalculator.Bill> bills = queries.getBills(start, end, insurances.keySet());
		List<Integer> globalBillIds = new ArrayList<Integer>();
		for (InsuranceSummaryCalculator.Bill bill : bills) {
			globalBillIds.add(bill.globalBillId);
		}
		Map<Integer, BigDecimal> billReportTotals = queries.getBillReportTotals(start, end);
		List<InsuranceSummaryRow> calculated = calculator.calculate(bills, queries.getItems(globalBillIds),
				insurances, queries.getItemStatuses(globalBillIds), billReportTotals);
		markVerified(calculated, verificationService.getScope());
		List<InsuranceSummaryRow> rows = combineMutuelle(calculated, billReportTotals != null);
		return new Summary(rows, calculator.total(rows), options, limited, billReportTotals != null);
	}

	/**
	 * Marks the rows of the insurances whose bills are verified; the verification of the others is not shown
	 */
	static void markVerified(List<InsuranceSummaryRow> rows, VerificationScope scope) {
		for (InsuranceSummaryRow row : rows) {
			if (scope.includes(row.getInsuranceId(), row.getCategory())) {
				row.setVerified(true);
			}
			else {
				row.clearVerification();
			}
		}
	}

	/**
	 * @return the rows with every insurance of the MUTUELLE category added up into one MUTUELLE row, first, as the
	 * bill report shows them
	 */
	static List<InsuranceSummaryRow> combineMutuelle(List<InsuranceSummaryRow> rows, boolean billReportRead) {
		InsuranceSummaryRow mutuelle = null;
		List<InsuranceSummaryRow> others = new ArrayList<InsuranceSummaryRow>();
		for (InsuranceSummaryRow row : rows) {
			if (InsuranceFilterOptions.isMutuelle(row.getCategory())) {
				if (mutuelle == null) {
					mutuelle = new InsuranceSummaryRow(null, InsuranceFilterOptions.MUTUELLE, InsuranceFilterOptions.MUTUELLE);
					mutuelle.setFilterValue(InsuranceFilterOptions.CATEGORY_PREFIX + InsuranceFilterOptions.MUTUELLE);
					if (billReportRead) {
						mutuelle.setBillReportTotal(BigDecimal.ZERO.setScale(2));
					}
				}
				mutuelle.add(row);
			}
			else {
				others.add(row);
			}
		}
		List<InsuranceSummaryRow> ret = new ArrayList<InsuranceSummaryRow>();
		if (mutuelle != null) {
			ret.add(mutuelle);
		}
		ret.addAll(others);
		return ret;
	}
}
