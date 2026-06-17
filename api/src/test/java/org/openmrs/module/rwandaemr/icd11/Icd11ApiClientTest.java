package org.openmrs.module.rwandaemr.icd11;

import org.junit.jupiter.api.Test;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Icd11ApiClientTest {

	@Test
	public void shouldCacheTokenAndNormalizeWhoSearchResults() {
		StubApiClient client = new StubApiClient(new StubConfig());

		List<Icd11SearchResult> firstResults = client.search("cholera", "mms");
		List<Icd11SearchResult> secondResults = client.search("cholera", "mms");

		assertEquals(1, client.tokenRequests);
		assertEquals(2, client.searchRequests);
		assertEquals(1, firstResults.size());
		assertEquals("1A00", firstResults.get(0).getIcd11Code());
		assertEquals("http://id.who.int/icd/release/11/2026-01/mms/257068234", firstResults.get(0).getEntityUri());
		assertEquals("http://id.who.int/icd/entity/257068234", firstResults.get(0).getFoundationUri());
		assertEquals("Cholera", firstResults.get(0).getTitle());
		assertEquals("mms", firstResults.get(0).getLinearization());
		assertEquals(firstResults.get(0).getEntityUri(), secondResults.get(0).getEntityUri());
	}

	@Test
	public void shouldRefreshTokenAndRetryAfterUnauthorizedSearch() {
		StubApiClient client = new StubApiClient(new StubConfig());
		client.rejectFirstSearch = true;

		List<Icd11SearchResult> results = client.search("cholera", "mms");

		assertEquals(2, client.tokenRequests);
		assertEquals(2, client.searchRequests);
		assertEquals(1, results.size());
	}

	@Test
	public void shouldBuildMmsSearchEndpointWithConfiguredRelease() {
		StubApiClient client = new StubApiClient(new StubConfig());

		assertEquals("https://id.who.int/icd/release/11/2026-01/mms/search", client.buildSearchEndpoint("mms"));
	}

	@Test
	public void shouldNormalizeWhoCodeDetails() {
		StubApiClient client = new StubApiClient(new StubConfig());

		Icd11SearchResult result = client.getCodeDetails("1A00", "mms");

		assertEquals("257068234", client.requestedEntityId);
		assertEquals("1A00", result.getIcd11Code());
		assertEquals("Cholera", result.getTitle());
		assertEquals("An infection caused by Vibrio cholerae.", result.getDefinition());
		assertEquals("Vibrio cholerae infection", result.getExclusions().get(0).getLabel());
		assertEquals("hasManifestation", result.getPostcoordinationScales().get(0).getAxisName());
		assertTrue(result.isRemoteMetadata());
	}

	private static class StubApiClient extends Icd11ApiClient {

		private int tokenRequests;

		private int searchRequests;

		private boolean rejectFirstSearch;

		private String requestedEntityId;

		private StubApiClient(Icd11Config config) {
			super(config);
		}

		@Override
		protected HttpPayload executeTokenRequest() {
			tokenRequests++;
			return new HttpPayload(200, "{\"access_token\":\"token-" + tokenRequests + "\",\"expires_in\":3600}");
		}

		@Override
		protected HttpPayload executeSearchRequest(String query, String linearization, String token) {
			searchRequests++;
			if (rejectFirstSearch && searchRequests == 1) {
				return new HttpPayload(401, "");
			}
			return new HttpPayload(200, "{\"destinationEntities\":[{"
					+ "\"id\":\"http://id.who.int/icd/release/11/2026-01/mms/257068234\","
					+ "\"title\":\"<em>Cholera</em>\","
					+ "\"theCode\":\"1A00\","
					+ "\"foundationUri\":\"http://id.who.int/icd/entity/257068234\"}]}");
		}

		@Override
		protected HttpPayload executeCodeInfoRequest(String code, String linearization, String token) {
			return new HttpPayload(200,
					"{\"stemId\":\"http://id.who.int/icd/release/11/2026-01/mms/257068234\"}");
		}

		@Override
		protected HttpPayload executeEntityRequest(String entityId, String linearization, String token) {
			requestedEntityId = entityId;
			return new HttpPayload(200, "{"
					+ "\"@id\":\"http://id.who.int/icd/release/11/2026-01/mms/257068234\","
					+ "\"source\":\"http://id.who.int/icd/entity/257068234\","
					+ "\"code\":\"1A00\","
					+ "\"title\":{\"@language\":\"en\",\"@value\":\"Cholera\"},"
					+ "\"definition\":{\"@language\":\"en\",\"@value\":\"An infection caused by Vibrio cholerae.\"},"
					+ "\"exclusion\":[{\"label\":{\"@language\":\"en\",\"@value\":\"Vibrio cholerae infection\"}}],"
					+ "\"postcoordinationScale\":[{\"axisName\":\"hasManifestation\","
					+ "\"requiredPostcoordination\":\"true\",\"scaleEntity\":[\"http://id.who.int/icd/entity/123\"]}]}");
		}
	}

	private static class StubConfig extends Icd11Config {

		@Override
		public boolean hasApiCredentials() {
			return true;
		}

		@Override
		public String getDefaultLinearization() {
			return DEFAULT_LINEARIZATION;
		}

		@Override
		public String getBaseUrl() {
			return DEFAULT_BASE_URL;
		}

		@Override
		public String getDefaultReleaseId() {
			return DEFAULT_RELEASE_ID;
		}
	}
}
