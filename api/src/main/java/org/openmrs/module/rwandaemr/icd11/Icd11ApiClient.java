package org.openmrs.module.rwandaemr.icd11;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PostcoordinationScale;
import org.openmrs.module.rwandaemr.icd11.model.Icd11SearchResult;
import org.openmrs.module.rwandaemr.icd11.model.Icd11Term;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for authenticated ICD-11 searches against the WHO ICD API.
 */
public class Icd11ApiClient {

	protected final Log log = LogFactory.getLog(getClass());

	private static final long TOKEN_EXPIRY_MARGIN_MS = 60000;

	private final Object tokenLock = new Object();

	private final ObjectMapper objectMapper;

	private Icd11Config config;

	private volatile String accessToken;

	private volatile long accessTokenExpiresAt;

	public Icd11ApiClient() {
		this(new Icd11Config(), new ObjectMapper());
	}

	public Icd11ApiClient(Icd11Config config) {
		this(config, new ObjectMapper());
	}

	Icd11ApiClient(Icd11Config config, ObjectMapper objectMapper) {
		this.config = config;
		this.objectMapper = objectMapper;
	}

	public void setConfig(Icd11Config config) {
		this.config = config;
	}

	public List<Icd11SearchResult> search(String query, String linearization) {
		if (StringUtils.isBlank(query)) {
			return Collections.emptyList();
		}
		if (!config.hasApiCredentials()) {
			throw new Icd11ApiException("WHO ICD-11 API credentials are not configured");
		}
		String requestedLinearization = StringUtils.defaultIfBlank(linearization, config.getDefaultLinearization());
		try {
			HttpPayload response = executeSearchRequest(query.trim(), requestedLinearization, getAccessToken());
			if (response.statusCode == 401) {
				log.warn("WHO ICD-11 API rejected cached access token; refreshing token and retrying search");
				invalidateAccessToken();
				response = executeSearchRequest(query.trim(), requestedLinearization, getAccessToken());
			}
			if (response.statusCode < 200 || response.statusCode >= 300) {
				throw new Icd11ApiException("WHO ICD-11 search failed with HTTP status " + response.statusCode);
			}
			return normalizeSearchResults(response.body, requestedLinearization);
		}
		catch (Icd11ApiException e) {
			throw e;
		}
		catch (Exception e) {
			throw new Icd11ApiException("Unable to search WHO ICD-11 API", e);
		}
	}

	public Icd11SearchResult getCodeDetails(String code, String linearization) {
		if (StringUtils.isBlank(code)) {
			throw new IllegalArgumentException("ICD-11 code is required");
		}
		if (!config.hasApiCredentials()) {
			throw new Icd11ApiException("WHO ICD-11 API credentials are not configured");
		}
		String requestedLinearization = StringUtils.defaultIfBlank(linearization, config.getDefaultLinearization());
		try {
			HttpPayload codeInfo = executeCodeInfoRequest(code.trim(), requestedLinearization, getAccessToken());
			if (codeInfo.statusCode == 401) {
				invalidateAccessToken();
				codeInfo = executeCodeInfoRequest(code.trim(), requestedLinearization, getAccessToken());
			}
			if (codeInfo.statusCode < 200 || codeInfo.statusCode >= 300) {
				throw new Icd11ApiException("WHO ICD-11 code lookup failed with HTTP status " + codeInfo.statusCode);
			}
			JsonNode codeInfoJson = objectMapper.readTree(codeInfo.body);
			String stemId = firstText(codeInfoJson, "stemId");
			if (StringUtils.isBlank(stemId)) {
				throw new Icd11ApiException("WHO ICD-11 code lookup response did not contain a stem id");
			}
			String entityId = entityIdFromUri(stemId);
			HttpPayload entity = executeEntityRequest(entityId, requestedLinearization, getAccessToken());
			if (entity.statusCode == 401) {
				invalidateAccessToken();
				entity = executeEntityRequest(entityId, requestedLinearization, getAccessToken());
			}
			if (entity.statusCode < 200 || entity.statusCode >= 300) {
				throw new Icd11ApiException("WHO ICD-11 entity lookup failed with HTTP status " + entity.statusCode);
			}
			return normalizeEntityDetails(entity.body, requestedLinearization);
		}
		catch (Icd11ApiException e) {
			throw e;
		}
		catch (Exception e) {
			throw new Icd11ApiException("Unable to retrieve WHO ICD-11 code details", e);
		}
	}

	protected HttpPayload executeSearchRequest(String query, String linearization, String token) throws Exception {
		String endpoint = buildSearchEndpoint(linearization);
		URIBuilder uriBuilder = new URIBuilder(endpoint);
		uriBuilder.addParameter("q", query);
		HttpGet request = new HttpGet(uriBuilder.build());
		request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		request.setHeader("API-Version", config.getApiVersion());
		request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, config.getLanguage());
		log.debug("Searching WHO ICD-11 API endpoint for query length " + query.length() + " using linearization " + linearization);
		return execute(request);
	}

	protected String buildSearchEndpoint(String linearization) {
		return config.getBaseUrl() + "/icd/release/11/" + config.getDefaultReleaseId() + "/" + linearization + "/search";
	}

	protected HttpPayload executeCodeInfoRequest(String code, String linearization, String token) throws Exception {
		HttpGet request = new HttpGet(new URIBuilder(config.getBaseUrl() + "/icd/release/11/"
				+ config.getDefaultReleaseId() + "/" + linearization + "/codeinfo/" + code).build());
		addApiHeaders(request, token);
		return execute(request);
	}

	protected HttpPayload executeEntityRequest(String entityId, String linearization, String token) throws Exception {
		HttpGet request = new HttpGet(new URIBuilder(config.getBaseUrl() + "/icd/release/11/"
				+ config.getDefaultReleaseId() + "/" + linearization + "/" + entityId).build());
		addApiHeaders(request, token);
		return execute(request);
	}

	protected HttpPayload executeTokenRequest() throws Exception {
		HttpPost request = new HttpPost(config.getTokenUrl());
		String credentials = config.getClientId() + ":" + config.getClientSecret();
		String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
		request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
		request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		List<NameValuePair> parameters = new ArrayList<>();
		parameters.add(new BasicNameValuePair("grant_type", "client_credentials"));
		parameters.add(new BasicNameValuePair("scope", "icdapi_access"));
		request.setEntity(new UrlEncodedFormEntity(parameters, StandardCharsets.UTF_8));
		log.debug("Requesting WHO ICD-11 API OAuth2 access token");
		return execute(request);
	}

	protected HttpPayload execute(org.apache.http.client.methods.HttpUriRequest request) throws Exception {
		int timeoutMs = config.getSearchTimeoutSeconds() * 1000;
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(timeoutMs)
				.setSocketTimeout(timeoutMs)
				.setConnectionRequestTimeout(timeoutMs)
				.build();
		try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();
			 CloseableHttpResponse response = httpClient.execute(request)) {
			return new HttpPayload(response.getStatusLine().getStatusCode(),
					response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
		}
	}

	protected List<Icd11SearchResult> normalizeSearchResults(String responseBody, String linearization) throws Exception {
		JsonNode root = objectMapper.readTree(responseBody);
		JsonNode entities = root.path("destinationEntities");
		if (!entities.isArray()) {
			entities = root.path("entities");
		}
		if (!entities.isArray()) {
			log.warn("WHO ICD-11 search response did not contain a result array");
			return Collections.emptyList();
		}

		List<Icd11SearchResult> results = new ArrayList<>();
		for (JsonNode entity : entities) {
			String entityUri = firstText(entity, "id", "@id", "entityUri", "linearizationUri");
			String title = cleanTitle(firstText(entity, "title", "label"));
			if (StringUtils.isBlank(entityUri) || StringUtils.isBlank(title)) {
				log.debug("Skipping WHO ICD-11 search result without URI or title");
				continue;
			}
			Icd11SearchResult result = new Icd11SearchResult();
			result.setIcd11Code(firstText(entity, "theCode", "code"));
			result.setEntityUri(entityUri);
			result.setFoundationUri(firstText(entity, "foundationUri", "foundationReference"));
			result.setTitle(title);
			result.setLinearization(linearization);
			results.add(result);
		}
		return results;
	}

	protected Icd11SearchResult normalizeEntityDetails(String responseBody, String linearization) throws Exception {
		JsonNode entity = objectMapper.readTree(responseBody);
		Icd11SearchResult result = new Icd11SearchResult();
		result.setIcd11Code(firstText(entity, "code"));
		result.setEntityUri(firstText(entity, "@id", "id"));
		result.setFoundationUri(firstText(entity, "source", "foundationUri"));
		result.setTitle(firstText(entity, "title"));
		result.setLinearization(linearization);
		result.setFullySpecifiedName(firstText(entity, "fullySpecifiedName"));
		result.setDefinition(firstText(entity, "definition"));
		result.setLongDefinition(firstText(entity, "longDefinition"));
		result.setCodingNote(firstText(entity, "codingNote"));
		result.setBrowserUrl(firstText(entity, "browserUrl"));
		result.setInclusions(normalizeTerms(entity.path("inclusion")));
		result.setExclusions(normalizeTerms(entity.path("exclusion")));
		result.setIndexTerms(normalizeTerms(entity.path("indexTerm")));
		result.setRelatedEntitiesInMaternalChapter(textValues(entity.path("relatedEntitiesInMaternalChapter")));
		result.setRelatedEntitiesInPerinatalChapter(textValues(entity.path("relatedEntitiesInPerinatalChapter")));
		result.setPostcoordinationScales(normalizePostcoordinationScales(entity.path("postcoordinationScale")));
		result.setRemoteMetadata(true);
		return result;
	}

	private List<Icd11Term> normalizeTerms(JsonNode terms) {
		List<Icd11Term> results = new ArrayList<>();
		if (!terms.isArray()) {
			return results;
		}
		for (JsonNode term : terms) {
			Icd11Term result = new Icd11Term();
			result.setLabel(firstText(term, "label"));
			result.setFoundationReference(firstText(term, "foundationReference"));
			result.setLinearizationReference(firstText(term, "linearizationReference"));
			results.add(result);
		}
		return results;
	}

	private List<String> textValues(JsonNode values) {
		List<String> results = new ArrayList<>();
		if (values.isArray()) {
			for (JsonNode value : values) {
				if (value.isTextual()) {
					results.add(value.asText());
				}
			}
		}
		return results;
	}

	private List<Icd11PostcoordinationScale> normalizePostcoordinationScales(JsonNode scales) {
		List<Icd11PostcoordinationScale> results = new ArrayList<>();
		if (!scales.isArray()) {
			return results;
		}
		for (JsonNode scale : scales) {
			Icd11PostcoordinationScale result = new Icd11PostcoordinationScale();
			result.setAxisName(firstText(scale, "axisName"));
			result.setRequired(Boolean.parseBoolean(firstText(scale, "requiredPostcoordination")));
			result.setAllowMultipleValues(firstText(scale, "allowMultipleValues"));
			result.setScaleEntities(textValues(scale.path("scaleEntity")));
			results.add(result);
		}
		return results;
	}

	private void addApiHeaders(HttpGet request, String token) {
		request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		request.setHeader("API-Version", config.getApiVersion());
		request.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, config.getLanguage());
	}

	private String entityIdFromUri(String entityIdOrUri) {
		String value = StringUtils.removeEnd(entityIdOrUri.trim(), "/");
		return value.substring(value.lastIndexOf('/') + 1);
	}

	private String getAccessToken() throws Exception {
		if (isAccessTokenValid()) {
			return accessToken;
		}
		synchronized (tokenLock) {
			if (isAccessTokenValid()) {
				return accessToken;
			}
			HttpPayload response = executeTokenRequest();
			if (response.statusCode < 200 || response.statusCode >= 300) {
				throw new Icd11ApiException("WHO ICD-11 OAuth2 token request failed with HTTP status " + response.statusCode);
			}
			JsonNode tokenResponse = objectMapper.readTree(response.body);
			String token = tokenResponse.path("access_token").asText(null);
			if (StringUtils.isBlank(token)) {
				throw new Icd11ApiException("WHO ICD-11 OAuth2 token response did not contain an access token");
			}
			long expiresInSeconds = tokenResponse.path("expires_in").asLong(3600);
			accessToken = token;
			accessTokenExpiresAt = System.currentTimeMillis() + Math.max(1, expiresInSeconds) * 1000;
			log.debug("Cached WHO ICD-11 API access token");
			return accessToken;
		}
	}

	private boolean isAccessTokenValid() {
		return StringUtils.isNotBlank(accessToken)
				&& System.currentTimeMillis() < accessTokenExpiresAt - TOKEN_EXPIRY_MARGIN_MS;
	}

	private void invalidateAccessToken() {
		synchronized (tokenLock) {
			accessToken = null;
			accessTokenExpiresAt = 0;
		}
	}

	private String firstText(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isTextual() && StringUtils.isNotBlank(value.asText())) {
				return value.asText();
			}
			if (value.isObject() && value.path("@value").isTextual()) {
				return value.path("@value").asText();
			}
		}
		return null;
	}

	private String cleanTitle(String title) {
		return title == null ? null : title.replaceAll("<[^>]+>", "").trim();
	}

	protected static class HttpPayload {

		private final int statusCode;

		private final String body;

		protected HttpPayload(int statusCode, String body) {
			this.statusCode = statusCode;
			this.body = body;
		}
	}
}
