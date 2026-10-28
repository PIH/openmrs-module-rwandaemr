package org.openmrs.module.rwandaemr.pathology;

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
import java.util.List;
import java.util.Map;

/**
 * Pathology cases, and the history of their statuses: who moved each case to each status, and when
 */
@Component
public class PathologyCaseRepository {

	private static final String SELECT_CASE = "select c.pathology_case_id, c.encounter_id, c.accession_number, c.status, " +
			"c.rejection_reason, c.creator, c.date_created, c.changed_by, c.date_changed from rwandaemr_pathology_case c ";

	private final DbSessionFactory dbSessionFactory;

	public PathologyCaseRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	/**
	 * @return the case of the request encounter, or null if the lab has not received or rejected its specimen
	 */
	@Transactional(readOnly = true)
	public PathologyCase getCase(Integer encounterId) {
		List<?> rows = getSession().createSQLQuery(SELECT_CASE + "where c.encounter_id = :encounterId")
				.setParameter("encounterId", encounterId)
				.list();
		return rows.isEmpty() ? null : toCase((Object[]) rows.get(0));
	}

	/**
	 * @return the cases of the given request encounters, by encounter id
	 */
	@Transactional(readOnly = true)
	public Map<Integer, PathologyCase> getCases(Collection<Integer> encounterIds) {
		Map<Integer, PathologyCase> ret = new HashMap<Integer, PathologyCase>();
		if (encounterIds == null || encounterIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(SELECT_CASE + "where c.encounter_id in (:encounterIds)");
		query.setParameterList("encounterIds", encounterIds);
		for (Object row : query.list()) {
			PathologyCase pathologyCase = toCase((Object[]) row);
			ret.put(pathologyCase.getEncounterId(), pathologyCase);
		}
		return ret;
	}

	/**
	 * @return the history of the statuses of the given cases, oldest first, by case id
	 */
	@Transactional(readOnly = true)
	public Map<Integer, List<StatusChange>> getHistories(Collection<Integer> caseIds) {
		Map<Integer, List<StatusChange>> ret = new HashMap<Integer, List<StatusChange>>();
		if (caseIds == null || caseIds.isEmpty()) {
			return ret;
		}
		NativeQuery<?> query = getSession().createSQLQuery(
				"select s.pathology_case_id, s.status, s.reason, s.creator, s.date_created " +
						"from rwandaemr_pathology_case_status s where s.pathology_case_id in (:caseIds) " +
						"order by s.date_created, s.pathology_case_status_id");
		query.setParameterList("caseIds", caseIds);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			StatusChange change = new StatusChange(PathologyStatus.valueOf((String) values[1]), (String) values[2],
					toInteger(values[3]), (Date) values[4]);
			ret.computeIfAbsent(toInteger(values[0]), k -> new ArrayList<StatusChange>()).add(change);
		}
		return ret;
	}

	/**
	 * Saves a new case, or the status of an existing one, and records the change in the case's history
	 */
	@Transactional
	public void saveCase(PathologyCase pathologyCase) {
		User user = Context.getAuthenticatedUser();
		Integer userId = user == null ? null : user.getUserId();
		Date now = new Date();
		if (pathologyCase.getPathologyCaseId() == null) {
			getSession().createSQLQuery(
					"insert into rwandaemr_pathology_case (encounter_id, accession_number, status, rejection_reason, " +
							"creator, date_created) values (:encounterId, :accessionNumber, :status, :reason, :userId, :now)")
					.setParameter("encounterId", pathologyCase.getEncounterId())
					.setParameter("accessionNumber", pathologyCase.getAccessionNumber())
					.setParameter("status", pathologyCase.getStatus().name())
					.setParameter("reason", pathologyCase.getRejectionReason())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.executeUpdate();
			Number id = (Number) getSession().createSQLQuery(
					"select pathology_case_id from rwandaemr_pathology_case where encounter_id = :encounterId")
					.setParameter("encounterId", pathologyCase.getEncounterId())
					.uniqueResult();
			pathologyCase.setPathologyCaseId(id.intValue());
			pathologyCase.setCreator(userId);
			pathologyCase.setDateCreated(now);
		}
		else {
			getSession().createSQLQuery(
					"update rwandaemr_pathology_case set accession_number = :accessionNumber, status = :status, " +
							"rejection_reason = :reason, changed_by = :userId, date_changed = :now " +
							"where pathology_case_id = :caseId")
					.setParameter("accessionNumber", pathologyCase.getAccessionNumber())
					.setParameter("status", pathologyCase.getStatus().name())
					.setParameter("reason", pathologyCase.getRejectionReason())
					.setParameter("userId", userId)
					.setParameter("now", now)
					.setParameter("caseId", pathologyCase.getPathologyCaseId())
					.executeUpdate();
			pathologyCase.setChangedBy(userId);
			pathologyCase.setDateChanged(now);
		}
		getSession().createSQLQuery(
				"insert into rwandaemr_pathology_case_status (pathology_case_id, status, reason, creator, date_created) " +
						"values (:caseId, :status, :reason, :userId, :now)")
				.setParameter("caseId", pathologyCase.getPathologyCaseId())
				.setParameter("status", pathologyCase.getStatus().name())
				.setParameter("reason", pathologyCase.getRejectionReason())
				.setParameter("userId", userId)
				.setParameter("now", now)
				.executeUpdate();
	}

	private PathologyCase toCase(Object[] values) {
		PathologyCase ret = new PathologyCase();
		ret.setPathologyCaseId(toInteger(values[0]));
		ret.setEncounterId(toInteger(values[1]));
		ret.setAccessionNumber((String) values[2]);
		ret.setStatus(PathologyStatus.valueOf((String) values[3]));
		ret.setRejectionReason((String) values[4]);
		ret.setCreator(toInteger(values[5]));
		ret.setDateCreated((Date) values[6]);
		ret.setChangedBy(toInteger(values[7]));
		ret.setDateChanged((Date) values[8]);
		return ret;
	}

	private Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}

	private Session getSession() {
		return dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
	}

	/**
	 * A case moved to a status: by whom, when, and the reason given for a rejection
	 */
	public static class StatusChange {

		private final PathologyStatus status;
		private final String reason;
		private final Integer userId;
		private final Date date;

		public StatusChange(PathologyStatus status, String reason, Integer userId, Date date) {
			this.status = status;
			this.reason = reason;
			this.userId = userId;
			this.date = date;
		}

		public PathologyStatus getStatus() {
			return status;
		}

		public String getReason() {
			return reason;
		}

		public Integer getUserId() {
			return userId;
		}

		public Date getDate() {
			return date;
		}
	}
}
