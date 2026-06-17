package org.openmrs.module.rwandaemr.icd11.dao;

import lombok.Setter;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Concept;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.icd11.model.Icd11CodeCache;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11BrowserNode;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class HibernateIcd11Dao implements Icd11Dao {

	private static final List<Chapter> MMS_CHAPTERS = Arrays.asList(
			new Chapter("01", "Certain infectious or parasitic diseases", "^1[A-H]"),
			new Chapter("02", "Neoplasms", "^2[A-F]"),
			new Chapter("03", "Diseases of the blood or blood-forming organs", "^3[A-C]"),
			new Chapter("04", "Diseases of the immune system", "^4A"),
			new Chapter("05", "Endocrine, nutritional or metabolic diseases", "^5[A-D]"),
			new Chapter("06", "Mental, behavioural or neurodevelopmental disorders", "^6[A-E]"),
			new Chapter("07", "Sleep-wake disorders", "^7A"),
			new Chapter("08", "Diseases of the nervous system", "^8[A-E]"),
			new Chapter("09", "Diseases of the visual system", "^9[A-D]"),
			new Chapter("10", "Diseases of the ear or mastoid process", "^A[A-C]"),
			new Chapter("11", "Diseases of the circulatory system", "^B[A-E]"),
			new Chapter("12", "Diseases of the respiratory system", "^CA"),
			new Chapter("13", "Diseases of the digestive system", "^D[A-E]"),
			new Chapter("14", "Diseases of the skin", "^EA"),
			new Chapter("15", "Diseases of the musculoskeletal system or connective tissue", "^FA"),
			new Chapter("16", "Diseases of the genitourinary system", "^GA"),
			new Chapter("17", "Conditions related to sexual health", "^HA"),
			new Chapter("18", "Pregnancy, childbirth or the puerperium", "^J[A-B]"),
			new Chapter("19", "Certain conditions originating in the perinatal period", "^KA"),
			new Chapter("20", "Developmental anomalies", "^L[A-D]"),
			new Chapter("21", "Symptoms, signs or clinical findings, not elsewhere classified", "^M[A-H]"),
			new Chapter("22", "Injury, poisoning or certain other consequences of external causes", "^N[A-F]"),
			new Chapter("23", "External causes of morbidity or mortality", "^P[A-D]"),
			new Chapter("24", "Factors influencing health status or contact with health services", "^Q[A-E]"),
			new Chapter("25", "Codes for special purposes", "^RA"),
			new Chapter("26", "Supplementary Chapter Traditional Medicine Conditions", "^S[A-J]"),
			new Chapter("V", "Supplementary section for functioning assessment", "^V"),
			new Chapter("X", "Extension Codes", "^X"));

	@Setter
	private DbSessionFactory sessionFactory;

	@Override
	@SuppressWarnings("unchecked")
	public Icd11CodeCache getCodeCacheByKey(String cacheKey) {
		List<Icd11CodeCache> matches = sessionFactory.getCurrentSession()
				.createQuery("from Icd11CodeCache where cacheKey = :cacheKey and voided = false order by dateCreated desc")
				.setParameter("cacheKey", cacheKey)
				.setMaxResults(1)
				.list();
		return matches.isEmpty() ? null : matches.get(0);
	}

	@Override
	public Icd11CodeCache saveCodeCache(Icd11CodeCache cacheEntry) {
		sessionFactory.getCurrentSession().saveOrUpdate(cacheEntry);
		return cacheEntry;
	}

	@Override
	public Icd11PatientDiagnosis saveDiagnosis(Icd11PatientDiagnosis diagnosis) {
		sessionFactory.getCurrentSession().saveOrUpdate(diagnosis);
		return diagnosis;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Icd11PatientDiagnosis getDiagnosisByUuid(String uuid) {
		List<Icd11PatientDiagnosis> matches = sessionFactory.getCurrentSession()
				.createQuery("from Icd11PatientDiagnosis where uuid = :uuid")
				.setParameter("uuid", uuid)
				.setMaxResults(1)
				.list();
		return matches.isEmpty() ? null : matches.get(0);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Icd11PatientDiagnosis> getDiagnoses(Patient patient, Encounter encounter) {
		String hql = "from Icd11PatientDiagnosis where patient = :patient and voided = false";
		if (encounter != null) {
			hql += " and encounter = :encounter";
		}
		hql += " order by dateCreated desc";
		org.hibernate.Query query = sessionFactory.getCurrentSession()
				.createQuery(hql)
				.setParameter("patient", patient);
		if (encounter != null) {
			query.setParameter("encounter", encounter);
		}
		return query.list();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Icd11SearchResult> searchLocalCodes(String query, String linearization) {
		List<Object[]> rows = sessionFactory.getCurrentSession().createSQLQuery(
				"select c.uuid, cn.name from concept c "
						+ "join concept_class cc on cc.concept_class_id = c.class_id "
						+ "join concept_name cn on cn.concept_id = c.concept_id "
						+ "where cc.name = 'ICD-11' and c.retired = 0 and cn.voided = 0 "
						+ "and cn.locale = 'en' and cn.locale_preferred = 1 "
						+ "and lower(cn.name) like :query order by cn.name limit 50")
				.setParameter("query", "%" + query.toLowerCase() + "%")
				.list();
		List<Icd11SearchResult> results = new java.util.ArrayList<>();
		for (Object[] row : rows) {
			String conceptUuid = String.valueOf(row[0]);
			String name = String.valueOf(row[1]);
			int separator = name.indexOf('-');
			Icd11SearchResult result = new Icd11SearchResult();
			result.setConceptUuid(conceptUuid);
			result.setIcd11Code(separator > 0 ? name.substring(0, separator).trim() : null);
			result.setTitle(separator > 0 ? name.substring(separator + 1).trim() : name);
			result.setEntityUri("local:concept/" + conceptUuid);
			result.setLinearization(linearization);
			results.add(result);
		}
		return results;
	}

	@Override
	public Concept getLocalConceptByCode(String code) {
		if (StringUtils.isBlank(code)) {
			return null;
		}
		Object conceptId = sessionFactory.getCurrentSession().createSQLQuery(
				"select c.concept_id from concept c "
						+ "join concept_class cc on cc.concept_class_id = c.class_id "
						+ "join concept_name cn on cn.concept_id = c.concept_id "
						+ "where cc.name = 'ICD-11' and c.retired = 0 and cn.voided = 0 "
						+ "and cn.locale = 'en' and cn.locale_preferred = 1 "
						+ "and lower(cn.name) like :codePrefix order by c.concept_id limit 1")
				.setParameter("codePrefix", code.trim().toLowerCase() + "-%")
				.uniqueResult();
		return conceptId == null ? null : (Concept) sessionFactory.getCurrentSession()
				.get(Concept.class, ((Number) conceptId).intValue());
	}

	@Override
	public Icd11SearchResult getLocalCode(String code, String linearization) {
		if (StringUtils.isBlank(code)) {
			return null;
		}
		Object[] row = (Object[]) sessionFactory.getCurrentSession().createSQLQuery(
				"select c.uuid, cn.name from concept c "
						+ "join concept_class cc on cc.concept_class_id = c.class_id "
						+ "join concept_name cn on cn.concept_id = c.concept_id "
						+ "where cc.name = 'ICD-11' and c.retired = 0 and cn.voided = 0 "
						+ "and cn.locale = 'en' and cn.locale_preferred = 1 "
						+ "and lower(substring_index(cn.name, '-', 1)) = :code limit 1")
				.setParameter("code", code.trim().toLowerCase())
				.uniqueResult();
		return row == null ? null : toSearchResult(row, linearization);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<Icd11BrowserNode> getLocalBrowserNodes(String parentCode) {
		if (StringUtils.isBlank(parentCode)) {
			List<Icd11BrowserNode> nodes = new java.util.ArrayList<>();
			for (Chapter chapter : MMS_CHAPTERS) {
				Icd11BrowserNode node = new Icd11BrowserNode();
				node.setCode("chapter:" + chapter.code);
				node.setTitle(chapter.code + " " + chapter.title);
				node.setHasChildren(true);
				nodes.add(node);
			}
			return nodes;
		}
		String parent = parentCode.trim();
		String sql;
		String pattern;
		Chapter chapter = findChapter(parent);
		if (chapter != null) {
			sql = "and substring_index(cn.name, '-', 1) regexp :pattern "
					+ "and length(substring_index(cn.name, '-', 1)) = 4 ";
			pattern = chapter.codePattern;
		}
		else if (parent.length() == 2) {
			sql = "and substring_index(cn.name, '-', 1) like :pattern "
					+ "and length(substring_index(cn.name, '-', 1)) = 4 ";
			pattern = parent + "__";
		}
		else {
			sql = "and substring_index(cn.name, '-', 1) like :pattern "
					+ "and locate('.', substring(substring_index(cn.name, '-', 1), :childStart)) = 0 ";
			pattern = parent + ".%";
		}
		org.hibernate.SQLQuery query = sessionFactory.getCurrentSession().createSQLQuery(
				"select c.uuid, cn.name from concept c "
						+ "join concept_class cc on cc.concept_class_id = c.class_id "
						+ "join concept_name cn on cn.concept_id = c.concept_id "
						+ "where cc.name = 'ICD-11' and c.retired = 0 and cn.voided = 0 "
						+ "and cn.locale = 'en' and cn.locale_preferred = 1 " + sql
						+ "order by cn.name limit 250")
				.setParameter("pattern", pattern);
		if (chapter == null && parent.length() != 2) {
			query.setParameter("childStart", parent.length() + 2);
		}
		List<Object[]> rows = query.list();
		List<Icd11BrowserNode> nodes = new java.util.ArrayList<>();
		for (Object[] row : rows) {
			Icd11SearchResult result = toSearchResult(row, null);
			Icd11BrowserNode node = new Icd11BrowserNode();
			node.setCode(result.getIcd11Code());
			node.setTitle(result.getTitle());
			node.setHasChildren(hasLocalChildren(result.getIcd11Code()));
			nodes.add(node);
		}
		return nodes;
	}

	private Chapter findChapter(String browserCode) {
		if (!browserCode.startsWith("chapter:")) {
			return null;
		}
		String chapterCode = browserCode.substring("chapter:".length());
		for (Chapter chapter : MMS_CHAPTERS) {
			if (chapter.code.equals(chapterCode)) {
				return chapter;
			}
		}
		return null;
	}

	private boolean hasLocalChildren(String code) {
		Number count = (Number) sessionFactory.getCurrentSession().createSQLQuery(
				"select count(*) from concept c "
						+ "join concept_class cc on cc.concept_class_id = c.class_id "
						+ "join concept_name cn on cn.concept_id = c.concept_id "
						+ "where cc.name = 'ICD-11' and c.retired = 0 and cn.voided = 0 "
						+ "and cn.locale = 'en' and cn.locale_preferred = 1 "
						+ "and substring_index(cn.name, '-', 1) like :pattern")
				.setParameter("pattern", code + ".%")
				.uniqueResult();
		return count != null && count.longValue() > 0;
	}

	private Icd11SearchResult toSearchResult(Object[] row, String linearization) {
		String conceptUuid = String.valueOf(row[0]);
		String name = String.valueOf(row[1]);
		int separator = name.indexOf('-');
		Icd11SearchResult result = new Icd11SearchResult();
		result.setConceptUuid(conceptUuid);
		result.setIcd11Code(separator > 0 ? name.substring(0, separator).trim() : null);
		result.setTitle(separator > 0 ? name.substring(separator + 1).trim() : name);
		result.setEntityUri("local:concept/" + conceptUuid);
		result.setLinearization(linearization);
		return result;
	}

	private static class Chapter {

		private final String code;

		private final String title;

		private final String codePattern;

		private Chapter(String code, String title, String codePattern) {
			this.code = code;
			this.title = title;
			this.codePattern = codePattern;
		}
	}
}
