package org.openmrs.module.rwandaemr.billing;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SQLQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the transfers of the visit of a bill from the tables of the Transfer app, which rwandaemr does not depend on:
 * nothing is shown when they are missing.
 * <ul>
 *     <li>Received: the HIE transfer ids recorded on the encounters of the visit (the Transfer ID question at
 *     registration), with this facility's copy of each transfer and its counter-referral when there is one</li>
 *     <li>Sent: the transfers made from this facility whose decision to transfer falls within the visit</li>
 * </ul>
 */
public class VisitTransferLookup {

	/** The Transfer app's global property with the concept of the Transfer ID question, and its default */
	public static final String GP_RECEIVED_TRANSFER_CONCEPT = "transferapp.receivedTransferConceptUuid";
	public static final String DEFAULT_RECEIVED_TRANSFER_CONCEPT = "076b04e5-d62d-47da-83e2-0da6e7a119b4";
	public static final String GP_RWANDAEMR_TRANSFER_ID_CONCEPT = "rwandaemr.hie.transfer_id";

	static final String TRANSFERS = "transfers";
	static final String FEEDBACK = "transfer_referral_feedback";
	static final String FACILITIES = "transfer_receiving_facilities";

	/**
	 * Runs a native query with named parameters; a collection parameter is a list
	 */
	public interface SqlRunner {
		List<Object[]> list(String sql, Map<String, Object> parameters);
	}

	private final Log log = LogFactory.getLog(getClass());

	private final SqlRunner sql;
	private final Integer transferIdConceptId;
	private Set<String> tables;

	/**
	 * @param transferIdConceptId the concept of the Transfer ID question, or null if it is not set up
	 */
	public VisitTransferLookup(SqlRunner sql, Integer transferIdConceptId) {
		this.sql = sql;
		this.transferIdConceptId = transferIdConceptId;
	}

	/**
	 * @return a runner on the current Hibernate session
	 */
	public static SqlRunner runner(final DbSessionFactory dbSessionFactory) {
		return new SqlRunner() {
			@Override
			public List<Object[]> list(String query, Map<String, Object> parameters) {
				SQLQuery q = dbSessionFactory.getCurrentSession().createSQLQuery(query);
				for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
					if (parameter.getValue() instanceof Collection) {
						q.setParameterList(parameter.getKey(), (Collection<?>) parameter.getValue());
					}
					else {
						q.setParameter(parameter.getKey(), parameter.getValue());
					}
				}
				List<Object[]> ret = new ArrayList<Object[]>();
				for (Object row : q.list()) {
					ret.add(row instanceof Object[] ? (Object[]) row : new Object[] { row });
				}
				return ret;
			}
		};
	}

	/**
	 * @return true if the Transfer app's tables are there
	 */
	public boolean isAvailable() {
		return getTables().contains(TRANSFERS);
	}

	/**
	 * @param patientId the patient of the bill
	 * @param scope the visit of the bill, or the days of its admission
	 * @return the transfers of the visit, or null when the Transfer app's tables are missing
	 */
	public VisitTransfers getTransfers(Integer patientId, BillJourneyScope scope) {
		if (patientId == null || scope == null || !isAvailable()) {
			return null;
		}
		VisitTransfers ret = new VisitTransfers(scope.getStart(), scope.getEnd());
		try {
			addReceived(ret, patientId, scope);
			addSent(ret, patientId, scope);
		}
		catch (RuntimeException e) {
			log.warn("Unable to read the transfers of patient " + patientId + ": " + e.getMessage());
			return null;
		}
		return ret;
	}

	private void addReceived(VisitTransfers transfers, Integer patientId, BillJourneyScope scope) {
		if (transferIdConceptId == null) {
			return;
		}
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("conceptId", transferIdConceptId);
		List<Object[]> rows = sql.list("select o.value_text, o.date_created, u.username from obs o "
				+ "join encounter e on e.encounter_id = o.encounter_id and e.voided = 0 "
				+ "left join users u on u.user_id = o.creator "
				+ "where o.voided = 0 and o.concept_id = :conceptId and o.value_text is not null "
				+ "and " + scope.toSqlWhere(patientId) + " order by o.date_created", parameters);
		Map<String, VisitTransfers.Received> byId = new LinkedHashMap<String, VisitTransfers.Received>();
		for (Object[] row : rows) {
			String hieTransferId = StringUtils.trimToNull(string(row, 0));
			if (hieTransferId == null || byId.containsKey(key(hieTransferId))) {
				continue;
			}
			VisitTransfers.Received received = new VisitTransfers.Received(hieTransferId, date(row, 1));
			received.setRecordedBy(string(row, 2));
			byId.put(key(hieTransferId), received);
		}
		if (byId.isEmpty()) {
			return;
		}
		List<String> ids = new ArrayList<String>();
		for (VisitTransfers.Received received : byId.values()) {
			ids.add(received.getHieTransferId());
		}
		parameters = new HashMap<String, Object>();
		parameters.put("patientId", patientId);
		parameters.put("ids", ids);
		for (Object[] row : sql.list("select t.hie_transfer_id, t.sending_facility, t.referring_unit, "
				+ "t.decision_to_transfer_at, t.transfer_type, t.form_kind, t.reason_for_transfer, t.diagnosis, t.uuid "
				+ "from " + TRANSFERS + " t where t.voided = 0 and t.patient_id = :patientId and t.hie_transfer_id in (:ids) "
				+ "order by t.transfer_id", parameters)) {
			VisitTransfers.Received received = byId.get(key(string(row, 0)));
			if (received == null || received.isLocalCopy()) {
				continue;
			}
			received.setLocalCopy(true);
			received.setSendingFacility(string(row, 1));
			received.setReferringUnit(string(row, 2));
			received.setDecisionToTransferAt(date(row, 3));
			received.setTransferType(string(row, 4));
			received.setFormKind(string(row, 5));
			received.setReason(string(row, 6));
			received.setDiagnosis(string(row, 7));
			received.setLocalUuid(string(row, 8));
		}
		if (getTables().contains(FEEDBACK)) {
			StringBuilder query = new StringBuilder("select f.hie_transfer_id, f.completed, f.completed_at from ")
					.append(FEEDBACK).append(" f where f.voided = 0 and f.patient_id = :patientId and (f.hie_transfer_id in (:ids)");
			if (scope.getVisitId() != null) {
				query.append(" or f.visit_id = :visitId");
				parameters.put("visitId", scope.getVisitId());
			}
			query.append(") order by f.transfer_referral_feedback_id");
			for (Object[] row : sql.list(query.toString(), parameters)) {
				VisitTransfers.Received received = byId.get(key(string(row, 0)));
				if (received == null && byId.size() == 1) {
					// Feedback on the visit itself, without the transfer id
					received = byId.values().iterator().next();
				}
				if (received != null && bool(row, 1)) {
					received.setCounterReferralCompleted(true);
					received.setCounterReferralCompletedAt(date(row, 2));
				}
			}
		}
		transfers.getReceived().addAll(byId.values());
	}

	private void addSent(VisitTransfers transfers, Integer patientId, BillJourneyScope scope) {
		if (scope.getStart() == null || scope.getEnd() == null) {
			return;
		}
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("patientId", patientId);
		parameters.put("start", scope.getStart());
		parameters.put("end", scope.getEnd());
		String facilityName = getTables().contains(FACILITIES)
				? "(select max(rf.facility_name) from " + FACILITIES + " rf where rf.facility_code = t.receiving_facility_code)"
				: "null";
		Set<String> receivedIds = new HashSet<String>();
		for (VisitTransfers.Received received : transfers.getReceived()) {
			receivedIds.add(key(received.getHieTransferId()));
		}
		for (Object[] row : sql.list("select t.hie_transfer_id, coalesce(t.decision_to_transfer_at, t.date_created), u.username, "
				+ "coalesce(" + facilityName + ", t.receiving_facility_code), t.receiving_service, t.transfer_type, "
				+ "t.form_kind, t.local_approval_status, t.approved_at, t.hie_sent, t.uuid "
				+ "from " + TRANSFERS + " t left join users u on u.user_id = t.creator "
				+ "where t.voided = 0 and t.patient_id = :patientId and coalesce(t.received_from_hie, 0) = 0 "
				+ "and coalesce(t.decision_to_transfer_at, t.date_created) between :start and :end "
				+ "order by coalesce(t.decision_to_transfer_at, t.date_created), t.transfer_id", parameters)) {
			String hieTransferId = StringUtils.trimToNull(string(row, 0));
			if (hieTransferId != null && receivedIds.contains(key(hieTransferId))) {
				// The local copy of a transfer received from the HIE
				continue;
			}
			VisitTransfers.Sent sent = new VisitTransfers.Sent();
			sent.setHieTransferId(hieTransferId);
			sent.setUuid(string(row, 10));
			sent.setDecisionToTransferAt(date(row, 1));
			sent.setCreatedBy(string(row, 2));
			sent.setReceivingFacility(string(row, 3));
			sent.setReceivingService(string(row, 4));
			sent.setTransferType(string(row, 5));
			sent.setFormKind(string(row, 6));
			sent.setApprovalStatus(string(row, 7));
			sent.setApprovedAt(date(row, 8));
			sent.setHieSent(bool(row, 9));
			transfers.getSent().add(sent);
		}
	}

	private Set<String> getTables() {
		if (tables == null) {
			tables = new HashSet<String>();
			Map<String, Object> parameters = new HashMap<String, Object>();
			parameters.put("names", java.util.Arrays.asList(TRANSFERS, FEEDBACK, FACILITIES));
			try {
				for (Object[] row : sql.list("select table_name from information_schema.tables "
						+ "where table_schema = database() and table_name in (:names)", parameters)) {
					String name = string(row, 0);
					if (name != null) {
						tables.add(name.toLowerCase(Locale.ROOT));
					}
				}
			}
			catch (RuntimeException e) {
				log.warn("Unable to check the tables of the Transfer app: " + e.getMessage());
			}
		}
		return tables;
	}

	private static String key(String hieTransferId) {
		return hieTransferId == null ? "" : hieTransferId.trim().toLowerCase(Locale.ROOT);
	}

	private static String string(Object[] row, int index) {
		Object value = row.length > index ? row[index] : null;
		return value == null ? null : value.toString();
	}

	private static Date date(Object[] row, int index) {
		Object value = row.length > index ? row[index] : null;
		return value instanceof Date ? (Date) value : null;
	}

	private static boolean bool(Object[] row, int index) {
		Object value = row.length > index ? row[index] : null;
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue() != 0;
		}
		return value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
	}
}
