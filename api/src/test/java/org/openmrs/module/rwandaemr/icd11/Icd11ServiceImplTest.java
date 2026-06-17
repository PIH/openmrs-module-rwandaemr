package org.openmrs.module.rwandaemr.icd11;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Concept;
import org.openmrs.User;
import org.openmrs.module.rwandaemr.icd11.dao.Icd11Dao;
import org.openmrs.module.rwandaemr.icd11.model.Icd11CodeCache;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResponse;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11BrowserNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Icd11ServiceImplTest {

	private StubConfig config;

	private StubDao dao;

	private StubApiClient apiClient;

	private TestIcd11Service service;

	@BeforeEach
	public void setUp() {
		config = new StubConfig();
		dao = new StubDao();
		apiClient = new StubApiClient(config);
		service = new TestIcd11Service();
		service.setConfig(config);
		service.setDao(dao);
		service.setApiClient(apiClient);
	}

	@Test
	public void shouldCacheSuccessfulSearchResults() {
		Icd11SearchResponse response = service.search("cholera");

		assertFalse(response.isFromCache());
		assertFalse(response.isStale());
		assertEquals(1, response.getResults().size());
		assertEquals("1A00", response.getResults().get(0).getIcd11Code());
		assertEquals("mms", dao.saved.getLinearization());
		assertEquals("en", dao.saved.getLanguage());
		assertTrue(dao.saved.getResponseJson().contains("\"icd11Code\":\"1A00\""));
	}

	@Test
	public void shouldPreferLocalDictionarySearchResults() {
		dao.localResults = Collections.singletonList(selectionResult("1A00", "Cholera"));

		Icd11SearchResponse response = service.search("cholera");

		assertTrue(response.isLocalDictionary());
		assertFalse(response.isFromCache());
		assertEquals(0, apiClient.searches);
	}

	@Test
	public void shouldBrowseLocalDictionaryNodes() {
		Icd11BrowserNode node = new Icd11BrowserNode();
		node.setCode("1A");
		node.setTitle("ICD-11 categories 1A");
		node.setHasChildren(true);
		dao.browserNodes = Collections.singletonList(node);

		List<Icd11BrowserNode> results = service.browse(null);

		assertEquals(1, results.size());
		assertEquals("1A", results.get(0).getCode());
		assertTrue(results.get(0).isHasChildren());
	}

	@Test
	public void shouldUseStaleCacheWhenWhoApiFails() {
		service.search("cholera");
		dao.saved.setExpiresAt(new Date(System.currentTimeMillis() - 1000));
		apiClient.fail = true;

		Icd11SearchResponse response = service.search("cholera");

		assertTrue(response.isFromCache());
		assertTrue(response.isStale());
		assertEquals("Cholera", response.getResults().get(0).getTitle());
	}

	@Test
	public void shouldDescribeCacheReadFailureAfterWhoApiFails() {
		apiClient.fail = true;
		dao.cacheReadError = new RuntimeException("cache table is unavailable");

		Icd11ApiException exception = assertThrows(Icd11ApiException.class, () -> service.search("cholera"));

		assertTrue(exception.getMessage().contains("WHO ICD-11 API search failed"));
		assertTrue(exception.getMessage().contains("cache table is unavailable"));
	}

	@Test
	public void shouldFindExactCodeWithoutApplyingSearchMinimumLength() {
		Icd11SearchResult result = service.getCode("1A");

		assertNotNull(result);
		assertEquals("1A", result.getIcd11Code());
	}

	@Test
	public void shouldSaveListAndVoidDiagnosis() {
		Patient patient = new Patient();
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		Icd11PatientDiagnosis diagnosis = new Icd11PatientDiagnosis();
		diagnosis.setPatient(patient);
		diagnosis.setEncounter(encounter);
		diagnosis.setEntityUri("http://id.who.int/icd/release/11/2026-01/mms/257068234");
		diagnosis.setTitle("Cholera");
		diagnosis.setDiagnosisType("primary");
		diagnosis.setCertainty("confirmed");

		service.saveDiagnosis(diagnosis);

		assertNotNull(diagnosis.getUuid());
		assertEquals("mms", diagnosis.getLinearization());
		assertFalse(diagnosis.getVoided());
		assertEquals(1, service.getDiagnoses(patient, encounter).size());

		service.voidDiagnosis(diagnosis, "Entered in error");

		assertTrue(diagnosis.getVoided());
		assertEquals("Entered in error", diagnosis.getVoidReason());
		assertEquals(0, service.getDiagnoses(patient, encounter).size());
	}

	@Test
	public void shouldReplaceEncounterDiagnosesFromHtmlFormSelections() {
		Patient patient = new Patient();
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		Icd11DiagnosisSelection cholera = selection("1A00", "cholera", "Cholera", "primary", "confirmed");
		Icd11DiagnosisSelection typhoid = selection("1A07", "typhoid", "Typhoid fever", "secondary", "presumed");

		service.replaceDiagnoses(patient, encounter, java.util.Arrays.asList(cholera, typhoid));
		service.replaceDiagnoses(patient, encounter, Collections.singletonList(typhoid));

		List<Icd11PatientDiagnosis> diagnoses = service.getDiagnoses(patient, encounter);
		assertEquals(1, diagnoses.size());
		assertEquals("1A07", diagnoses.get(0).getIcd11Code());
		assertEquals("secondary", diagnoses.get(0).getDiagnosisType());
		assertEquals("presumed", diagnoses.get(0).getCertainty());
	}

	private Icd11DiagnosisSelection selection(String code, String uri, String title, String diagnosisType, String certainty) {
		Icd11DiagnosisSelection selection = new Icd11DiagnosisSelection();
		selection.setIcd11Code(code);
		selection.setEntityUri(uri);
		selection.setTitle(title);
		selection.setDiagnosisType(diagnosisType);
		selection.setCertainty(certainty);
		return selection;
	}

	private Icd11SearchResult selectionResult(String code, String title) {
		Icd11SearchResult result = new Icd11SearchResult();
		result.setIcd11Code(code);
		result.setTitle(title);
		result.setEntityUri("local:concept/test");
		result.setConceptUuid("test");
		return result;
	}

	private static class TestIcd11Service extends Icd11ServiceImpl {

		@Override
		protected User getAuthenticatedUser() {
			return new User();
		}

		@Override
		protected void ensureDiagnosisObservation(Icd11PatientDiagnosis diagnosis) {
			// Observation persistence is covered by integration behavior in the OpenMRS context.
		}

		@Override
		protected void voidDiagnosisObservation(Icd11PatientDiagnosis diagnosis, String reason) {
			// Observation persistence is covered by integration behavior in the OpenMRS context.
		}
	}

	private static class StubApiClient extends Icd11ApiClient {

		private boolean fail;

		private int searches;

		private StubApiClient(Icd11Config config) {
			super(config);
		}

		@Override
		public java.util.List<Icd11SearchResult> search(String query, String linearization) {
			searches++;
			if (fail) {
				throw new Icd11ApiException("WHO unavailable");
			}
			Icd11SearchResult result = new Icd11SearchResult();
			result.setIcd11Code("1A".equals(query) ? "1A" : "1A00");
			result.setEntityUri("http://id.who.int/icd/release/11/2026-01/mms/257068234");
			result.setFoundationUri("http://id.who.int/icd/entity/257068234");
			result.setTitle("Cholera");
			result.setLinearization(linearization);
			return Collections.singletonList(result);
		}
	}

	private static class StubDao implements Icd11Dao {

		private Icd11CodeCache saved;

		private final List<Icd11PatientDiagnosis> diagnoses = new ArrayList<>();

		private RuntimeException cacheReadError;

		private List<Icd11SearchResult> localResults = Collections.emptyList();

		private List<Icd11BrowserNode> browserNodes = Collections.emptyList();

		@Override
		public Icd11CodeCache getCodeCacheByKey(String cacheKey) {
			if (cacheReadError != null) {
				throw cacheReadError;
			}
			return saved != null && cacheKey.equals(saved.getCacheKey()) ? saved : null;
		}

		@Override
		public Icd11CodeCache saveCodeCache(Icd11CodeCache cacheEntry) {
			saved = cacheEntry;
			return saved;
		}

		@Override
		public Icd11PatientDiagnosis saveDiagnosis(Icd11PatientDiagnosis diagnosis) {
			if (!diagnoses.contains(diagnosis)) {
				diagnoses.add(diagnosis);
			}
			return diagnosis;
		}

		@Override
		public Icd11PatientDiagnosis getDiagnosisByUuid(String uuid) {
			for (Icd11PatientDiagnosis diagnosis : diagnoses) {
				if (uuid.equals(diagnosis.getUuid())) {
					return diagnosis;
				}
			}
			return null;
		}

		@Override
		public List<Icd11PatientDiagnosis> getDiagnoses(Patient patient, Encounter encounter) {
			List<Icd11PatientDiagnosis> matches = new ArrayList<>();
			for (Icd11PatientDiagnosis diagnosis : diagnoses) {
				if (patient.equals(diagnosis.getPatient())
						&& (encounter == null || encounter.equals(diagnosis.getEncounter()))
						&& !Boolean.TRUE.equals(diagnosis.getVoided())) {
					matches.add(diagnosis);
				}
			}
			return matches;
		}

		@Override
		public List<Icd11SearchResult> searchLocalCodes(String query, String linearization) {
			return localResults;
		}

		@Override
		public Concept getLocalConceptByCode(String code) {
			return new Concept();
		}

		@Override
		public Icd11SearchResult getLocalCode(String code, String linearization) {
			return null;
		}

		@Override
		public List<Icd11BrowserNode> getLocalBrowserNodes(String parentCode) {
			return browserNodes;
		}
	}

	private static class StubConfig extends Icd11Config {

		@Override
		public boolean isEnabled() {
			return true;
		}

		@Override
		public boolean isCacheEnabled() {
			return true;
		}

		@Override
		public String getDefaultLinearization() {
			return DEFAULT_LINEARIZATION;
		}

		@Override
		public String getDefaultReleaseId() {
			return DEFAULT_RELEASE_ID;
		}

		@Override
		public String getLanguage() {
			return DEFAULT_LANGUAGE;
		}

		@Override
		public String getApiVersion() {
			return DEFAULT_API_VERSION;
		}

		@Override
		public int getCacheTtlHours() {
			return DEFAULT_CACHE_TTL_HOURS;
		}

		@Override
		public int getSearchMinChars() {
			return DEFAULT_SEARCH_MIN_CHARS;
		}
	}
}
