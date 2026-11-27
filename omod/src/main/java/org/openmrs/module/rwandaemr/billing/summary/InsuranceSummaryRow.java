package org.openmrs.module.rwandaemr.billing.summary;

import java.math.BigDecimal;

/**
 * The vouchers of one insurance for a period, or the total of several insurances: closed global bills admitted in the
 * period, with their amounts computed as the bill report computes them.
 */
public class InsuranceSummaryRow {

	private Integer insuranceId;
	private String name;
	private String category;
	private int vouchers;
	private int confirmed;
	private int sent;
	private int notSent;
	private int failed;
	private BigDecimal total = BigDecimal.ZERO;
	private BigDecimal patientDue = BigDecimal.ZERO;
	private BigDecimal insuranceDue = BigDecimal.ZERO;
	private int verificationNotStarted;
	private int verificationInReview;
	private int verificationClosed;
	private int awaitingInsurerReview;
	private int awaitingInsurerFinalDecision;
	private BigDecimal rejected = BigDecimal.ZERO;
	private BigDecimal billReportTotal;
	private String filterValue;
	private boolean verified;

	public InsuranceSummaryRow() {
	}

	public InsuranceSummaryRow(Integer insuranceId, String name, String category) {
		this.insuranceId = insuranceId;
		this.name = name;
		this.category = category;
	}

	/**
	 * Adds the figures of another row to this one, for totals
	 */
	public void add(InsuranceSummaryRow row) {
		vouchers += row.vouchers;
		confirmed += row.confirmed;
		sent += row.sent;
		notSent += row.notSent;
		failed += row.failed;
		total = total.add(row.total);
		patientDue = patientDue.add(row.patientDue);
		insuranceDue = insuranceDue.add(row.insuranceDue);
		verificationNotStarted += row.verificationNotStarted;
		verificationInReview += row.verificationInReview;
		verificationClosed += row.verificationClosed;
		awaitingInsurerReview += row.awaitingInsurerReview;
		awaitingInsurerFinalDecision += row.awaitingInsurerFinalDecision;
		rejected = rejected.add(row.rejected);
		verified = verified || row.verified;
		if (row.billReportTotal != null) {
			billReportTotal = billReportTotal == null ? row.billReportTotal : billReportTotal.add(row.billReportTotal);
		}
	}

	/**
	 * @return true if the bills of this insurance are verified, as set in rwandaemr.billVerification.insurances
	 */
	public boolean isVerified() {
		return verified;
	}

	public void setVerified(boolean verified) {
		this.verified = verified;
	}

	/**
	 * Marks the bills of this insurance as not verified: their verification counts and rejections are not shown, and
	 * not added to the totals
	 */
	public void clearVerification() {
		verified = false;
		verificationNotStarted = 0;
		verificationInReview = 0;
		verificationClosed = 0;
		awaitingInsurerReview = 0;
		awaitingInsurerFinalDecision = 0;
		rejected = BigDecimal.ZERO;
	}

	/**
	 * @return the bill report's total less this total: zero when they match, null when the bill report was not read
	 */
	public BigDecimal getBillReportDifference() {
		return billReportTotal == null ? null : billReportTotal.subtract(total);
	}

	/**
	 * @return true if the bill report has the same total as the vouchers
	 */
	public boolean isMatchingBillReport() {
		BigDecimal difference = getBillReportDifference();
		return difference != null && difference.signum() == 0;
	}

	/**
	 * @return the value that chooses this row in the insurance filters: the insurance id, or category:MUTUELLE
	 */
	public String getFilterValue() {
		return filterValue != null ? filterValue : (insuranceId == null ? "" : insuranceId.toString());
	}

	public void setFilterValue(String filterValue) {
		this.filterValue = filterValue;
	}

	public Integer getInsuranceId() {
		return insuranceId;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public int getVouchers() {
		return vouchers;
	}

	public void setVouchers(int vouchers) {
		this.vouchers = vouchers;
	}

	public int getConfirmed() {
		return confirmed;
	}

	public void setConfirmed(int confirmed) {
		this.confirmed = confirmed;
	}

	public int getSent() {
		return sent;
	}

	public void setSent(int sent) {
		this.sent = sent;
	}

	public int getNotSent() {
		return notSent;
	}

	public void setNotSent(int notSent) {
		this.notSent = notSent;
	}

	public int getFailed() {
		return failed;
	}

	public void setFailed(int failed) {
		this.failed = failed;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
		this.total = total;
	}

	public BigDecimal getPatientDue() {
		return patientDue;
	}

	public void setPatientDue(BigDecimal patientDue) {
		this.patientDue = patientDue;
	}

	public BigDecimal getInsuranceDue() {
		return insuranceDue;
	}

	public void setInsuranceDue(BigDecimal insuranceDue) {
		this.insuranceDue = insuranceDue;
	}

	public int getVerificationNotStarted() {
		return verificationNotStarted;
	}

	public void setVerificationNotStarted(int verificationNotStarted) {
		this.verificationNotStarted = verificationNotStarted;
	}

	public int getVerificationInReview() {
		return verificationInReview;
	}

	public void setVerificationInReview(int verificationInReview) {
		this.verificationInReview = verificationInReview;
	}

	public int getVerificationClosed() {
		return verificationClosed;
	}

	public void setVerificationClosed(int verificationClosed) {
		this.verificationClosed = verificationClosed;
	}

	public int getAwaitingInsurerReview() {
		return awaitingInsurerReview;
	}

	public void setAwaitingInsurerReview(int awaitingInsurerReview) {
		this.awaitingInsurerReview = awaitingInsurerReview;
	}

	public int getAwaitingInsurerFinalDecision() {
		return awaitingInsurerFinalDecision;
	}

	public void setAwaitingInsurerFinalDecision(int awaitingInsurerFinalDecision) {
		this.awaitingInsurerFinalDecision = awaitingInsurerFinalDecision;
	}

	public BigDecimal getRejected() {
		return rejected;
	}

	public void setRejected(BigDecimal rejected) {
		this.rejected = rejected;
	}

	public BigDecimal getBillReportTotal() {
		return billReportTotal;
	}

	public void setBillReportTotal(BigDecimal billReportTotal) {
		this.billReportTotal = billReportTotal;
	}

	/**
	 * @return the share of vouchers whose verification is closed, in percent, for the progress bar
	 */
	public int getVerificationClosedPercent() {
		return vouchers == 0 ? 0 : Math.round(100f * verificationClosed / vouchers);
	}

	/**
	 * @return the share of vouchers in verification, in percent, for the progress bar
	 */
	public int getVerificationInReviewPercent() {
		return vouchers == 0 ? 0 : Math.round(100f * verificationInReview / vouchers);
	}
}
