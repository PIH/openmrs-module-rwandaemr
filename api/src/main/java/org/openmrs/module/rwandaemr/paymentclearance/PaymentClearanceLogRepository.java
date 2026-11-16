package org.openmrs.module.rwandaemr.paymentclearance;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentClearanceLogRepository {

	private final DbSessionFactory dbSessionFactory;

	public PaymentClearanceLogRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	@Transactional
	public void saveLogEntry(PaymentClearanceLogEntry entry) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"insert into payment_clearance_sms_log (" +
						"global_bill_id, patient_id, patient_identifier, phone_number, message_content, provider_message_id, " +
						"request_payload, response_code, response_body, status, error_message, creator, date_created" +
						") values (" +
						":globalBillId, :patientId, :patientIdentifier, :phoneNumber, :messageContent, :providerMessageId, " +
						":requestPayload, :responseCode, :responseBody, :status, :errorMessage, :creator, :dateCreated" +
						")"
		);
		query.setParameter("globalBillId", entry.getGlobalBillId());
		query.setParameter("patientId", entry.getPatientId());
		query.setParameter("patientIdentifier", entry.getPatientIdentifier());
		query.setParameter("phoneNumber", entry.getPhoneNumber());
		query.setParameter("messageContent", entry.getMessageContent());
		query.setParameter("providerMessageId", entry.getProviderMessageId());
		query.setParameter("requestPayload", entry.getRequestPayload());
		query.setParameter("responseCode", entry.getResponseCode());
		query.setParameter("responseBody", entry.getResponseBody());
		query.setParameter("status", entry.getStatus());
		query.setParameter("errorMessage", entry.getErrorMessage());
		query.setParameter("creator", entry.getCreator());
		query.setParameter("dateCreated", entry.getDateCreated());
		query.executeUpdate();
	}

	@Transactional(readOnly = true)
	public Map<Integer, PaymentClearanceLogEntry> getLatestByGlobalBillIds(List<Integer> globalBillIds) {
		Map<Integer, PaymentClearanceLogEntry> ret = new HashMap<Integer, PaymentClearanceLogEntry>();
		if (globalBillIds == null || globalBillIds.isEmpty()) {
			return ret;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select l.global_bill_id, l.phone_number, l.status, l.error_message, l.provider_message_id, l.date_created " +
						"from payment_clearance_sms_log l " +
						"inner join (" +
						"  select global_bill_id, max(payment_clearance_sms_log_id) latest_id " +
						"  from payment_clearance_sms_log where global_bill_id in (:globalBillIds) group by global_bill_id" +
						") latest on latest.latest_id = l.payment_clearance_sms_log_id"
		);
		query.setParameterList("globalBillIds", globalBillIds);
		List<?> rows = query.list();
		for (Object row : rows) {
			Object[] values = (Object[]) row;
			PaymentClearanceLogEntry entry = new PaymentClearanceLogEntry();
			entry.setGlobalBillId(values[0] == null ? null : ((Number) values[0]).intValue());
			entry.setPhoneNumber((String) values[1]);
			entry.setStatus((String) values[2]);
			entry.setErrorMessage((String) values[3]);
			entry.setProviderMessageId((String) values[4]);
			entry.setDateCreated((Date) values[5]);
			if (entry.getGlobalBillId() != null) {
				ret.put(entry.getGlobalBillId(), entry);
			}
		}
		return ret;
	}
}
