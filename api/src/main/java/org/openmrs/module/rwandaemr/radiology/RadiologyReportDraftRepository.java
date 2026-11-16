package org.openmrs.module.rwandaemr.radiology;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Radiology reports saved as draft.  They are kept apart from the patient's observations, which clinicians can see, and
 * there is at most one draft for each order.
 */
@Component
public class RadiologyReportDraftRepository {

	private final DbSessionFactory dbSessionFactory;

	public RadiologyReportDraftRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @return the draft report of the order, or null if there is none
	 */
	@Transactional(readOnly = true)
	public Draft getDraft(Integer orderId) {
		List<?> rows = getSession().createSQLQuery(
				"select d.report_text, d.provider_id, coalesce(d.date_changed, d.date_created), " +
						"coalesce(d.changed_by, d.creator) from rwandaemr_radiology_report_draft d where d.order_id = :orderId")
				.setParameter("orderId", orderId)
				.list();
		if (rows.isEmpty()) {
			return null;
		}
		Object[] values = (Object[]) rows.get(0);
		Draft draft = new Draft();
		draft.setOrderId(orderId);
		draft.setText((String) values[0]);
		draft.setProviderId(values[1] == null ? null : ((Number) values[1]).intValue());
		draft.setLastSaved((Date) values[2]);
		draft.setLastSavedBy(values[3] == null ? null : ((Number) values[3]).intValue());
		return draft;
	}

	/**
	 * @return which of the given orders have a draft report
	 */
	@Transactional(readOnly = true)
	public Set<Integer> getOrdersWithDrafts(Collection<Integer> orderIds) {
		Set<Integer> ret = new HashSet<Integer>();
		if (orderIds == null || orderIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select order_id from rwandaemr_radiology_report_draft where order_id in (:orderIds)");
		query.setParameterList("orderIds", orderIds);
		for (Object orderId : query.list()) {
			ret.add(((Number) orderId).intValue());
		}
		return ret;
	}

	/**
	 * Saves the draft report of the order, replacing any earlier draft
	 */
	@Transactional
	public void saveDraft(Integer orderId, String text, Integer providerId) {
		User user = Context.getAuthenticatedUser();
		Integer userId = user == null ? null : user.getUserId();
		Date now = new Date();
		int updated = getSession().createSQLQuery(
				"update rwandaemr_radiology_report_draft set report_text = :text, provider_id = :providerId, " +
						"changed_by = :userId, date_changed = :now where order_id = :orderId")
				.setParameter("text", text)
				.setParameter("providerId", providerId)
				.setParameter("userId", userId)
				.setParameter("now", now)
				.setParameter("orderId", orderId)
				.executeUpdate();
		if (updated == 0) {
			getSession().createSQLQuery(
					"insert into rwandaemr_radiology_report_draft (order_id, report_text, provider_id, creator, date_created) " +
							"values (:orderId, :text, :providerId, :userId, :now)")
					.setParameter("orderId", orderId)
					.setParameter("text", text)
					.setParameter("providerId", providerId)
					.setParameter("userId", userId)
					.setParameter("now", now)
					.executeUpdate();
		}
	}

	/**
	 * Removes the draft report of the order, once the report is saved as final
	 */
	@Transactional
	public void deleteDraft(Integer orderId) {
		getSession().createSQLQuery("delete from rwandaemr_radiology_report_draft where order_id = :orderId")
				.setParameter("orderId", orderId)
				.executeUpdate();
	}

	private Session getSession() {
		return dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
	}

	/**
	 * A report saved as draft
	 */
	public static class Draft {

		private Integer orderId;
		private String text;
		private Integer providerId;
		private Date lastSaved;
		private Integer lastSavedBy;

		public Integer getOrderId() {
			return orderId;
		}

		public void setOrderId(Integer orderId) {
			this.orderId = orderId;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public Integer getProviderId() {
			return providerId;
		}

		public void setProviderId(Integer providerId) {
			this.providerId = providerId;
		}

		public Date getLastSaved() {
			return lastSaved;
		}

		public void setLastSaved(Date lastSaved) {
			this.lastSaved = lastSaved;
		}

		public Integer getLastSavedBy() {
			return lastSavedBy;
		}

		public void setLastSavedBy(Integer lastSavedBy) {
			this.lastSavedBy = lastSavedBy;
		}
	}
}
