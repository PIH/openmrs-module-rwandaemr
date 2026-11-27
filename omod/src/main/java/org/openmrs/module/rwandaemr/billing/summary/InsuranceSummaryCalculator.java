package org.openmrs.module.rwandaemr.billing.summary;

import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationStage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds up the vouchers of each insurance.  Amounts are computed item by item exactly as the bill report does: the
 * total of an item is its unit price times its quantity, the patient pays (100 - insurance rate)% of it, rounded per
 * item, and the insurance the rest.
 */
public class InsuranceSummaryCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * A closed global bill of the period
	 */
	public static class Bill {
		final Integer globalBillId;
		final Integer insuranceId;
		final String voucherStatus;
		final BillVerificationStage stage;

		public Bill(Integer globalBillId, Integer insuranceId, String voucherStatus, BillVerificationStage stage) {
			this.globalBillId = globalBillId;
			this.insuranceId = insuranceId;
			this.voucherStatus = voucherStatus;
			this.stage = stage == null ? BillVerificationStage.FACILITY_REVIEW : stage;
		}
	}

	/**
	 * An item billed on a global bill, that is not voided
	 */
	public static class Item {
		final Integer globalBillId;
		final Integer itemId;
		final BigDecimal unitPrice;
		final BigDecimal quantity;

		public Item(Integer globalBillId, Integer itemId, BigDecimal unitPrice, BigDecimal quantity) {
			this.globalBillId = globalBillId;
			this.itemId = itemId;
			this.unitPrice = unitPrice;
			this.quantity = quantity;
		}

		/**
		 * @return the total of the item, as the bill report computes it
		 */
		BigDecimal getTotal() {
			BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
			BigDecimal count = quantity == null ? BigDecimal.ONE : quantity;
			return price.multiply(count).setScale(2, RoundingMode.HALF_UP);
		}
	}

	/**
	 * An insurance, with the rate it pays
	 */
	public static class Insurance {
		final Integer insuranceId;
		final String name;
		final String category;
		final BigDecimal ratePercent;

		public Insurance(Integer insuranceId, String name, String category, BigDecimal ratePercent) {
			this.insuranceId = insuranceId;
			this.name = name;
			this.category = category;
			this.ratePercent = ratePercent == null ? BigDecimal.ZERO : ratePercent;
		}
	}

	/**
	 * @param insurances the insurances, by id; a bill of an insurance not given is left out
	 * @param itemStatuses the verification status of the items that were verified, by item
	 * @param billReportTotals the total of the bill report for each insurance, or null if it could not be read
	 * @return a row per insurance that has vouchers, ordered by name
	 */
	public List<InsuranceSummaryRow> calculate(Collection<Bill> bills, Collection<Item> items,
	                                           Map<Integer, Insurance> insurances,
	                                           Map<Integer, BillItemVerificationStatus> itemStatuses,
	                                           Map<Integer, BigDecimal> billReportTotals) {
		Map<Integer, Bill> billsById = new HashMap<Integer, Bill>();
		Map<Integer, InsuranceSummaryRow> rows = new LinkedHashMap<Integer, InsuranceSummaryRow>();
		for (Bill bill : bills) {
			Insurance insurance = insurances.get(bill.insuranceId);
			if (insurance == null) {
				continue;
			}
			billsById.put(bill.globalBillId, bill);
			InsuranceSummaryRow row = rows.get(insurance.insuranceId);
			if (row == null) {
				row = new InsuranceSummaryRow(insurance.insuranceId, insurance.name, insurance.category);
				rows.put(insurance.insuranceId, row);
			}
			countBill(row, bill);
		}
		for (Item item : items) {
			Bill bill = billsById.get(item.globalBillId);
			if (bill == null) {
				continue;
			}
			InsuranceSummaryRow row = rows.get(bill.insuranceId);
			BigDecimal total = item.getTotal();
			BigDecimal patientDue = total.multiply(HUNDRED.subtract(insurances.get(bill.insuranceId).ratePercent))
					.divide(HUNDRED, 2, RoundingMode.HALF_UP);
			row.setTotal(row.getTotal().add(total));
			row.setPatientDue(row.getPatientDue().add(patientDue));
			row.setInsuranceDue(row.getInsuranceDue().add(total.subtract(patientDue).setScale(2, RoundingMode.HALF_UP)));
			BillItemVerificationStatus status = itemStatuses == null ? null : itemStatuses.get(item.itemId);
			if (status != null && status.getOutcome() == BillItemVerificationStatus.Outcome.REJECTED) {
				row.setRejected(row.getRejected().add(total));
			}
		}
		if (billReportTotals != null) {
			for (InsuranceSummaryRow row : rows.values()) {
				BigDecimal reportTotal = billReportTotals.get(row.getInsuranceId());
				row.setBillReportTotal(reportTotal == null ? BigDecimal.ZERO.setScale(2) : reportTotal.setScale(2, RoundingMode.HALF_UP));
			}
		}
		List<InsuranceSummaryRow> ret = new ArrayList<InsuranceSummaryRow>(rows.values());
		Collections.sort(ret, new Comparator<InsuranceSummaryRow>() {
			@Override
			public int compare(InsuranceSummaryRow left, InsuranceSummaryRow right) {
				return String.valueOf(left.getName()).compareToIgnoreCase(String.valueOf(right.getName()));
			}
		});
		return ret;
	}

	/**
	 * @return the total of the given rows
	 */
	public InsuranceSummaryRow total(Collection<InsuranceSummaryRow> rows) {
		InsuranceSummaryRow ret = new InsuranceSummaryRow(null, "Total", null);
		for (InsuranceSummaryRow row : rows) {
			ret.add(row);
		}
		return ret;
	}

	private void countBill(InsuranceSummaryRow row, Bill bill) {
		row.setVouchers(row.getVouchers() + 1);
		if (RhipVoucherSubmission.STATUS_CONFIRMED.equals(bill.voucherStatus)) {
			row.setConfirmed(row.getConfirmed() + 1);
		}
		else if (RhipVoucherSubmission.STATUS_SENT.equals(bill.voucherStatus)
				|| RhipVoucherSubmission.STATUS_PROCESSING.equals(bill.voucherStatus)) {
			row.setSent(row.getSent() + 1);
		}
		else if (RhipVoucherSubmission.STATUS_FAILED.equals(bill.voucherStatus)) {
			row.setFailed(row.getFailed() + 1);
		}
		else {
			row.setNotSent(row.getNotSent() + 1);
		}
		switch (bill.stage) {
			case CLOSED:
				row.setVerificationClosed(row.getVerificationClosed() + 1);
				break;
			case FACILITY_REVIEW:
				row.setVerificationNotStarted(row.getVerificationNotStarted() + 1);
				break;
			default:
				row.setVerificationInReview(row.getVerificationInReview() + 1);
				break;
		}
		if (bill.stage == BillVerificationStage.INSURER_REVIEW) {
			row.setAwaitingInsurerReview(row.getAwaitingInsurerReview() + 1);
		}
		else if (bill.stage == BillVerificationStage.FINAL_REVIEW) {
			row.setAwaitingInsurerFinalDecision(row.getAwaitingInsurerFinalDecision() + 1);
		}
	}
}
