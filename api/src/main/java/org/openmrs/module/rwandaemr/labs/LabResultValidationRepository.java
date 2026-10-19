package org.openmrs.module.rwandaemr.labs;

import org.hibernate.query.NativeQuery;
import org.openmrs.Order;
import org.openmrs.User;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Records each validation decision on an order's lab results, as an audit trail
 */
@Component
public class LabResultValidationRepository {

	public enum Action {
		VALIDATED, RETURNED
	}

	private final DbSessionFactory dbSessionFactory;

	public LabResultValidationRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @param resultsEnteredBy the ids of the users who entered or changed the results, comma separated
	 */
	public void saveDecision(Order order, Action action, String reason, String resultsEnteredBy, User decidedBy) {
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"insert into rwandaemr_lab_result_validation " +
						"(order_id, action, reason, results_entered_by, creator, date_created, uuid) " +
						"values (:orderId, :action, :reason, :resultsEnteredBy, :creator, :dateCreated, :uuid)");
		query.setParameter("orderId", order.getOrderId());
		query.setParameter("action", action.name());
		query.setParameter("reason", reason);
		query.setParameter("resultsEnteredBy", resultsEnteredBy);
		query.setParameter("creator", decidedBy.getUserId());
		query.setParameter("dateCreated", new Date());
		query.setParameter("uuid", UUID.randomUUID().toString());
		query.executeUpdate();
	}
}
