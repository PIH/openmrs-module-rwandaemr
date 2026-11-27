package org.openmrs.module.rwandaemr.billing.verification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The verification of global bills: the step each bill is at, the latest decision on each of its items, the reasons
 * given for rejections and disputes, and the audit trail of everything done
 */
@Component
public class BillVerificationRepository {

	private final Log log = LogFactory.getLog(getClass());

	private final DbSessionFactory dbSessionFactory;

	public BillVerificationRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @return the step of each of the given bills that has started its verification, by global bill id.  Bills
	 * missing from the result are still at the facility review.
	 */
	@Transactional(readOnly = true)
	public Map<Integer, BillVerificationStage> getStages(Collection<Integer> globalBillIds) {
		Map<Integer, BillVerificationStage> ret = new HashMap<Integer, BillVerificationStage>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select global_bill_id, stage from rwandaemr_bill_verification where global_bill_id in (:globalBillIds)");
		query.setParameterList("globalBillIds", globalBillIds);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			ret.put(toInteger(values[0]), BillVerificationStage.valueOf((String) values[1]));
		}
		return ret;
	}

	/**
	 * @return the latest decision on each item of the given bills that has one, by patient service bill id
	 */
	@Transactional(readOnly = true)
	public Map<Integer, BillItemVerification> getItems(Collection<Integer> globalBillIds) {
		Map<Integer, BillItemVerification> ret = new LinkedHashMap<Integer, BillItemVerification>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select v.patient_service_bill_id, v.global_bill_id, v.status, v.reason_id, r.name, v.note, " +
						"coalesce(v.changed_by, v.creator), coalesce(v.date_changed, v.date_created) " +
						"from rwandaemr_bill_item_verification v " +
						"left join rwandaemr_bill_verification_reason r on r.reason_id = v.reason_id " +
						"where v.global_bill_id in (:globalBillIds)");
		query.setParameterList("globalBillIds", globalBillIds);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			BillItemVerification item = new BillItemVerification();
			item.setPatientServiceBillId(toInteger(values[0]));
			item.setGlobalBillId(toInteger(values[1]));
			item.setStatus(BillItemVerificationStatus.valueOf((String) values[2]));
			item.setReasonId(toInteger(values[3]));
			item.setReasonName((String) values[4]);
			item.setNote((String) values[5]);
			item.setChangedBy(toInteger(values[6]));
			item.setDateChanged((Date) values[7]);
			ret.put(item.getPatientServiceBillId(), item);
		}
		return ret;
	}

	/**
	 * @return the ids of the items of the bill that are still billed: not voided, and on a consommation that is not
	 * voided
	 */
	@Transactional(readOnly = true)
	public List<Integer> getActiveItemIds(Integer globalBillId) {
		List<Integer> ret = new ArrayList<Integer>();
		List<?> rows = getSession().createSQLQuery(
				"select psb.patient_service_bill_id from moh_bill_patient_service_bill psb " +
						"join moh_bill_consommation c on c.consommation_id = psb.consommation_id " +
						"where c.global_bill_id = :globalBillId and coalesce(psb.voided, 0) = 0 and coalesce(c.voided, 0) = 0 " +
						"order by psb.patient_service_bill_id")
				.setParameter("globalBillId", globalBillId)
				.list();
		for (Object row : rows) {
			ret.add(toInteger(row));
		}
		return ret;
	}

	/**
	 * @return for each of the given bills that has items still billed, by global bill id: the number of those items, and
	 * the number the facility has not accepted or rejected yet
	 */
	@Transactional(readOnly = true)
	public Map<Integer, int[]> getFacilityReviewItemCounts(Collection<Integer> globalBillIds) {
		Map<Integer, int[]> ret = new HashMap<Integer, int[]>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select c.global_bill_id, count(*), " +
						"sum(case when v.status is null or v.status not in ('FACILITY_ACCEPTED', 'FACILITY_REJECTED') then 1 else 0 end) " +
						"from moh_bill_patient_service_bill psb " +
						"join moh_bill_consommation c on c.consommation_id = psb.consommation_id " +
						"left join rwandaemr_bill_item_verification v on v.patient_service_bill_id = psb.patient_service_bill_id " +
						"where c.global_bill_id in (:globalBillIds) and coalesce(psb.voided, 0) = 0 and coalesce(c.voided, 0) = 0 " +
						"group by c.global_bill_id");
		query.setParameterList("globalBillIds", globalBillIds);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			ret.put(toInteger(values[0]), new int[] { toInteger(values[1]), toInteger(values[2]) });
		}
		return ret;
	}

	/**
	 * @return for each of the given bills, how many of its items that are not voided have each verification status;
	 * an item never verified counts as pending
	 */
	@Transactional(readOnly = true)
	public Map<Integer, Map<BillItemVerificationStatus, Integer>> getItemStatusCounts(Collection<Integer> globalBillIds) {
		Map<Integer, Map<BillItemVerificationStatus, Integer>> ret = new HashMap<Integer, Map<BillItemVerificationStatus, Integer>>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select c.global_bill_id, coalesce(v.status, 'PENDING'), count(*) " +
						"from moh_bill_patient_service_bill psb " +
						"join moh_bill_consommation c on c.consommation_id = psb.consommation_id " +
						"left join rwandaemr_bill_item_verification v on v.patient_service_bill_id = psb.patient_service_bill_id " +
						"where c.global_bill_id in (:globalBillIds) and coalesce(psb.voided, 0) = 0 and coalesce(c.voided, 0) = 0 " +
						"group by c.global_bill_id, coalesce(v.status, 'PENDING')");
		query.setParameterList("globalBillIds", globalBillIds);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			Integer globalBillId = toInteger(values[0]);
			Map<BillItemVerificationStatus, Integer> counts = ret.get(globalBillId);
			if (counts == null) {
				counts = new HashMap<BillItemVerificationStatus, Integer>();
				ret.put(globalBillId, counts);
			}
			try {
				counts.put(BillItemVerificationStatus.valueOf(values[1].toString()), toInteger(values[2]));
			}
			catch (IllegalArgumentException e) {
				log.warn("Unknown verification status of items of global bill " + globalBillId + ": " + values[1]);
			}
		}
		return ret;
	}

	/**
	 * @return the insurance of the bill, or null if the bill does not exist
	 */
	@Transactional(readOnly = true)
	public Integer getInsuranceId(Integer globalBillId) {
		Object ret = getSession().createSQLQuery(
				"select insurance_id from moh_bill_global_bill where global_bill_id = :globalBillId")
				.setParameter("globalBillId", globalBillId)
				.uniqueResult();
		return toInteger(ret);
	}

	/**
	 * @return the insurance id and insurance category of each of the given bills, by global bill id
	 */
	@Transactional(readOnly = true)
	public Map<Integer, Object[]> getInsurances(Collection<Integer> globalBillIds) {
		Map<Integer, Object[]> ret = new HashMap<Integer, Object[]>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		List<?> rows = getSession().createSQLQuery(
				"select gb.global_bill_id, gb.insurance_id, i.category from moh_bill_global_bill gb " +
						"left join moh_bill_insurance i on i.insurance_id = gb.insurance_id where gb.global_bill_id in (:ids)")
				.setParameterList("ids", globalBillIds)
				.list();
		for (Object row : rows) {
			Object[] values = (Object[]) row;
			ret.put(((Number) values[0]).intValue(), new Object[] { toInteger(values[1]),
					values[2] == null ? null : values[2].toString() });
		}
		return ret;
	}

	/**
	 * @return the reasons of the given type that are offered, by name
	 */
	@Transactional(readOnly = true)
	public List<VerificationReason> getReasons(VerificationReason.Type type) {
		List<VerificationReason> ret = new ArrayList<VerificationReason>();
		List<?> rows = getSession().createSQLQuery(
				"select reason_id, reason_type, name, retired from rwandaemr_bill_verification_reason " +
						"where reason_type = :type and retired = 0 order by name")
				.setParameter("type", type.name())
				.list();
		for (Object row : rows) {
			ret.add(toReason((Object[]) row));
		}
		return ret;
	}

	@Transactional(readOnly = true)
	public VerificationReason getReason(Integer reasonId) {
		List<?> rows = getSession().createSQLQuery(
				"select reason_id, reason_type, name, retired from rwandaemr_bill_verification_reason where reason_id = :reasonId")
				.setParameter("reasonId", reasonId)
				.list();
		return rows.isEmpty() ? null : toReason((Object[]) rows.get(0));
	}

	/**
	 * @return the reason of the given type with the given name, ignoring case, or null if there is none
	 */
	@Transactional(readOnly = true)
	public VerificationReason getReasonByName(VerificationReason.Type type, String name) {
		List<?> rows = getSession().createSQLQuery(
				"select reason_id, reason_type, name, retired from rwandaemr_bill_verification_reason " +
						"where reason_type = :type and lower(name) = lower(:name)")
				.setParameter("type", type.name())
				.setParameter("name", name)
				.list();
		return rows.isEmpty() ? null : toReason((Object[]) rows.get(0));
	}

	/**
	 * Saves a new reason, and gives it its id
	 */
	@Transactional
	public void saveReason(VerificationReason reason) {
		getSession().createSQLQuery(
				"insert into rwandaemr_bill_verification_reason (reason_type, name, retired, creator, date_created) " +
						"values (:type, :name, 0, :userId, :now)")
				.setParameter("type", reason.getType().name())
				.setParameter("name", reason.getName())
				.setParameter("userId", getUserId())
				.setParameter("now", new Date())
				.executeUpdate();
		reason.setReasonId(getReasonByName(reason.getType(), reason.getName()).getReasonId());
	}

	/**
	 * Saves the decision on the item, and records it in the audit trail of its bill
	 */
	@Transactional
	public void saveItem(BillItemVerification item) {
		Integer userId = getUserId();
		Date now = new Date();
		Number existing = (Number) getSession().createSQLQuery(
				"select count(*) from rwandaemr_bill_item_verification where patient_service_bill_id = :itemId")
				.setParameter("itemId", item.getPatientServiceBillId())
				.uniqueResult();
		if (existing.intValue() == 0) {
			getSession().createSQLQuery(
					"insert into rwandaemr_bill_item_verification (patient_service_bill_id, global_bill_id, status, " +
							"reason_id, note, creator, date_created) values (:itemId, :globalBillId, :status, :reasonId, " +
							":note, :userId, :now)")
					.setParameter("itemId", item.getPatientServiceBillId())
					.setParameter("globalBillId", item.getGlobalBillId())
					.setParameter("status", item.getStatus().name())
					.setParameter("reasonId", item.getReasonId())
					.setParameter("note", item.getNote())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.executeUpdate();
		}
		else {
			getSession().createSQLQuery(
					"update rwandaemr_bill_item_verification set status = :status, reason_id = :reasonId, note = :note, " +
							"changed_by = :userId, date_changed = :now where patient_service_bill_id = :itemId")
					.setParameter("status", item.getStatus().name())
					.setParameter("reasonId", item.getReasonId())
					.setParameter("note", item.getNote())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.setParameter("itemId", item.getPatientServiceBillId())
					.executeUpdate();
		}
		item.setChangedBy(userId);
		item.setDateChanged(now);
		saveEvent(item.getGlobalBillId(), item.getPatientServiceBillId(), item.getStatus().name(), item.getReasonId(),
				item.getNote(), userId, now);
	}

	/**
	 * Moves the bill to the given step, and records it in the audit trail of the bill
	 */
	@Transactional
	public void saveStage(Integer globalBillId, BillVerificationStage stage) {
		Integer userId = getUserId();
		Date now = new Date();
		Number existing = (Number) getSession().createSQLQuery(
				"select count(*) from rwandaemr_bill_verification where global_bill_id = :globalBillId")
				.setParameter("globalBillId", globalBillId)
				.uniqueResult();
		if (existing.intValue() == 0) {
			getSession().createSQLQuery(
					"insert into rwandaemr_bill_verification (global_bill_id, stage, creator, date_created) " +
							"values (:globalBillId, :stage, :userId, :now)")
					.setParameter("globalBillId", globalBillId)
					.setParameter("stage", stage.name())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.executeUpdate();
		}
		else {
			getSession().createSQLQuery(
					"update rwandaemr_bill_verification set stage = :stage, changed_by = :userId, date_changed = :now " +
							"where global_bill_id = :globalBillId")
					.setParameter("stage", stage.name())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.setParameter("globalBillId", globalBillId)
					.executeUpdate();
		}
		saveEvent(globalBillId, null, stage.name(), null, null, userId, now);
	}

	/**
	 * @return the number of entries in the audit trail of each of the given bills that has any
	 */
	@Transactional(readOnly = true)
	public Map<Integer, Integer> getEventCounts(Collection<Integer> globalBillIds) {
		Map<Integer, Integer> ret = new HashMap<Integer, Integer>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		List<?> rows = getSession().createSQLQuery(
				"select global_bill_id, count(*) from rwandaemr_bill_verification_event where global_bill_id in (:ids) " +
						"group by global_bill_id")
				.setParameterList("ids", globalBillIds)
				.list();
		for (Object row : rows) {
			Object[] values = (Object[]) row;
			ret.put(((Number) values[0]).intValue(), ((Number) values[1]).intValue());
		}
		return ret;
	}

	/**
	 * @return the audit trail of the bill, oldest first
	 */
	@Transactional(readOnly = true)
	public List<VerificationEvent> getEvents(Integer globalBillId) {
		List<VerificationEvent> ret = new ArrayList<VerificationEvent>();
		List<?> rows = getSession().createSQLQuery(
				"select e.global_bill_id, e.patient_service_bill_id, e.status, r.name, e.note, e.creator, u.username, " +
						"e.date_created from rwandaemr_bill_verification_event e " +
						"left join rwandaemr_bill_verification_reason r on r.reason_id = e.reason_id " +
						"left join users u on u.user_id = e.creator " +
						"where e.global_bill_id = :globalBillId order by e.date_created, e.bill_verification_event_id")
				.setParameter("globalBillId", globalBillId)
				.list();
		for (Object row : rows) {
			Object[] values = (Object[]) row;
			Integer itemId = toInteger(values[1]);
			String status = (String) values[2];
			ret.add(new VerificationEvent(toInteger(values[0]), itemId,
					itemId == null ? null : BillItemVerificationStatus.valueOf(status),
					itemId == null ? BillVerificationStage.valueOf(status) : null,
					(String) values[3], (String) values[4], toInteger(values[5]), (String) values[6], (Date) values[7]));
		}
		return ret;
	}

	private void saveEvent(Integer globalBillId, Integer itemId, String status, Integer reasonId, String note,
						   Integer userId, Date now) {
		getSession().createSQLQuery(
				"insert into rwandaemr_bill_verification_event (global_bill_id, patient_service_bill_id, status, " +
						"reason_id, note, creator, date_created) values (:globalBillId, :itemId, :status, :reasonId, " +
						":note, :userId, :now)")
				.setParameter("globalBillId", globalBillId)
				.setParameter("itemId", itemId)
				.setParameter("status", status)
				.setParameter("reasonId", reasonId)
				.setParameter("note", note)
				.setParameter("userId", userId)
				.setParameter("now", now)
				.executeUpdate();
	}

	private VerificationReason toReason(Object[] values) {
		Object retired = values[3];
		boolean isRetired = retired instanceof Boolean ? (Boolean) retired
				: retired instanceof Number && ((Number) retired).intValue() != 0;
		return new VerificationReason(toInteger(values[0]), VerificationReason.Type.valueOf((String) values[1]),
				(String) values[2], isRetired);
	}

	private Integer getUserId() {
		User user = Context.getAuthenticatedUser();
		return user == null ? null : user.getUserId();
	}

	private Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private Session getSession() {
		return dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
	}
}
