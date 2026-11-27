package org.openmrs.module.rwandaemr.billing.summary;

import org.junit.jupiter.api.Test;
import org.openmrs.module.rwandaemr.billing.verification.VerificationScope;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class RhipInsuranceSummaryServiceTest {

	private static InsuranceSummaryRow row(int id, String name, String category, int vouchers, String total, String billReport) {
		InsuranceSummaryRow ret = new InsuranceSummaryRow(id, name, category);
		ret.setVouchers(vouchers);
		ret.setTotal(new BigDecimal(total));
		ret.setBillReportTotal(billReport == null ? null : new BigDecimal(billReport));
		return ret;
	}

	@Test
	public void shouldAddUpEveryMutuelleInsuranceIntoOneRowFirst() {
		List<InsuranceSummaryRow> rows = RhipInsuranceSummaryService.combineMutuelle(Arrays.asList(
				row(1, "MMI", "MMI", 3, "300", "300"),
				row(2, "Mutuelle Butaro", "MUTUELLE", 10, "1000", "1000"),
				row(3, "Mutuelle Burera", "Mutuelle", 5, "500", "450"),
				row(4, "RSSB", "RSSB", 2, "200", "200")), true);

		assertThat(rows, hasSize(3));
		InsuranceSummaryRow mutuelle = rows.get(0);
		assertThat(mutuelle.getName(), equalTo("MUTUELLE"));
		assertThat(mutuelle.getFilterValue(), equalTo("category:MUTUELLE"));
		assertThat(mutuelle.getVouchers(), is(15));
		assertThat(mutuelle.getTotal(), comparesEqualTo(new BigDecimal("1500")));
		assertThat(mutuelle.getBillReportTotal(), comparesEqualTo(new BigDecimal("1450")));
		assertThat(mutuelle.isMatchingBillReport(), is(false));
		assertThat(rows.get(1).getName(), equalTo("MMI"));
		assertThat(rows.get(1).getFilterValue(), equalTo("1"));
		assertThat(rows.get(2).getName(), equalTo("RSSB"));
	}

	@Test
	public void shouldLeaveTheRowsAsTheyAreWithoutMutuelle() {
		List<InsuranceSummaryRow> rows = RhipInsuranceSummaryService.combineMutuelle(Arrays.asList(
				row(1, "MMI", "MMI", 3, "300", null)), false);

		assertThat(rows, hasSize(1));
		assertThat(rows.get(0).getName(), equalTo("MMI"));
	}

	@Test
	public void shouldShowTheVerificationOfVerifiedInsurancesOnly() {
		InsuranceSummaryRow rssb = row(4, "RSSB", "RSSB", 2, "200", null);
		rssb.setVerificationNotStarted(2);
		rssb.setRejected(new BigDecimal("50"));
		InsuranceSummaryRow mutuelle = row(2, "Mutuelle Butaro", "MUTUELLE", 3, "300", null);
		mutuelle.setVerificationInReview(3);
		mutuelle.setRejected(new BigDecimal("30"));
		List<InsuranceSummaryRow> rows = Arrays.asList(rssb, mutuelle);

		RhipInsuranceSummaryService.markVerified(rows, new VerificationScope(""));
		InsuranceSummaryRow total = new InsuranceSummaryCalculator().total(rows);

		assertThat(rssb.isVerified(), is(false));
		assertThat(rssb.getVerificationNotStarted(), is(0));
		assertThat(rssb.getRejected(), comparesEqualTo(BigDecimal.ZERO));
		assertThat(mutuelle.isVerified(), is(true));
		assertThat(total.isVerified(), is(true));
		assertThat(total.getVerificationInReview(), is(3));
		assertThat(total.getRejected(), comparesEqualTo(new BigDecimal("30")));
		assertThat(total.getTotal(), comparesEqualTo(new BigDecimal("500")));
	}
}
