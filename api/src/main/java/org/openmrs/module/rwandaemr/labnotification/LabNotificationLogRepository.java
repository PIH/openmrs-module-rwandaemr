package org.openmrs.module.rwandaemr.labnotification;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Component
public class LabNotificationLogRepository {

	private final DbSessionFactory dbSessionFactory;

	public LabNotificationLogRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	@Transactional(readOnly = true)
	public boolean hasActiveNotificationForOrder(Integer orderId) {
		if (orderId == null) {
			return false;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select count(1) from lab_notification_log " +
						"where order_id = :orderId and status in ('QUEUED', 'DELIVRD', 'ACCEPTD')"
		);
		query.setParameter("orderId", orderId);
		Number count = (Number) query.uniqueResult();
		return count != null && count.intValue() > 0;
	}

	@Transactional
	public void saveLogEntry(LabNotificationLogEntry entry) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"insert into lab_notification_log (" +
						"patient_id, order_id, order_uuid, phone_number, message_content, provider_message_id, " +
						"request_payload, response_code, response_body, status, error_message, dlr_status, dlr_level, " +
						"creator, date_created, date_changed" +
						") values (" +
						":patientId, :orderId, :orderUuid, :phoneNumber, :messageContent, :providerMessageId, " +
						":requestPayload, :responseCode, :responseBody, :status, :errorMessage, :dlrStatus, :dlrLevel, " +
						":creator, :dateCreated, :dateChanged" +
						")"
		);
		query.setParameter("patientId", entry.getPatientId());
		query.setParameter("orderId", entry.getOrderId());
		query.setParameter("orderUuid", entry.getOrderUuid());
		query.setParameter("phoneNumber", entry.getPhoneNumber());
		query.setParameter("messageContent", entry.getMessageContent());
		query.setParameter("providerMessageId", entry.getProviderMessageId());
		query.setParameter("requestPayload", entry.getRequestPayload());
		query.setParameter("responseCode", entry.getResponseCode());
		query.setParameter("responseBody", entry.getResponseBody());
		query.setParameter("status", entry.getStatus());
		query.setParameter("errorMessage", entry.getErrorMessage());
		query.setParameter("dlrStatus", entry.getDlrStatus());
		query.setParameter("dlrLevel", entry.getDlrLevel());
		query.setParameter("creator", entry.getCreator());
		query.setParameter("dateCreated", entry.getDateCreated());
		query.setParameter("dateChanged", entry.getDateChanged());
		query.executeUpdate();
	}

	@Transactional
	public boolean updateDeliveryReport(String providerMessageId, String status, String level) {
		if (providerMessageId == null) {
			return false;
		}
		Date now = new Date();
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"update lab_notification_log set status = :status, dlr_status = :status, dlr_level = :level, " +
						"date_changed = :dateChanged where provider_message_id = :providerMessageId"
		);
		query.setParameter("status", status);
		query.setParameter("level", level);
		query.setParameter("dateChanged", now);
		query.setParameter("providerMessageId", providerMessageId);
		return query.executeUpdate() > 0;
	}
}
