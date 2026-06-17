package org.openmrs.module.rwandaemr.icd11;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.rwandaemr.icd11.dao.Icd11Dao;
import org.openmrs.module.rwandaemr.icd11.model.Icd11CodeCache;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResponse;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11BrowserNode;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Transactional
public class Icd11ServiceImpl extends BaseOpenmrsService implements Icd11Service {

	private static final String VISIT_DIAGNOSES_CONCEPT_UUID = "46ef3304-1668-4094-9bc9-92e509fdbbf8";
	private static final String CODED_DIAGNOSIS_CONCEPT_UUID = "3cd94c66-26fe-102b-80cb-0017a47871b2";
	private static final String DIAGNOSIS_CERTAINTY_CONCEPT_UUID = "d3a5e64f-377c-468d-9849-2b8b89e8d2f9";
	private static final String CONFIRMED_CERTAINTY_CONCEPT_UUID = "3ce885b1-feae-4c87-82e9-d3a4b6a6ed6b";
	private static final String PRESUMED_CERTAINTY_CONCEPT_UUID = "0ab917fb-a96b-49c6-8471-c3e9d9a08287";
	private static final String DIAGNOSIS_ORDER_CONCEPT_UUID = "159946AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String PRIMARY_DIAGNOSIS_CONCEPT_UUID = "159943AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String SECONDARY_DIAGNOSIS_CONCEPT_UUID = "159944AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String DIAGNOSIS_DATE_CONCEPT_UUID = "bba9f76a-7bad-4611-8940-0ee2bc609d69";

	protected final Log log = LogFactory.getLog(getClass());

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Setter
	private Icd11Config config;

	@Setter
	private Icd11ApiClient apiClient;

	@Setter
	private Icd11Dao dao;

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	public Icd11SearchResponse search(String query) {
		return search(query, config.getDefaultLinearization());
	}

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	public Icd11SearchResponse search(String query, String linearization) {
		return search(query, linearization, true);
	}

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	public Icd11SearchResult getCode(String code) {
		String normalizedCode = StringUtils.trimToEmpty(code);
		if (StringUtils.isBlank(normalizedCode)) {
			throw new IllegalArgumentException("ICD-11 code is required");
		}
		Icd11SearchResult localResult = dao.getLocalCode(normalizedCode, config.getDefaultLinearization());
		if (localResult != null) {
			try {
				Icd11SearchResult remoteResult = apiClient.getCodeDetails(normalizedCode, config.getDefaultLinearization());
				if (remoteResult != null) {
					remoteResult.setConceptUuid(localResult.getConceptUuid());
					return remoteResult;
				}
			}
			catch (Exception e) {
				log.warn("Unable to enrich local ICD-11 code " + normalizedCode + " with WHO metadata", e);
			}
			return localResult;
		}
		for (Icd11SearchResult result : searchRemote(normalizedCode, config.getDefaultLinearization()).getResults()) {
			if (normalizedCode.equalsIgnoreCase(result.getIcd11Code())) {
				return result;
			}
		}
		return null;
	}

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	@Transactional(readOnly = true)
	public List<Icd11BrowserNode> browse(String parentCode) {
		return dao.getLocalBrowserNodes(StringUtils.trimToNull(parentCode));
	}

	@Override
	@Authorized(PrivilegeConstants.EDIT_DIAGNOSES)
	public Icd11PatientDiagnosis saveDiagnosis(Icd11PatientDiagnosis diagnosis) {
		validateDiagnosis(diagnosis);
		if (diagnosis.getId() == null) {
			diagnosis.setUuid(UUID.randomUUID().toString());
			diagnosis.setCreator(getAuthenticatedUser());
			diagnosis.setDateCreated(new Date());
			diagnosis.setVoided(false);
		}
		else {
			diagnosis.setChangedBy(getAuthenticatedUser());
			diagnosis.setDateChanged(new Date());
		}
		Icd11PatientDiagnosis saved = dao.saveDiagnosis(diagnosis);
		ensureDiagnosisObservation(saved);
		return saved;
	}

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	@Transactional(readOnly = true)
	public Icd11PatientDiagnosis getDiagnosisByUuid(String uuid) {
		return StringUtils.isBlank(uuid) ? null : dao.getDiagnosisByUuid(uuid.trim());
	}

	@Override
	@Authorized(PrivilegeConstants.GET_DIAGNOSES)
	@Transactional(readOnly = true)
	public List<Icd11PatientDiagnosis> getDiagnoses(Patient patient, Encounter encounter) {
		if (patient == null) {
			throw new IllegalArgumentException("Patient is required");
		}
		return dao.getDiagnoses(patient, encounter);
	}

	@Override
	@Authorized(PrivilegeConstants.DELETE_DIAGNOSES)
	public Icd11PatientDiagnosis voidDiagnosis(Icd11PatientDiagnosis diagnosis, String reason) {
		if (diagnosis == null) {
			throw new IllegalArgumentException("Diagnosis is required");
		}
		diagnosis.setVoided(true);
		diagnosis.setVoidedBy(getAuthenticatedUser());
		diagnosis.setDateVoided(new Date());
		diagnosis.setVoidReason(StringUtils.defaultIfBlank(reason, "Deleted through RwandaEMR ICD-11 REST API"));
		voidDiagnosisObservation(diagnosis, diagnosis.getVoidReason());
		return dao.saveDiagnosis(diagnosis);
	}

	@Override
	@Authorized(PrivilegeConstants.EDIT_DIAGNOSES)
	public List<Icd11PatientDiagnosis> replaceDiagnoses(Patient patient, Encounter encounter,
													   List<Icd11DiagnosisSelection> selections) {
		if (patient == null || encounter == null || !patient.equals(encounter.getPatient())) {
			throw new IllegalArgumentException("A matching patient and encounter are required");
		}
		List<Icd11DiagnosisSelection> requested = selections == null ? new ArrayList<Icd11DiagnosisSelection>() : selections;
		Map<String, Icd11PatientDiagnosis> existingByKey = new HashMap<>();
		for (Icd11PatientDiagnosis diagnosis : dao.getDiagnoses(patient, encounter)) {
			existingByKey.put(buildDiagnosisKey(diagnosis.getEntityUri(), diagnosis.getDiagnosisType(), diagnosis.getCertainty()),
					diagnosis);
		}
		Set<String> selectedKeys = new HashSet<>();
		List<Icd11PatientDiagnosis> saved = new ArrayList<>();
		int primaryDiagnoses = 0;
		for (Icd11DiagnosisSelection selection : requested) {
			validateSelection(selection);
			if ("primary".equalsIgnoreCase(selection.getDiagnosisType()) && ++primaryDiagnoses > 1) {
				throw new IllegalArgumentException("Only one primary ICD-11 diagnosis may be selected");
			}
			String key = buildDiagnosisKey(selection.getEntityUri(), selection.getDiagnosisType(), selection.getCertainty());
			if (!selectedKeys.add(key)) {
				throw new IllegalArgumentException("Duplicate ICD-11 diagnosis selection");
			}
			Icd11PatientDiagnosis diagnosis = existingByKey.get(key);
			if (diagnosis == null) {
				diagnosis = new Icd11PatientDiagnosis();
				diagnosis.setPatient(patient);
				diagnosis.setEncounter(encounter);
				diagnosis.setIcd11Code(StringUtils.trimToNull(selection.getIcd11Code()));
				diagnosis.setEntityUri(selection.getEntityUri().trim());
				diagnosis.setFoundationUri(StringUtils.trimToNull(selection.getFoundationUri()));
				diagnosis.setTitle(selection.getTitle().trim());
				diagnosis.setLinearization(StringUtils.defaultIfBlank(selection.getLinearization(),
						config.getDefaultLinearization()).trim());
				diagnosis.setDiagnosisType(selection.getDiagnosisType().trim().toLowerCase());
				diagnosis.setCertainty(selection.getCertainty().trim().toLowerCase());
				saveDiagnosis(diagnosis);
			}
			else {
				ensureDiagnosisObservation(diagnosis);
			}
			saved.add(diagnosis);
		}
		for (Map.Entry<String, Icd11PatientDiagnosis> entry : existingByKey.entrySet()) {
			if (!selectedKeys.contains(entry.getKey())) {
				voidDiagnosisInternal(entry.getValue(), "Removed through HTML Form Entry ICD-11 widget");
			}
		}
		return saved;
	}

	private Icd11SearchResponse search(String query, String linearization, boolean enforceMinimumLength) {
		if (!config.isEnabled()) {
			throw new IllegalStateException("ICD-11 integration is disabled");
		}
		String normalizedQuery = StringUtils.trimToEmpty(query);
		if (enforceMinimumLength && normalizedQuery.length() < config.getSearchMinChars()) {
			throw new IllegalArgumentException("ICD-11 search query must contain at least " + config.getSearchMinChars() + " characters");
		}
		if (StringUtils.isBlank(normalizedQuery)) {
			throw new IllegalArgumentException("ICD-11 search query is required");
		}
		String requestedLinearization = StringUtils.defaultIfBlank(linearization, config.getDefaultLinearization()).trim();
		List<Icd11SearchResult> localResults = dao.searchLocalCodes(normalizedQuery, requestedLinearization);
		if (!localResults.isEmpty()) {
			return buildResponse(normalizedQuery, requestedLinearization, localResults, false, false, true);
		}
		return searchRemote(normalizedQuery, requestedLinearization);
	}

	private Icd11SearchResponse searchRemote(String normalizedQuery, String requestedLinearization) {
		String cacheKey = buildCacheKey(normalizedQuery, requestedLinearization);
		try {
			List<Icd11SearchResult> results = apiClient.search(normalizedQuery, requestedLinearization);
			cacheResults(cacheKey, normalizedQuery, requestedLinearization, results);
			return buildResponse(normalizedQuery, requestedLinearization, results, false, false, false);
		}
		catch (Exception apiError) {
			log.warn("WHO ICD-11 API search failed; attempting local cache fallback", apiError);
			Icd11CodeCache cacheEntry;
			try {
				cacheEntry = config.isCacheEnabled() ? dao.getCodeCacheByKey(cacheKey) : null;
			}
			catch (Exception cacheReadError) {
				log.error("Unable to read local ICD-11 cache after WHO API failure", cacheReadError);
				throw new Icd11ApiException("WHO ICD-11 API search failed and the local cache could not be read: "
						+ cacheReadError.getMessage(), apiError);
			}
			if (cacheEntry == null) {
				throw new Icd11ApiException("Unable to search WHO ICD-11 API and no local cache entry is available", apiError);
			}
			try {
				List<Icd11SearchResult> results = objectMapper.readValue(cacheEntry.getResponseJson(),
						new TypeReference<List<Icd11SearchResult>>() { });
				boolean stale = cacheEntry.getExpiresAt() == null || cacheEntry.getExpiresAt().before(new Date());
				log.info("Returning " + results.size() + " cached ICD-11 search results"
						+ (stale ? " after cache expiry" : ""));
				return buildResponse(normalizedQuery, requestedLinearization, results, true, stale, false);
			}
			catch (Exception cacheError) {
				throw new Icd11ApiException("Unable to read local ICD-11 cache entry", cacheError);
			}
		}
	}

	protected void cacheResults(String cacheKey, String query, String linearization, List<Icd11SearchResult> results) {
		if (!config.isCacheEnabled()) {
			return;
		}
		try {
			Date now = new Date();
			Icd11CodeCache cacheEntry = dao.getCodeCacheByKey(cacheKey);
			if (cacheEntry == null) {
				cacheEntry = new Icd11CodeCache();
				cacheEntry.setUuid(UUID.randomUUID().toString());
				cacheEntry.setCreator(getAuthenticatedUser());
				cacheEntry.setDateCreated(now);
				cacheEntry.setVoided(false);
			}
			else {
				cacheEntry.setChangedBy(getAuthenticatedUser());
				cacheEntry.setDateChanged(now);
			}
			cacheEntry.setCacheKey(cacheKey);
			cacheEntry.setTitle(query);
			cacheEntry.setLinearization(linearization);
			cacheEntry.setLanguage(config.getLanguage());
			cacheEntry.setApiVersion(config.getApiVersion());
			cacheEntry.setResponseJson(objectMapper.writeValueAsString(results));
			cacheEntry.setExpiresAt(new Date(now.getTime() + config.getCacheTtlHours() * 60L * 60L * 1000L));
			dao.saveCodeCache(cacheEntry);
			log.debug("Cached " + results.size() + " normalized ICD-11 search results");
		}
		catch (Exception e) {
			// A cache write failure must not make a successful WHO search unusable.
			log.warn("Unable to update local ICD-11 search cache", e);
		}
	}

	protected String buildCacheKey(String query, String linearization) {
		String rawKey = "search|" + config.getDefaultReleaseId().toLowerCase() + "|" + linearization.toLowerCase()
				+ "|" + config.getLanguage().toLowerCase()
				+ "|" + config.getApiVersion().toLowerCase() + "|" + query.toLowerCase();
		return "search:" + DigestUtils.sha256Hex(rawKey.getBytes(StandardCharsets.UTF_8));
	}

	protected User getAuthenticatedUser() {
		return Context.getAuthenticatedUser();
	}

	private void validateDiagnosis(Icd11PatientDiagnosis diagnosis) {
		if (diagnosis == null) {
			throw new IllegalArgumentException("Diagnosis is required");
		}
		if (diagnosis.getPatient() == null) {
			throw new IllegalArgumentException("Patient is required");
		}
		if (diagnosis.getEncounter() == null) {
			throw new IllegalArgumentException("Encounter is required");
		}
		if (!diagnosis.getPatient().equals(diagnosis.getEncounter().getPatient())) {
			throw new IllegalArgumentException("Encounter does not belong to patient");
		}
		if (StringUtils.isBlank(diagnosis.getEntityUri())) {
			throw new IllegalArgumentException("ICD-11 entity URI is required");
		}
		if (StringUtils.isBlank(diagnosis.getTitle())) {
			throw new IllegalArgumentException("ICD-11 diagnosis title is required");
		}
		if (!"primary".equalsIgnoreCase(diagnosis.getDiagnosisType())
				&& !"secondary".equalsIgnoreCase(diagnosis.getDiagnosisType())) {
			throw new IllegalArgumentException("ICD-11 diagnosis type must be primary or secondary");
		}
		if (!"confirmed".equalsIgnoreCase(diagnosis.getCertainty())
				&& !"presumed".equalsIgnoreCase(diagnosis.getCertainty())) {
			throw new IllegalArgumentException("ICD-11 diagnosis certainty must be confirmed or presumed");
		}
		if (StringUtils.isBlank(diagnosis.getLinearization())) {
			diagnosis.setLinearization(config.getDefaultLinearization());
		}
	}

	private void validateSelection(Icd11DiagnosisSelection selection) {
		if (selection == null || StringUtils.isBlank(selection.getEntityUri())
				|| StringUtils.isBlank(selection.getTitle())) {
			throw new IllegalArgumentException("Each ICD-11 diagnosis requires an entity URI and title");
		}
		if (!"primary".equalsIgnoreCase(selection.getDiagnosisType())
				&& !"secondary".equalsIgnoreCase(selection.getDiagnosisType())) {
			throw new IllegalArgumentException("ICD-11 diagnosis type must be primary or secondary");
		}
		if (!"confirmed".equalsIgnoreCase(selection.getCertainty())
				&& !"presumed".equalsIgnoreCase(selection.getCertainty())) {
			throw new IllegalArgumentException("ICD-11 diagnosis certainty must be confirmed or presumed");
		}
		if (StringUtils.isBlank(selection.getIcd11Code()) || dao.getLocalConceptByCode(selection.getIcd11Code()) == null) {
			throw new IllegalArgumentException("Selected ICD-11 diagnosis is not available in the local concept dictionary");
		}
	}

	private String buildDiagnosisKey(String entityUri, String diagnosisType, String certainty) {
		return StringUtils.trimToEmpty(entityUri).toLowerCase() + "|"
				+ StringUtils.trimToEmpty(diagnosisType).toLowerCase() + "|"
				+ StringUtils.trimToEmpty(certainty).toLowerCase();
	}

	private void voidDiagnosisInternal(Icd11PatientDiagnosis diagnosis, String reason) {
		diagnosis.setVoided(true);
		diagnosis.setVoidedBy(getAuthenticatedUser());
		diagnosis.setDateVoided(new Date());
		diagnosis.setVoidReason(reason);
		voidDiagnosisObservation(diagnosis, reason);
		dao.saveDiagnosis(diagnosis);
	}

	protected void ensureDiagnosisObservation(Icd11PatientDiagnosis diagnosis) {
		if (diagnosis.getObsGroup() != null) {
			return;
		}
		Concept diagnosisValue = dao.getLocalConceptByCode(diagnosis.getIcd11Code());
		if (diagnosisValue == null) {
			throw new IllegalArgumentException("Selected ICD-11 diagnosis is not available in the local concept dictionary");
		}
		Encounter encounter = diagnosis.getEncounter();
		Date obsDatetime = encounter.getEncounterDatetime() == null ? new Date() : encounter.getEncounterDatetime();
		Obs group = createObs(encounter, requireConcept(VISIT_DIAGNOSES_CONCEPT_UUID), obsDatetime);
		addCodedMember(group, encounter, requireConcept(CODED_DIAGNOSIS_CONCEPT_UUID), diagnosisValue, obsDatetime);
		addCodedMember(group, encounter, requireConcept(DIAGNOSIS_ORDER_CONCEPT_UUID),
				requireConcept("primary".equalsIgnoreCase(diagnosis.getDiagnosisType())
						? PRIMARY_DIAGNOSIS_CONCEPT_UUID : SECONDARY_DIAGNOSIS_CONCEPT_UUID), obsDatetime);
		addCodedMember(group, encounter, requireConcept(DIAGNOSIS_CERTAINTY_CONCEPT_UUID),
				requireConcept("confirmed".equalsIgnoreCase(diagnosis.getCertainty())
						? CONFIRMED_CERTAINTY_CONCEPT_UUID : PRESUMED_CERTAINTY_CONCEPT_UUID), obsDatetime);
		Obs date = createObs(encounter, requireConcept(DIAGNOSIS_DATE_CONCEPT_UUID), obsDatetime);
		date.setValueDate(obsDatetime);
		group.addGroupMember(date);
		encounter.addObs(group);
		diagnosis.setObsGroup(Context.getObsService().saveObs(group, "Saved from RwandaEMR ICD-11 diagnosis capture"));
		dao.saveDiagnosis(diagnosis);
	}

	protected void voidDiagnosisObservation(Icd11PatientDiagnosis diagnosis, String reason) {
		if (diagnosis.getObsGroup() != null && !Boolean.TRUE.equals(diagnosis.getObsGroup().getVoided())) {
			Context.getObsService().voidObs(diagnosis.getObsGroup(), reason);
		}
	}

	private Obs createObs(Encounter encounter, Concept concept, Date obsDatetime) {
		Obs obs = new Obs(encounter.getPatient(), concept, obsDatetime, encounter.getLocation());
		obs.setEncounter(encounter);
		return obs;
	}

	private void addCodedMember(Obs group, Encounter encounter, Concept question, Concept answer, Date obsDatetime) {
		Obs member = createObs(encounter, question, obsDatetime);
		member.setValueCoded(answer);
		group.addGroupMember(member);
	}

	private Concept requireConcept(String uuid) {
		Concept concept = Context.getConceptService().getConceptByUuid(uuid);
		if (concept == null) {
			throw new IllegalStateException("Required diagnosis metadata concept is missing: " + uuid);
		}
		return concept;
	}

	private Icd11SearchResponse buildResponse(String query, String linearization, List<Icd11SearchResult> results,
											 boolean fromCache, boolean stale, boolean localDictionary) {
		Icd11SearchResponse response = new Icd11SearchResponse();
		response.setQuery(query);
		response.setLinearization(linearization);
		response.setResults(results);
		response.setFromCache(fromCache);
		response.setStale(stale);
		response.setLocalDictionary(localDictionary);
		return response;
	}
}
