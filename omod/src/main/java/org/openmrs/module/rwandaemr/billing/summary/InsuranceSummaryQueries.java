package org.openmrs.module.rwandaemr.billing.summary;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.query.NativeQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.mohbilling.model.RhipVoucherSubmission;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationStage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads what the insurance summary adds up, straight from the billing tables: the closed global bills admitted in a
 * period, the same bills as the RHIP voucher page filtered on the admission date.
 */
public class InsuranceSummaryQueries {

	private static final Log log = LogFactory.getLog(InsuranceSummaryQueries.class);

	private static final int BATCH_SIZE = 1000;

	/**
	 * The status of the voucher of a bill, as the RHIP voucher page shows it
	 */
	private static final String VOUCHER_STATUS_SQL = "(case when ((gb.rhip_voucher_code is not null and gb.rhip_voucher_code <> '') "
			+ "or (gb.rhip_voucher_reference_number is not null and gb.rhip_voucher_reference_number <> '')) "
			+ "and exists (select 1 from moh_bill_rhip_voucher_submission confirmed_sub "
			+ "where confirmed_sub.global_bill_id = gb.global_bill_id and confirmed_sub.status = '" + RhipVoucherSubmission.STATUS_CONFIRMED + "') "
			+ "then '" + RhipVoucherSubmission.STATUS_CONFIRMED + "' "
			+ "when (gb.rhip_voucher_code is not null and gb.rhip_voucher_code <> '') "
			+ "or (gb.rhip_voucher_reference_number is not null and gb.rhip_voucher_reference_number <> '') "
			+ "or exists (select 1 from moh_bill_rhip_voucher_submission sent_sub "
			+ "where sent_sub.global_bill_id = gb.global_bill_id and sent_sub.status = '" + RhipVoucherSubmission.STATUS_SENT + "') "
			+ "then '" + RhipVoucherSubmission.STATUS_SENT + "' "
			+ "else coalesce((select latest.status from moh_bill_rhip_voucher_submission latest "
			+ "where latest.global_bill_id = gb.global_bill_id "
			+ "order by latest.attempt_number desc, latest.date_submitted desc, latest.rhip_voucher_submission_id desc limit 1), '"
			+ RhipVoucherSubmission.STATUS_NOT_SENT + "') end)";

	private final DbSessionFactory dbSessionFactory;

	public InsuranceSummaryQueries(DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @param insuranceIds the insurances to read, or null for all of them
	 * @return the closed global bills admitted in the period, with the step their verification is at
	 */
	public List<InsuranceSummaryCalculator.Bill> getBills(Date start, Date end, Collection<Integer> insuranceIds) {
		StringBuilder sql = new StringBuilder("select gb.global_bill_id, gb.insurance_id, ")
				.append(VOUCHER_STATUS_SQL)
				.append(" from moh_bill_global_bill gb join moh_bill_admission admission on gb.admission_id = admission.admission_id ")
				.append("where gb.closed = 1 and (gb.voided = 0 or gb.voided is null) ")
				.append("and admission.admission_date between :start and :end ");
		if (insuranceIds != null) {
			sql.append("and gb.insurance_id in (:insuranceIds) ");
		}
		NativeQuery<?> query = session().createSQLQuery(sql.toString());
		query.setParameter("start", start);
		query.setParameter("end", end);
		if (insuranceIds != null) {
			query.setParameterList("insuranceIds", insuranceIds.isEmpty() ? java.util.Collections.singleton(-1) : insuranceIds);
		}
		List<Object[]> rows = new ArrayList<Object[]>();
		for (Object row : query.list()) {
			rows.add((Object[]) row);
		}
		Map<Integer, BillVerificationStage> stages = getStages(globalBillIds(rows));
		List<InsuranceSummaryCalculator.Bill> ret = new ArrayList<InsuranceSummaryCalculator.Bill>();
		for (Object[] row : rows) {
			Integer globalBillId = toInteger(row[0]);
			ret.add(new InsuranceSummaryCalculator.Bill(globalBillId, toInteger(row[1]),
					row[2] == null ? null : row[2].toString(), stages.get(globalBillId)));
		}
		return ret;
	}

	/**
	 * @return the items of the given bills that are not voided, on consommations that are not voided
	 */
	public List<InsuranceSummaryCalculator.Item> getItems(List<Integer> globalBillIds) {
		List<InsuranceSummaryCalculator.Item> ret = new ArrayList<InsuranceSummaryCalculator.Item>();
		for (List<Integer> batch : batches(globalBillIds)) {
			NativeQuery<?> query = session().createSQLQuery(
					"select c.global_bill_id, psb.patient_service_bill_id, psb.unit_price, psb.quantity "
							+ "from moh_bill_patient_service_bill psb join moh_bill_consommation c on psb.consommation_id = c.consommation_id "
							+ "where c.global_bill_id in (:ids) and (c.voided = 0 or c.voided is null) "
							+ "and (psb.voided = 0 or psb.voided is null)");
			query.setParameterList("ids", batch);
			for (Object row : query.list()) {
				Object[] values = (Object[]) row;
				ret.add(new InsuranceSummaryCalculator.Item(toInteger(values[0]), toInteger(values[1]),
						toBigDecimal(values[2]), toBigDecimal(values[3])));
			}
		}
		return ret;
	}

	/**
	 * @return the verification status of the items of the given bills that were verified, by item
	 */
	public Map<Integer, BillItemVerificationStatus> getItemStatuses(List<Integer> globalBillIds) {
		Map<Integer, BillItemVerificationStatus> ret = new HashMap<Integer, BillItemVerificationStatus>();
		for (List<Integer> batch : batches(globalBillIds)) {
			NativeQuery<?> query = session().createSQLQuery(
					"select patient_service_bill_id, status from rwandaemr_bill_item_verification where global_bill_id in (:ids)");
			query.setParameterList("ids", batch);
			for (Object row : query.list()) {
				Object[] values = (Object[]) row;
				try {
					ret.put(toInteger(values[0]), BillItemVerificationStatus.valueOf(values[1].toString()));
				}
				catch (RuntimeException e) {
					log.warn("Unknown verification status of bill item " + values[0] + ": " + values[1]);
				}
			}
		}
		return ret;
	}

	/**
	 * @return the total of the bill report for each insurance, for the same admissions; null if the bill report data
	 * can't be read
	 */
	public Map<Integer, BigDecimal> getBillReportTotals(Date start, Date end) {
		try {
			NativeQuery<?> query = session().createSQLQuery(
					"select insurance_id, sum(total_invoice) from moh_bill_bill_report_etl "
							+ "where admission_date >= :start and admission_date <= :end group by insurance_id");
			query.setParameter("start", start);
			query.setParameter("end", end);
			Map<Integer, BigDecimal> ret = new HashMap<Integer, BigDecimal>();
			for (Object row : query.list()) {
				Object[] values = (Object[]) row;
				if (values[0] != null) {
					ret.put(toInteger(values[0]), toBigDecimal(values[1]));
				}
			}
			return ret;
		}
		catch (RuntimeException e) {
			log.warn("Unable to read the bill report data to compare with the vouchers", e);
			return null;
		}
	}

	private Map<Integer, BillVerificationStage> getStages(List<Integer> globalBillIds) {
		Map<Integer, BillVerificationStage> ret = new HashMap<Integer, BillVerificationStage>();
		for (List<Integer> batch : batches(globalBillIds)) {
			NativeQuery<?> query = session().createSQLQuery(
					"select global_bill_id, stage from rwandaemr_bill_verification where global_bill_id in (:ids)");
			query.setParameterList("ids", batch);
			for (Object row : query.list()) {
				Object[] values = (Object[]) row;
				try {
					ret.put(toInteger(values[0]), BillVerificationStage.valueOf(values[1].toString()));
				}
				catch (RuntimeException e) {
					log.warn("Unknown verification step of global bill " + values[0] + ": " + values[1]);
				}
			}
		}
		return ret;
	}

	private List<Integer> globalBillIds(List<Object[]> rows) {
		List<Integer> ret = new ArrayList<Integer>();
		for (Object[] row : rows) {
			ret.add(toInteger(row[0]));
		}
		return ret;
	}

	private List<List<Integer>> batches(List<Integer> ids) {
		List<List<Integer>> ret = new ArrayList<List<Integer>>();
		for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
			ret.add(ids.subList(i, Math.min(ids.size(), i + BATCH_SIZE)));
		}
		return ret;
	}

	private org.hibernate.Session session() {
		return dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
	}

	private static Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private static BigDecimal toBigDecimal(Object value) {
		if (value == null) {
			return null;
		}
		return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
	}
}
