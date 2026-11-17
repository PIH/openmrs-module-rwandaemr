package org.openmrs.module.rwandaemr.sms;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Searches the SMS sent by RwandaEMR: lab result notifications and payment clearance messages
 */
@Component
public class SmsLogRepository {

	public static final String SOURCE_ALL = "ALL";
	public static final String SOURCE_LAB_RESULT = "LAB_RESULT";
	public static final String SOURCE_PAYMENT_CLEARANCE = "PAYMENT_CLEARANCE";
	public static final String STATUS_ALL = "ALL";

	private final DbSessionFactory dbSessionFactory;

	public SmsLogRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @param page the page to return, starting at 1
	 */
	@Transactional(readOnly = true)
	public SmsLogSearchResult search(SmsLogSearchCriteria criteria, int page, int pageSize) {
		SmsLogSearchCriteria c = criteria == null ? new SmsLogSearchCriteria() : criteria;
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		String baseSql = buildBaseSql();
		String whereSql = buildWhereSql(c);

		NativeQuery<?> countQuery = session.createSQLQuery("select count(1) from (" + baseSql + ") sms_logs" + whereSql);
		applyParameters(countQuery, c);
		Number count = (Number) countQuery.uniqueResult();
		int totalCount = count == null ? 0 : count.intValue();

		NativeQuery<?> query = session.createSQLQuery(
				"select * from (" + baseSql + ") sms_logs" + whereSql + " order by date_created desc, source_id desc");
		applyParameters(query, c);
		query.setFirstResult(Math.max(page - 1, 0) * pageSize);
		query.setMaxResults(pageSize);

		List<SmsLogEntry> logs = new ArrayList<>();
		for (Object row : query.list()) {
			logs.add(toLogEntry((Object[]) row));
		}
		return new SmsLogSearchResult(logs, totalCount);
	}

	private String buildBaseSql() {
		return "select 'LAB_RESULT' source_type, lab_notification_log_id source_id, patient_id, "
				+ "cast(null as char) patient_identifier, order_uuid source_reference, phone_number, message_content, "
				+ "provider_message_id, request_payload, response_code, response_body, status, error_message, "
				+ "dlr_status, dlr_level, date_created, date_changed from lab_notification_log "
				+ "union all "
				+ "select 'PAYMENT_CLEARANCE' source_type, payment_clearance_sms_log_id source_id, patient_id, "
				+ "patient_identifier, cast(global_bill_id as char) source_reference, phone_number, message_content, "
				+ "provider_message_id, request_payload, response_code, response_body, status, error_message, "
				+ "dlr_status, dlr_level, date_created, date_changed from payment_clearance_sms_log";
	}

	private String buildWhereSql(SmsLogSearchCriteria criteria) {
		List<String> clauses = new ArrayList<>();
		if (criteria.getStartDate() != null) {
			clauses.add("date_created >= :startDate");
		}
		if (criteria.getEndDate() != null) {
			clauses.add("date_created <= :endDate");
		}
		if (StringUtils.isNotBlank(criteria.getSourceType()) && !SOURCE_ALL.equals(criteria.getSourceType())) {
			clauses.add("source_type = :sourceType");
		}
		if (StringUtils.isNotBlank(criteria.getStatus()) && !STATUS_ALL.equals(criteria.getStatus())) {
			clauses.add("(status = :status or dlr_status = :status)");
		}
		if (criteria.getResponseCode() != null) {
			clauses.add("response_code = :responseCode");
		}
		if (StringUtils.isNotBlank(criteria.getQuery())) {
			clauses.add("(phone_number like :query or patient_identifier like :query or source_reference like :query "
					+ "or provider_message_id like :query or message_content like :query or error_message like :query)");
		}
		return clauses.isEmpty() ? "" : " where " + StringUtils.join(clauses, " and ");
	}

	private void applyParameters(NativeQuery<?> query, SmsLogSearchCriteria criteria) {
		if (criteria.getStartDate() != null) {
			query.setParameter("startDate", criteria.getStartDate());
		}
		if (criteria.getEndDate() != null) {
			query.setParameter("endDate", criteria.getEndDate());
		}
		if (StringUtils.isNotBlank(criteria.getSourceType()) && !SOURCE_ALL.equals(criteria.getSourceType())) {
			query.setParameter("sourceType", criteria.getSourceType());
		}
		if (StringUtils.isNotBlank(criteria.getStatus()) && !STATUS_ALL.equals(criteria.getStatus())) {
			query.setParameter("status", criteria.getStatus());
		}
		if (criteria.getResponseCode() != null) {
			query.setParameter("responseCode", criteria.getResponseCode());
		}
		if (StringUtils.isNotBlank(criteria.getQuery())) {
			query.setParameter("query", "%" + criteria.getQuery().trim() + "%");
		}
	}

	private SmsLogEntry toLogEntry(Object[] row) {
		SmsLogEntry entry = new SmsLogEntry();
		entry.setSourceType((String) row[0]);
		entry.setSourceId(toInteger(row[1]));
		entry.setPatientId(toInteger(row[2]));
		entry.setPatientIdentifier((String) row[3]);
		entry.setReference(row[4] == null ? null : row[4].toString());
		entry.setPhoneNumber((String) row[5]);
		entry.setMessageContent((String) row[6]);
		entry.setProviderMessageId((String) row[7]);
		entry.setRequestPayload((String) row[8]);
		entry.setResponseCode(toInteger(row[9]));
		entry.setResponseBody((String) row[10]);
		entry.setStatus((String) row[11]);
		entry.setErrorMessage((String) row[12]);
		entry.setDlrStatus((String) row[13]);
		entry.setDlrLevel((String) row[14]);
		entry.setDateCreated((Date) row[15]);
		entry.setDateChanged((Date) row[16]);
		return entry;
	}

	private Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}
}
