package org.openmrs.module.rwandaemr.labreports;

import org.apache.commons.lang.StringUtils;
import org.hibernate.SQLQuery;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.OrderType;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.pihapps.orders.LabTestCategory;
import org.openmrs.util.ConfigUtil;
import org.openmrs.util.OpenmrsUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LabReportServiceImpl extends BaseOpenmrsService implements LabReportService {

	public static final String NEGATIVE_CONCEPT_PROPERTY = "rwandaemr.labReports.negativeConcept";

	private DbSessionFactory sessionFactory;

	private LabOrderConfig labOrderConfig;

	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public void setLabOrderConfig(LabOrderConfig labOrderConfig) {
		this.labOrderConfig = labOrderConfig;
	}

	@Override
	public List<AggregateLabReportRow> getAggregateLabReport(LabReportSearchCriteria criteria) {
		validateDateRange(criteria);
		Map<Integer, LabTestInfo> labTests = getLeafLabTests(criteria);
		if (labTests.isEmpty()) {
			return Collections.emptyList();
		}

		String aggregateSql = "select result.concept_id, " +
				"sum(case when result.value_coded is not null and result.value_coded <> :negativeConceptId then 1 else 0 end) positive_count, " +
				"sum(case when result.value_coded = :negativeConceptId then 1 else 0 end) negative_count, " +
				"count(result.obs_id) total_count " +
				"from obs result " +
				"left join orders o on o.order_id = result.order_id " +
				"left join encounter order_encounter on order_encounter.encounter_id = o.encounter_id " +
				"left join encounter result_encounter on result_encounter.encounter_id = result.encounter_id " +
				"where result.voided = 0 " +
				"and result.concept_id in (:testConceptIds) " +
				"and result.obs_datetime >= :startDate " +
				"and result.obs_datetime < :endDateExclusive " +
				"and (result.value_coded is not null or result.value_numeric is not null or result.value_text is not null or result.value_datetime is not null) ";
		if (criteria.getLocationId() != null) {
			aggregateSql += "and coalesce(order_encounter.location_id, result_encounter.location_id, result.location_id) = :locationId ";
		}
		aggregateSql += "group by result.concept_id";

		SQLQuery query = sessionFactory.getCurrentSession().createSQLQuery(aggregateSql);
		query.setParameterList("testConceptIds", labTests.keySet());
		query.setInteger("negativeConceptId", getNegativeConceptId());
		query.setTimestamp("startDate", OpenmrsUtil.firstSecondOfDay(criteria.getStartDate()));
		query.setTimestamp("endDateExclusive", dayAfter(criteria.getEndDate()));
		if (criteria.getLocationId() != null) {
			query.setInteger("locationId", criteria.getLocationId());
		}

		Map<Integer, AggregateLabReportRow> rowsByConcept = new LinkedHashMap<Integer, AggregateLabReportRow>();
		for (Object rowObj : query.list()) {
			Object[] row = (Object[]) rowObj;
			Integer conceptId = toInteger(row[0]);
			LabTestInfo info = labTests.get(conceptId);
			if (info == null) {
				continue;
			}
			AggregateLabReportRow reportRow = new AggregateLabReportRow();
			reportRow.setCategoryConceptId(info.categoryConceptId);
			reportRow.setCategory(info.categoryName);
			reportRow.setTestConceptId(info.testConceptId);
			reportRow.setTest(info.testName);
			reportRow.setPositiveCount(toInteger(row[1]));
			reportRow.setNegativeCount(toInteger(row[2]));
			reportRow.setTotalResultedCount(toInteger(row[3]));
			if (matchesResultType(reportRow, criteria.getResultType())) {
				rowsByConcept.put(conceptId, reportRow);
			}
		}

		List<AggregateLabReportRow> rows = new ArrayList<AggregateLabReportRow>(rowsByConcept.values());
		Collections.sort(rows, new Comparator<AggregateLabReportRow>() {
			@Override
			public int compare(AggregateLabReportRow left, AggregateLabReportRow right) {
				int category = safe(left.getCategory()).compareToIgnoreCase(safe(right.getCategory()));
				if (category != 0) {
					return category;
				}
				return safe(left.getTest()).compareToIgnoreCase(safe(right.getTest()));
			}
		});
		return rows;
	}

	@Override
	public List<LabTurnaroundTimeRow> getTurnaroundTimeRows(LabReportSearchCriteria criteria) {
		validateDateRange(criteria);
		Map<Integer, LabTestInfo> labTests = getOrderableLabTests(criteria);
		if (labTests.isEmpty()) {
			return Collections.emptyList();
		}
		List<Integer> resultConceptIds = new ArrayList<Integer>(getLeafLabTests(criteria).keySet());
		if (resultConceptIds.isEmpty()) {
			resultConceptIds.addAll(labTests.keySet());
		}

		List<Integer> orderTypeIds = getLabOrderTypeIds();
		String tatSql = "select o.order_id, o.uuid, test_name.name test_name, loc.name location_name, o.urgency, o.fulfiller_status, " +
				"o.date_activated ordered_at, specimen.specimen_received_at, coalesce(result_date.resulted_at, result_obs.resulted_at) resulted_at, " +
				"timestampdiff(minute, o.date_activated, specimen.specimen_received_at) / 60.0 order_to_received_hours, " +
				"timestampdiff(minute, specimen.specimen_received_at, coalesce(result_date.resulted_at, result_obs.resulted_at)) / 60.0 received_to_result_hours, " +
				"timestampdiff(minute, o.date_activated, coalesce(result_date.resulted_at, result_obs.resulted_at)) / 60.0 order_to_result_hours " +
				"from orders o " +
				"inner join concept_name test_name on test_name.concept_id = o.concept_id and test_name.locale_preferred = 1 " +
				"left join encounter order_encounter on order_encounter.encounter_id = o.encounter_id " +
				"left join location loc on loc.location_id = order_encounter.location_id " +
				"left join (" +
				"  select order_id, min(obs_datetime) resulted_at " +
				"  from obs " +
				"  where voided = 0 and order_id is not null and concept_id in (:resultConceptIds) " +
				"  and (value_coded is not null or value_numeric is not null or value_text is not null or value_datetime is not null) " +
				"  group by order_id" +
				") result_obs on result_obs.order_id = o.order_id " +
				"left join (" +
				"  select link.order_id, min(sr.value_datetime) specimen_received_at " +
				"  from obs link inner join obs sr on sr.encounter_id = link.encounter_id " +
				"  where link.voided = 0 and link.order_id is not null and sr.voided = 0 and sr.concept_id = :specimenReceivedConceptId " +
				"  group by link.order_id" +
				") specimen on specimen.order_id = o.order_id " +
				"left join (" +
				"  select link.order_id, min(rd.value_datetime) resulted_at " +
				"  from obs link inner join obs rd on rd.encounter_id = link.encounter_id " +
				"  where link.voided = 0 and link.order_id is not null and rd.voided = 0 and rd.concept_id = :resultsDateConceptId " +
				"  group by link.order_id" +
				") result_date on result_date.order_id = o.order_id " +
				"where o.voided = 0 " +
				"and o.concept_id in (:orderConceptIds) ";
		if (!orderTypeIds.isEmpty()) {
			tatSql += "and o.order_type_id in (:orderTypeIds) ";
		}
		tatSql += "and o.date_activated >= :startDate " +
				"and o.date_activated < :endDateExclusive ";
		if (criteria.getLocationId() != null) {
			tatSql += "and order_encounter.location_id = :locationId ";
		}
		tatSql += "order by o.date_activated desc, test_name.name";

		SQLQuery query = sessionFactory.getCurrentSession().createSQLQuery(tatSql);
		query.setParameterList("orderConceptIds", labTests.keySet());
		query.setParameterList("resultConceptIds", resultConceptIds);
		if (!orderTypeIds.isEmpty()) {
			query.setParameterList("orderTypeIds", orderTypeIds);
		}
		query.setInteger("specimenReceivedConceptId", getConfiguredConceptId(labOrderConfig.getSpecimenReceivedDateQuestion()));
		query.setInteger("resultsDateConceptId", getConfiguredConceptId(labOrderConfig.getResultsDateQuestion()));
		query.setTimestamp("startDate", OpenmrsUtil.firstSecondOfDay(criteria.getStartDate()));
		query.setTimestamp("endDateExclusive", dayAfter(criteria.getEndDate()));
		if (criteria.getLocationId() != null) {
			query.setInteger("locationId", criteria.getLocationId());
		}

		List<LabTurnaroundTimeRow> rows = new ArrayList<LabTurnaroundTimeRow>();
		for (Object rowObj : query.list()) {
			Object[] row = (Object[]) rowObj;
			LabTurnaroundTimeRow tatRow = new LabTurnaroundTimeRow();
			tatRow.setOrderId(toInteger(row[0]));
			tatRow.setOrderUuid((String) row[1]);
			tatRow.setTest((String) row[2]);
			tatRow.setLocation((String) row[3]);
			tatRow.setUrgency((String) row[4]);
			tatRow.setFulfillerStatus((String) row[5]);
			tatRow.setOrderedAt((Date) row[6]);
			tatRow.setSpecimenReceivedAt((Date) row[7]);
			tatRow.setResultedAt((Date) row[8]);
			tatRow.setOrderToReceivedHours(toDouble(row[9]));
			tatRow.setReceivedToResultHours(toDouble(row[10]));
			tatRow.setOrderToResultHours(toDouble(row[11]));
			rows.add(tatRow);
		}
		return rows;
	}

	@Override
	public LabTurnaroundTimeSummary getTurnaroundTimeSummary(LabReportSearchCriteria criteria) {
		List<LabTurnaroundTimeRow> rows = getTurnaroundTimeRows(criteria);
		List<Double> completedHours = new ArrayList<Double>();
		Double targetHours = criteria.getTargetHours() == null ? 48.0 : criteria.getTargetHours();
		int delayed = 0;
		double sum = 0.0;
		for (LabTurnaroundTimeRow row : rows) {
			if (row.getOrderToResultHours() != null) {
				completedHours.add(row.getOrderToResultHours());
				sum += row.getOrderToResultHours();
				if (row.getOrderToResultHours() > targetHours) {
					delayed++;
				}
			}
		}
		Collections.sort(completedHours);

		LabTurnaroundTimeSummary summary = new LabTurnaroundTimeSummary();
		summary.setTotalOrders(rows.size());
		summary.setCompletedOrders(completedHours.size());
		summary.setDelayedOrders(delayed);
		summary.setAverageHours(completedHours.isEmpty() ? null : round(sum / completedHours.size()));
		summary.setMedianHours(percentile(completedHours, 50));
		summary.setPercentile90Hours(percentile(completedHours, 90));
		summary.setPercentWithinTarget(completedHours.isEmpty() ? null : round(((completedHours.size() - delayed) * 100.0) / completedHours.size()));
		return summary;
	}

	private Map<Integer, LabTestInfo> getOrderableLabTests(LabReportSearchCriteria criteria) {
		return getLabTests(criteria, false);
	}

	private Map<Integer, LabTestInfo> getLeafLabTests(LabReportSearchCriteria criteria) {
		Map<Integer, LabTestInfo> ret = getResultLabTests(criteria);
		if (ret.isEmpty()) {
			ret = getLabTests(criteria, true);
		}
		return ret;
	}

	private Map<Integer, LabTestInfo> getResultLabTests(LabReportSearchCriteria criteria) {
		Map<Integer, LabTestInfo> ret = new LinkedHashMap<Integer, LabTestInfo>();
		if (labOrderConfig == null || labOrderConfig.getLabResultCategoriesConceptSet() == null) {
			return ret;
		}
		for (Concept category : labOrderConfig.getLabResultCategoriesConceptSet().getSetMembers()) {
			if (category == null) {
				continue;
			}
			if (criteria.getCategoryConceptId() != null && !criteria.getCategoryConceptId().equals(category.getConceptId())) {
				continue;
			}
			for (Concept leaf : getLeafConcepts(category, new HashSet<Integer>())) {
				if (criteria.getTestConceptId() != null && !criteria.getTestConceptId().equals(leaf.getConceptId())) {
					continue;
				}
				ret.put(leaf.getConceptId(), new LabTestInfo(category, leaf));
			}
		}
		return ret;
	}

	private Map<Integer, LabTestInfo> getLabTests(LabReportSearchCriteria criteria, boolean leafTests) {
		Map<Integer, LabTestInfo> ret = new LinkedHashMap<Integer, LabTestInfo>();
		if (labOrderConfig == null) {
			return ret;
		}
		for (LabTestCategory category : labOrderConfig.getAvailableLabTestsByCategory()) {
			if (category == null || category.getCategory() == null || category.getLabTests() == null) {
				continue;
			}
			if (criteria.getCategoryConceptId() != null && !criteria.getCategoryConceptId().equals(category.getCategory().getConceptId())) {
				continue;
			}
			for (Concept labTest : category.getLabTests()) {
				if (criteria.getTestConceptId() != null && !containsConcept(labTest, criteria.getTestConceptId())) {
					continue;
				}
				if (leafTests) {
					for (Concept leaf : getLeafConcepts(labTest, new HashSet<Integer>())) {
						ret.put(leaf.getConceptId(), new LabTestInfo(category.getCategory(), leaf));
					}
				} else {
					ret.put(labTest.getConceptId(), new LabTestInfo(category.getCategory(), labTest));
				}
			}
		}
		return ret;
	}

	private List<Concept> getLeafConcepts(Concept concept, Set<Integer> visitedConceptIds) {
		List<Concept> ret = new ArrayList<Concept>();
		if (concept == null) {
			return ret;
		}
		if (!visitedConceptIds.add(concept.getConceptId())) {
			return ret;
		}
		if (concept.isSet() && concept.getSetMembers() != null && !concept.getSetMembers().isEmpty()) {
			for (Concept member : concept.getSetMembers()) {
				ret.addAll(getLeafConcepts(member, visitedConceptIds));
			}
		} else {
			ret.add(concept);
		}
		return ret;
	}

	private boolean containsConcept(Concept concept, Integer conceptId) {
		return containsConcept(concept, conceptId, new HashSet<Integer>());
	}

	private boolean containsConcept(Concept concept, Integer conceptId, Set<Integer> visitedConceptIds) {
		if (concept == null || conceptId == null) {
			return false;
		}
		if (!visitedConceptIds.add(concept.getConceptId())) {
			return false;
		}
		if (conceptId.equals(concept.getConceptId())) {
			return true;
		}
		if (concept.isSet() && concept.getSetMembers() != null) {
			for (Concept member : concept.getSetMembers()) {
				if (containsConcept(member, conceptId, visitedConceptIds)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Integer> getLabOrderTypeIds() {
		List<Integer> ret = new ArrayList<Integer>();
		if (labOrderConfig != null) {
			for (OrderType orderType : labOrderConfig.getTestOrderTypes()) {
				ret.add(orderType.getOrderTypeId());
			}
		}
		return ret;
	}

	private int getNegativeConceptId() {
		String configured = ConfigUtil.getProperty(NEGATIVE_CONCEPT_PROPERTY);
		if (StringUtils.isBlank(configured)) {
			configured = "664";
		}
		Concept concept = Context.getConceptService().getConceptByReference(configured);
		if (concept != null) {
			return concept.getConceptId();
		}
		return Integer.parseInt(configured);
	}

	private Integer getConfiguredConceptId(Concept concept) {
		if (concept == null) {
			return -1;
		}
		return concept.getConceptId();
	}

	private void validateDateRange(LabReportSearchCriteria criteria) {
		if (criteria == null || criteria.getStartDate() == null || criteria.getEndDate() == null) {
			throw new IllegalArgumentException("startDate and endDate are required");
		}
	}

	private Date dayAfter(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(OpenmrsUtil.firstSecondOfDay(date));
		cal.add(Calendar.DATE, 1);
		return cal.getTime();
	}

	private boolean matchesResultType(AggregateLabReportRow row, String resultType) {
		if (StringUtils.isBlank(resultType) || "all".equalsIgnoreCase(resultType) || "resulted".equalsIgnoreCase(resultType)) {
			return true;
		}
		if ("positive".equalsIgnoreCase(resultType)) {
			return row.getPositiveCount() != null && row.getPositiveCount() > 0;
		}
		if ("negative".equalsIgnoreCase(resultType)) {
			return row.getNegativeCount() != null && row.getNegativeCount() > 0;
		}
		return true;
	}

	private Integer toInteger(Object value) {
		if (value == null) {
			return 0;
		}
		if (value instanceof Integer) {
			return (Integer) value;
		}
		if (value instanceof BigInteger) {
			return ((BigInteger) value).intValue();
		}
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return Integer.valueOf(value.toString());
	}

	private Double toDouble(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return round(((Number) value).doubleValue());
		}
		return round(Double.valueOf(value.toString()));
	}

	private Double percentile(List<Double> values, int percentile) {
		if (values.isEmpty()) {
			return null;
		}
		double index = (percentile / 100.0) * (values.size() - 1);
		int lower = (int) Math.floor(index);
		int upper = (int) Math.ceil(index);
		if (lower == upper) {
			return round(values.get(lower));
		}
		double weight = index - lower;
		return round(values.get(lower) + ((values.get(upper) - values.get(lower)) * weight));
	}

	private Double round(Double value) {
		if (value == null) {
			return null;
		}
		return Math.round(value * 10.0) / 10.0;
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private class LabTestInfo {

		private Integer categoryConceptId;

		private String categoryName;

		private Integer testConceptId;

		private String testName;

		private LabTestInfo(Concept category, Concept test) {
			this.categoryConceptId = category.getConceptId();
			this.categoryName = getPreferredName(category);
			this.testConceptId = test.getConceptId();
			this.testName = getPreferredName(test);
		}
	}

	private String getPreferredName(Concept concept) {
		Locale locale = Context.getLocale();
		ConceptName name = concept.getName(locale);
		if (name == null) {
			name = concept.getName();
		}
		return name == null ? concept.getUuid() : name.getName();
	}
}
