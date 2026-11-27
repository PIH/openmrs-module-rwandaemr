package org.openmrs.module.rwandaemr.billing.verification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.query.NativeQuery;
import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
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
import java.util.function.Supplier;

/**
 * How far lab tests got, without their results:
 * <ul>
 *     <li>the specimen is recorded when the lab creates the specimen collection encounter (Laboratory Encounter) that
 *     pihapps links to each order through an observation carrying the order</li>
 *     <li>results are entered when the first result observation linked to the order is created</li>
 * </ul>
 * Result values are never read, so they can't reach whoever verifies the bill.
 */
@Component
public class LabTestProgressLookup {

	private final Log log = LogFactory.getLog(getClass());

	private final DbSessionFactory dbSessionFactory;
	private final LabOrderConfig labOrderConfig;

	public LabTestProgressLookup(@Autowired DbSessionFactory dbSessionFactory, @Autowired LabOrderConfig labOrderConfig) {
		this.dbSessionFactory = dbSessionFactory;
		this.labOrderConfig = labOrderConfig;
	}

	/**
	 * @return true if the order is a lab test: of a lab test order type, and for a test the lab offers.  Pathology and
	 * radiology orders are test orders too, but not lab tests.
	 */
	public boolean isLabTest(Order order) {
		if (order == null || order.getOrderType() == null) {
			return false;
		}
		OrderType orderType = order.getOrderType();
		if (orderType.equals(labOrderConfig.getLabTestOrderType())) {
			return true;
		}
		List<OrderType> testOrderTypes = labOrderConfig.getTestOrderTypes();
		if (testOrderTypes == null || !testOrderTypes.contains(orderType)) {
			return false;
		}
		List<Concept> labTests = labOrderConfig.getEnabledLabTests();
		return labTests != null && labTests.contains(order.getConcept());
	}

	/**
	 * @return how far each of the given lab tests got, in the given order
	 */
	@Transactional(readOnly = true)
	public List<LabTestProgress> getProgress(Collection<Order> orders) {
		Map<Integer, LabTestProgress> ret = new LinkedHashMap<Integer, LabTestProgress>();
		for (Order order : orders) {
			LabTestProgress progress = new LabTestProgress();
			progress.setOrderId(order.getOrderId());
			progress.setOrderNumber(order.getOrderNumber());
			progress.setTestName(order.getConcept() == null ? "" : order.getConcept().getDisplayString());
			progress.setUrgency(order.getUrgency() == null ? null : order.getUrgency().name());
			progress.setOrderedAt(order.getDateActivated());
			progress.setOrderedBy(order.getOrderer() == null ? null : order.getOrderer().getName());
			progress.setFulfillerStatus(order.getFulfillerStatus() == null ? null : order.getFulfillerStatus().name());
			progress.setCancelled(Boolean.TRUE.equals(order.getVoided()) || order.getAction() == Order.Action.DISCONTINUE
					|| (order.getDateStopped() != null && order.getFulfillerStatus() == null));
			ret.put(order.getOrderId(), progress);
		}
		if (!ret.isEmpty()) {
			Map<Integer, String> userNames = new HashMap<Integer, String>();
			// Each step is read on its own, so one that fails still leaves the others
			run("specimen encounters", ret, () -> { addSpecimenRecorded(ret, userNames); return null; });
			run("result entries", ret, () -> { addResultsEntered(ret, userNames); return null; });
			run("reasons not performed", ret, () -> { addNotPerformedReasons(ret); return null; });
		}
		return new ArrayList<LabTestProgress>(ret.values());
	}

	private void run(String step, Map<Integer, LabTestProgress> progressByOrderId, Supplier<Void> supplier) {
		try {
			supplier.get();
		}
		catch (RuntimeException e) {
			log.warn("Unable to read the " + step + " of lab orders " + progressByOrderId.keySet(), e);
		}
	}

	/**
	 * Adds when the specimen was recorded and by whom: the creation of the first specimen collection encounter
	 * linked to each order
	 */
	private void addSpecimenRecorded(Map<Integer, LabTestProgress> progressByOrderId, Map<Integer, String> userNames) {
		EncounterType specimenEncounterType = labOrderConfig.getSpecimenCollectionEncounterType();
		StringBuilder sql = new StringBuilder("select o.order_id, e.date_created, e.creator from obs o ")
				.append("join encounter e on e.encounter_id = o.encounter_id ")
				.append("where o.voided = 0 and e.voided = 0 and o.order_id in (:orderIds) ");
		if (specimenEncounterType != null) {
			sql.append("and e.encounter_type = :encounterType ");
		}
		sql.append("order by e.date_created, e.encounter_id");
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(sql.toString());
		query.setParameterList("orderIds", progressByOrderId.keySet());
		if (specimenEncounterType != null) {
			query.setParameter("encounterType", specimenEncounterType.getEncounterTypeId());
		}
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			LabTestProgress progress = progressByOrderId.get(toInteger(values[0]));
			if (progress != null && progress.getSpecimenRecordedAt() == null) {
				progress.setSpecimenRecordedAt((Date) values[1]);
				progress.setSpecimenRecordedBy(userName(toInteger(values[2]), userNames));
			}
		}
	}

	/**
	 * Adds when results were entered and by whom: the creation of the first result observation linked to each order.
	 * Observations the lab records about the specimen or the order are not results.
	 */
	private void addResultsEntered(Map<Integer, LabTestProgress> progressByOrderId, Map<Integer, String> userNames) {
		List<Integer> excluded = new ArrayList<Integer>();
		excluded.add(-1);
		addConcept(excluded, labOrderConfig::getSpecimenReceivedDateQuestion);
		addConcept(excluded, labOrderConfig::getResultsDateQuestion);
		addConcept(excluded, labOrderConfig::getReasonTestNotPerformedQuestion);
		addConcept(excluded, labOrderConfig::getTestOrderNumberQuestion);
		addConcept(excluded, labOrderConfig::getLabIdentifierConcept);
		addConcept(excluded, labOrderConfig::getTestLocationQuestion);
		addConcept(excluded, labOrderConfig::getEstimatedCollectionDateQuestion);
		try {
			List<Concept> linking = labOrderConfig.getFulfillerEncounterLinkingConcepts();
			if (linking != null) {
				for (Concept concept : linking) {
					excluded.add(concept.getConceptId());
				}
			}
		}
		catch (RuntimeException e) {
			log.warn("Unable to read the lab fulfiller encounter linking concepts", e);
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.order_id, o.date_created, o.creator from obs o where o.voided = 0 and o.order_id in (:orderIds) " +
						"and o.concept_id not in (:excluded) " +
						"and (o.value_coded is not null or o.value_numeric is not null or o.value_text is not null " +
						"or o.value_datetime is not null or o.value_drug is not null or o.value_complex is not null) " +
						"order by o.date_created, o.obs_id");
		query.setParameterList("orderIds", progressByOrderId.keySet());
		query.setParameterList("excluded", excluded);
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			LabTestProgress progress = progressByOrderId.get(toInteger(values[0]));
			if (progress != null && progress.getResultsEnteredAt() == null) {
				progress.setResultsEnteredAt((Date) values[1]);
				progress.setResultsEnteredBy(userName(toInteger(values[2]), userNames));
			}
		}
	}

	/**
	 * Adds the reason the lab gave for not doing a test
	 */
	private void addNotPerformedReasons(Map<Integer, LabTestProgress> progressByOrderId) {
		Concept question = labOrderConfig.getReasonTestNotPerformedQuestion();
		if (question == null) {
			return;
		}
		NativeQuery<?> query = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createSQLQuery(
				"select o.order_id, o.value_coded, o.value_text from obs o where o.voided = 0 " +
						"and o.order_id in (:orderIds) and o.concept_id = :question order by o.date_created desc, o.obs_id desc");
		query.setParameterList("orderIds", progressByOrderId.keySet());
		query.setParameter("question", question.getConceptId());
		for (Object row : query.list()) {
			Object[] values = (Object[]) row;
			LabTestProgress progress = progressByOrderId.get(toInteger(values[0]));
			if (progress != null && progress.getNotPerformedReason() == null) {
				Integer coded = toInteger(values[1]);
				Concept reason = coded == null ? null : Context.getConceptService().getConcept(coded);
				progress.setNotPerformedReason(reason != null ? reason.getDisplayString()
						: values[2] == null ? "No reason given" : values[2].toString());
			}
		}
	}

	private String userName(Integer userId, Map<Integer, String> cache) {
		if (userId == null) {
			return null;
		}
		if (!cache.containsKey(userId)) {
			User user = Context.getUserService().getUser(userId);
			String name = null;
			if (user != null) {
				name = user.getPersonName() != null ? user.getPersonName().getFullName() : user.getUsername();
			}
			cache.put(userId, name);
		}
		return cache.get(userId);
	}

	/**
	 * Adds the configured concept, if any.  A concept that isn't configured, or can't be found, is skipped.
	 */
	private void addConcept(List<Integer> conceptIds, Supplier<Concept> concept) {
		try {
			Concept c = concept.get();
			if (c != null) {
				conceptIds.add(c.getConceptId());
			}
		}
		catch (RuntimeException e) {
			log.debug("Lab concept not configured", e);
		}
	}

	private Integer toInteger(Object value) {
		return value == null ? null : ((Number) value).intValue();
	}
}
