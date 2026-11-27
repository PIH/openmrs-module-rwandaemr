package org.openmrs.module.rwandaemr.billing.summary;

import org.junit.jupiter.api.Test;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationStage;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class InsuranceSummaryCalculatorTest {

	private static final Integer RSSB = 1;
	private static final Integer MMI = 2;

	private final InsuranceSummaryCalculator calculator = new InsuranceSummaryCalculator();

	private Map<Integer, InsuranceSummaryCalculator.Insurance> insurances() {
		Map<Integer, InsuranceSummaryCalculator.Insurance> ret = new HashMap<Integer, InsuranceSummaryCalculator.Insurance>();
		ret.put(RSSB, new InsuranceSummaryCalculator.Insurance(RSSB, "RSSB", "BASIC", new BigDecimal("90")));
		ret.put(MMI, new InsuranceSummaryCalculator.Insurance(MMI, "MMI", "BASIC", new BigDecimal("85")));
		return ret;
	}

	private InsuranceSummaryCalculator.Bill bill(int id, Integer insuranceId, String voucherStatus, BillVerificationStage stage) {
		return new InsuranceSummaryCalculator.Bill(id, insuranceId, voucherStatus, stage);
	}

	private InsuranceSummaryCalculator.Item item(int billId, int itemId, String unitPrice, String quantity) {
		return new InsuranceSummaryCalculator.Item(billId, itemId, new BigDecimal(unitPrice),
				quantity == null ? null : new BigDecimal(quantity));
	}

	@Test
	public void shouldSplitEachItemBetweenPatientAndInsuranceAsTheBillReportDoes() {
		List<InsuranceSummaryRow> rows = calculator.calculate(
				Collections.singletonList(bill(10, RSSB, RhipVoucherSubmission.STATUS_SENT, null)),
				Arrays.asList(item(10, 100, "1359", "1"), item(10, 101, "333.33", "3")),
				insurances(), null, null);

		assertThat(rows, hasSize(1));
		InsuranceSummaryRow rssb = rows.get(0);
		// 1359 -> patient 135.90; 999.99 -> patient 100.00 (rounded per item)
		assertThat(rssb.getTotal(), comparesEqualTo(new BigDecimal("2358.99")));
		assertThat(rssb.getPatientDue(), comparesEqualTo(new BigDecimal("235.90")));
		assertThat(rssb.getInsuranceDue(), comparesEqualTo(new BigDecimal("2123.09")));
	}

	@Test
	public void shouldCountAnItemWithoutQuantityOnce() {
		List<InsuranceSummaryRow> rows = calculator.calculate(
				Collections.singletonList(bill(10, MMI, null, null)),
				Collections.singletonList(item(10, 100, "200", null)), insurances(), null, null);

		assertThat(rows.get(0).getTotal(), comparesEqualTo(new BigDecimal("200")));
		assertThat(rows.get(0).getPatientDue(), comparesEqualTo(new BigDecimal("30")));
	}

	@Test
	public void shouldCountVouchersByStatusAndVerificationStep() {
		List<InsuranceSummaryRow> rows = calculator.calculate(Arrays.asList(
				bill(1, RSSB, RhipVoucherSubmission.STATUS_CONFIRMED, BillVerificationStage.CLOSED),
				bill(2, RSSB, RhipVoucherSubmission.STATUS_SENT, BillVerificationStage.INSURER_REVIEW),
				bill(3, RSSB, RhipVoucherSubmission.STATUS_FAILED, BillVerificationStage.FINAL_REVIEW),
				bill(4, RSSB, RhipVoucherSubmission.STATUS_NOT_SENT, null)),
				Collections.<InsuranceSummaryCalculator.Item>emptyList(), insurances(), null, null);

		InsuranceSummaryRow rssb = rows.get(0);
		assertThat(rssb.getVouchers(), is(4));
		assertThat(rssb.getConfirmed(), is(1));
		assertThat(rssb.getSent(), is(1));
		assertThat(rssb.getFailed(), is(1));
		assertThat(rssb.getNotSent(), is(1));
		assertThat(rssb.getVerificationClosed(), is(1));
		assertThat(rssb.getVerificationInReview(), is(2));
		assertThat(rssb.getVerificationNotStarted(), is(1));
		assertThat(rssb.getAwaitingInsurerReview(), is(1));
		assertThat(rssb.getAwaitingInsurerFinalDecision(), is(1));
	}

	@Test
	public void shouldOnlyCountFinalRejectionsAsRejected() {
		Map<Integer, BillItemVerificationStatus> statuses = new HashMap<Integer, BillItemVerificationStatus>();
		statuses.put(100, BillItemVerificationStatus.FINAL_REJECTED);
		statuses.put(101, BillItemVerificationStatus.INSURER_REJECTED);
		statuses.put(102, BillItemVerificationStatus.FACILITY_REJECTED);
		List<InsuranceSummaryRow> rows = calculator.calculate(
				Collections.singletonList(bill(10, RSSB, null, BillVerificationStage.FACILITY_RESPONSE)),
				Arrays.asList(item(10, 100, "100", "1"), item(10, 101, "50", "1"), item(10, 102, "20", "1")),
				insurances(), statuses, null);

		assertThat(rows.get(0).getRejected(), comparesEqualTo(new BigDecimal("120")));
	}

	@Test
	public void shouldLeaveOutBillsOfInsurancesNotShown() {
		List<InsuranceSummaryRow> rows = calculator.calculate(Arrays.asList(bill(1, RSSB, null, null), bill(2, 99, null, null)),
				Arrays.asList(item(1, 100, "10", "1"), item(2, 200, "1000", "1")), insurances(), null, null);

		assertThat(rows, hasSize(1));
		assertThat(rows.get(0).getTotal(), comparesEqualTo(BigDecimal.TEN));
	}

	@Test
	public void shouldCompareWithTheBillReport() {
		Map<Integer, BigDecimal> billReport = new HashMap<Integer, BigDecimal>();
		billReport.put(RSSB, new BigDecimal("1359.00"));
		List<InsuranceSummaryRow> rows = calculator.calculate(
				Arrays.asList(bill(1, RSSB, null, null), bill(2, MMI, null, null)),
				Arrays.asList(item(1, 100, "1359", "1"), item(2, 200, "500", "1")), insurances(), null, billReport);

		InsuranceSummaryRow mmi = rows.get(0);
		InsuranceSummaryRow rssb = rows.get(1);
		assertThat(mmi.getName(), equalTo("MMI"));
		assertThat(rssb.isMatchingBillReport(), is(true));
		assertThat(mmi.isMatchingBillReport(), is(false));
		assertThat(mmi.getBillReportDifference(), comparesEqualTo(new BigDecimal("-500")));
	}

	@Test
	public void shouldNotCompareWhenTheBillReportCannotBeRead() {
		List<InsuranceSummaryRow> rows = calculator.calculate(Collections.singletonList(bill(1, RSSB, null, null)),
				Collections.singletonList(item(1, 100, "10", "1")), insurances(), null, null);

		assertThat(rows.get(0).getBillReportDifference(), nullValue());
		assertThat(rows.get(0).isMatchingBillReport(), is(false));
	}

	@Test
	public void shouldAddUpTheTotalOfAllInsurances() {
		List<InsuranceSummaryRow> rows = calculator.calculate(
				Arrays.asList(bill(1, RSSB, null, null), bill(2, MMI, null, null)),
				Arrays.asList(item(1, 100, "1000", "1"), item(2, 200, "500", "1")), insurances(), null, null);

		InsuranceSummaryRow total = calculator.total(rows);
		assertThat(total.getVouchers(), is(2));
		assertThat(total.getTotal(), comparesEqualTo(new BigDecimal("1500")));
		assertThat(total.getPatientDue(), comparesEqualTo(new BigDecimal("175")));
		assertThat(total.getInsuranceDue(), comparesEqualTo(new BigDecimal("1325")));
	}
}
