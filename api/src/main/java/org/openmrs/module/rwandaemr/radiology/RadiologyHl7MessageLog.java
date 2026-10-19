package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a copy of every HL7 message exchanged with PACS, so that what PACS sent or was sent can be checked later, for
 * example whether a study's images and viewer link have arrived, or why a result was refused.  Each message is saved in
 * its own transaction, so it is kept even when processing the message fails.
 */
@Component
public class RadiologyHl7MessageLog {

	private static final int MAX_ERROR_LENGTH = 10000;

	private final DbSessionFactory dbSessionFactory;

	public RadiologyHl7MessageLog(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * Records a result message received from PACS, and whether it was processed
	 * @param error why the message could not be processed, or null if it was
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void logIncoming(String message, String ackMessage, Exception error) {
		RadiologyHl7Message entry = new RadiologyHl7Message();
		entry.setDirection(RadiologyHl7Message.DIRECTION_IN);
		entry.setMessageType(getField(message, "MSH", 0, 9));
		entry.setMessageControlId(getField(message, "MSH", 0, 10));
		String placerNumber = getComponent(getField(message, "OBR", 0, 2), 0);
		entry.setAccessionNumber(placerNumber);
		entry.setOrderNumbers(placerNumber);
		entry.setResultStatus(getField(message, "OBX", 0, 11));
		entry.setHasImageLink(HL7Utils.getViewerUrl(getField(message, "OBX", 1, 5)) != null);
		entry.setStatus(error == null ? RadiologyHl7Message.STATUS_PROCESSED : RadiologyHl7Message.STATUS_FAILED);
		entry.setErrorMessage(error == null ? null : getErrorMessage(error));
		entry.setMessage(message);
		entry.setAckMessage(ackMessage);
		save(entry);
	}

	/**
	 * Records an order message sent to PACS, and how PACS answered it
	 * @param status one of the outgoing statuses of {@link RadiologyHl7Message}
	 * @param error why PACS did not accept it, or null if it did
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void logOutgoing(List<TestOrder> orders, String accessionNumber, String orderControl, String message,
							String ackMessage, String status, String error) {
		RadiologyHl7Message entry = new RadiologyHl7Message();
		entry.setDirection(RadiologyHl7Message.DIRECTION_OUT);
		entry.setMessageType(getField(message, "MSH", 0, 9));
		entry.setMessageControlId(getField(message, "MSH", 0, 10));
		entry.setOrderControl(orderControl);
		entry.setAccessionNumber(accessionNumber);
		List<String> orderNumbers = new ArrayList<String>();
		if (orders != null) {
			for (TestOrder order : orders) {
				orderNumbers.add(order.getOrderNumber());
			}
		}
		entry.setOrderNumbers(StringUtils.join(orderNumbers, ","));
		entry.setStatus(status);
		entry.setErrorMessage(StringUtils.abbreviate(error, MAX_ERROR_LENGTH));
		entry.setMessage(message);
		entry.setAckMessage(ackMessage);
		save(entry);
	}

	/**
	 * @return the messages exchanged with PACS about the given order or the study it was sent in, newest first
	 */
	@Transactional(readOnly = true)
	public List<RadiologyHl7Message> getMessagesForOrder(Order order, int maxResults) {
		List<RadiologyHl7Message> ret = new ArrayList<RadiologyHl7Message>();
		Set<String> numbers = new LinkedHashSet<String>();
		if (StringUtils.isNotBlank(order.getOrderNumber())) {
			numbers.add(order.getOrderNumber());
		}
		if (StringUtils.isNotBlank(order.getAccessionNumber())) {
			numbers.add(order.getAccessionNumber());
		}
		if (numbers.isEmpty()) {
			return ret;
		}
		StringBuilder sql = new StringBuilder(
				"select radiology_hl7_message_id, direction, message_type, message_control_id, order_control, " +
						"result_status, accession_number, order_numbers, has_image_link, status, error_message, message, " +
						"ack_message, creator, date_created from rwandaemr_radiology_hl7_message " +
						"where accession_number in (:numbers)");
		List<String> patterns = new ArrayList<String>();
		for (String number : numbers) {
			sql.append(" or concat(',', order_numbers, ',') like :pattern").append(patterns.size());
			patterns.add("%," + number + ",%");
		}
		sql.append(" order by radiology_hl7_message_id desc");
		NativeQuery<?> query = getSession().createSQLQuery(sql.toString());
		query.setParameterList("numbers", numbers);
		for (int i = 0; i < patterns.size(); i++) {
			query.setParameter("pattern" + i, patterns.get(i));
		}
		query.setMaxResults(maxResults);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			RadiologyHl7Message entry = new RadiologyHl7Message();
			entry.setId(toInteger(values[0]));
			entry.setDirection((String) values[1]);
			entry.setMessageType((String) values[2]);
			entry.setMessageControlId((String) values[3]);
			entry.setOrderControl((String) values[4]);
			entry.setResultStatus((String) values[5]);
			entry.setAccessionNumber((String) values[6]);
			entry.setOrderNumbers((String) values[7]);
			entry.setHasImageLink(toBoolean(values[8]));
			entry.setStatus((String) values[9]);
			entry.setErrorMessage((String) values[10]);
			entry.setMessage((String) values[11]);
			entry.setAckMessage((String) values[12]);
			entry.setCreator(toInteger(values[13]));
			entry.setDateCreated((Date) values[14]);
			ret.add(entry);
		}
		return ret;
	}

	private void save(RadiologyHl7Message entry) {
		User user = Context.getAuthenticatedUser();
		NativeQuery<?> query = getSession().createSQLQuery(
				"insert into rwandaemr_radiology_hl7_message (" +
						"direction, message_type, message_control_id, order_control, result_status, accession_number, " +
						"order_numbers, has_image_link, status, error_message, message, ack_message, creator, date_created" +
						") values (" +
						":direction, :messageType, :messageControlId, :orderControl, :resultStatus, :accessionNumber, " +
						":orderNumbers, :hasImageLink, :status, :errorMessage, :message, :ackMessage, :creator, :dateCreated" +
						")");
		query.setParameter("direction", entry.getDirection());
		query.setParameter("messageType", limit(entry.getMessageType(), 20));
		query.setParameter("messageControlId", limit(entry.getMessageControlId(), 50));
		query.setParameter("orderControl", limit(entry.getOrderControl(), 10));
		query.setParameter("resultStatus", limit(entry.getResultStatus(), 10));
		query.setParameter("accessionNumber", limit(entry.getAccessionNumber(), 50));
		query.setParameter("orderNumbers", limit(entry.getOrderNumbers(), 1000));
		query.setParameter("hasImageLink", entry.getHasImageLink());
		query.setParameter("status", entry.getStatus());
		query.setParameter("errorMessage", entry.getErrorMessage());
		query.setParameter("message", entry.getMessage());
		query.setParameter("ackMessage", entry.getAckMessage());
		query.setParameter("creator", user == null ? null : user.getUserId());
		query.setParameter("dateCreated", new Date());
		query.executeUpdate();
	}

	private Session getSession() {
		return dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
	}

	/**
	 * @return the given field of the given occurrence (0 for the first) of a segment of an HL7 message, or null if there
	 * is none.  Field numbers are as in the HL7 standard, so MSH-9 is the message type and OBR-2 the placer order number.
	 */
	static String getField(String message, String segmentName, int occurrence, int fieldNumber) {
		if (StringUtils.isBlank(message)) {
			return null;
		}
		int found = 0;
		for (String segment : message.split("[\r\n]+")) {
			if (!segment.startsWith(segmentName + "|")) {
				continue;
			}
			if (found++ < occurrence) {
				continue;
			}
			String[] fields = segment.split("\\|", -1);
			// In MSH the field separator itself is MSH-1, so the remaining fields are numbered one higher
			int index = "MSH".equals(segmentName) ? fieldNumber - 1 : fieldNumber;
			return index < fields.length ? StringUtils.trimToNull(fields[index]) : null;
		}
		return null;
	}

	/**
	 * @return the given component (0 for the first) of an HL7 field, or null if there is none
	 */
	static String getComponent(String field, int component) {
		if (field == null) {
			return null;
		}
		String[] components = field.split("\\^", -1);
		return component < components.length ? StringUtils.trimToNull(components[component]) : null;
	}

	private static String getErrorMessage(Exception error) {
		StringBuilder sb = new StringBuilder();
		Throwable t = error;
		while (t != null && sb.length() < MAX_ERROR_LENGTH) {
			if (sb.length() > 0) {
				sb.append(" | caused by: ");
			}
			sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
			t = t.getCause() == t ? null : t.getCause();
		}
		return StringUtils.abbreviate(sb.toString(), MAX_ERROR_LENGTH);
	}

	private static String limit(String value, int maxLength) {
		return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private static Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private static Boolean toBoolean(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		return ((Number) value).intValue() != 0;
	}
}
