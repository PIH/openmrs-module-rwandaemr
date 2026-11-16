package org.openmrs.module.rwandaemr.icd11;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

/**
 * Configuration for the WHO ICD-11 API integration.
 */
@Component
public class Icd11Config {

	public static final String ENABLED_PROPERTY = "rwandaemr.icd11.enabled";
	public static final String CLIENT_ID_PROPERTY = "rwandaemr.icd11.api.clientId";
	public static final String CLIENT_SECRET_PROPERTY = "rwandaemr.icd11.api.clientSecret";
	public static final String BASE_URL_PROPERTY = "rwandaemr.icd11.api.baseUrl";
	public static final String TOKEN_URL_PROPERTY = "rwandaemr.icd11.api.tokenUrl";
	public static final String API_VERSION_PROPERTY = "rwandaemr.icd11.api.version";
	public static final String LANGUAGE_PROPERTY = "rwandaemr.icd11.api.language";
	public static final String DEFAULT_LINEARIZATION_PROPERTY = "rwandaemr.icd11.default.linearization";
	public static final String DEFAULT_RELEASE_ID_PROPERTY = "rwandaemr.icd11.default.releaseId";
	public static final String CACHE_ENABLED_PROPERTY = "rwandaemr.icd11.cache.enabled";
	public static final String CACHE_TTL_HOURS_PROPERTY = "rwandaemr.icd11.cache.ttlHours";
	public static final String SEARCH_MIN_CHARS_PROPERTY = "rwandaemr.icd11.search.minChars";
	public static final String SEARCH_TIMEOUT_SECONDS_PROPERTY = "rwandaemr.icd11.search.timeoutSeconds";

	public static final String DEFAULT_BASE_URL = "https://id.who.int";
	public static final String DEFAULT_TOKEN_URL = "https://icdaccessmanagement.who.int/connect/token";
	public static final String DEFAULT_API_VERSION = "v2";
	public static final String DEFAULT_LANGUAGE = "en";
	public static final String DEFAULT_LINEARIZATION = "mms";
	public static final String DEFAULT_RELEASE_ID = "2026-01";
	public static final int DEFAULT_CACHE_TTL_HOURS = 24;
	public static final int DEFAULT_SEARCH_MIN_CHARS = 3;
	public static final int DEFAULT_SEARCH_TIMEOUT_SECONDS = 10;

	public boolean isEnabled() {
		return getBoolean(ENABLED_PROPERTY, false);
	}

	public String getClientId() {
		return ConfigUtil.getProperty(CLIENT_ID_PROPERTY);
	}

	public String getClientSecret() {
		return ConfigUtil.getProperty(CLIENT_SECRET_PROPERTY);
	}

	public String getBaseUrl() {
		return withoutTrailingSlash(getString(BASE_URL_PROPERTY, DEFAULT_BASE_URL));
	}

	public String getTokenUrl() {
		return getString(TOKEN_URL_PROPERTY, DEFAULT_TOKEN_URL);
	}

	public String getApiVersion() {
		return getString(API_VERSION_PROPERTY, DEFAULT_API_VERSION);
	}

	public String getLanguage() {
		return getString(LANGUAGE_PROPERTY, DEFAULT_LANGUAGE);
	}

	public String getDefaultLinearization() {
		return getString(DEFAULT_LINEARIZATION_PROPERTY, DEFAULT_LINEARIZATION);
	}

	public String getDefaultReleaseId() {
		return getString(DEFAULT_RELEASE_ID_PROPERTY, DEFAULT_RELEASE_ID);
	}

	public boolean isCacheEnabled() {
		return getBoolean(CACHE_ENABLED_PROPERTY, true);
	}

	public int getCacheTtlHours() {
		return getPositiveInt(CACHE_TTL_HOURS_PROPERTY, DEFAULT_CACHE_TTL_HOURS);
	}

	public int getSearchMinChars() {
		return getPositiveInt(SEARCH_MIN_CHARS_PROPERTY, DEFAULT_SEARCH_MIN_CHARS);
	}

	public int getSearchTimeoutSeconds() {
		return getPositiveInt(SEARCH_TIMEOUT_SECONDS_PROPERTY, DEFAULT_SEARCH_TIMEOUT_SECONDS);
	}

	public boolean hasApiCredentials() {
		return StringUtils.isNotBlank(getClientId()) && StringUtils.isNotBlank(getClientSecret());
	}

	protected String getString(String propertyName, String defaultValue) {
		String value = ConfigUtil.getProperty(propertyName);
		return StringUtils.isBlank(value) ? defaultValue : value.trim();
	}

	protected boolean getBoolean(String propertyName, boolean defaultValue) {
		String value = ConfigUtil.getProperty(propertyName);
		return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
	}

	protected int getPositiveInt(String propertyName, int defaultValue) {
		String value = ConfigUtil.getProperty(propertyName);
		if (StringUtils.isNotBlank(value)) {
			try {
				int parsed = Integer.parseInt(value.trim());
				if (parsed > 0) {
					return parsed;
				}
			}
			catch (NumberFormatException ignored) {
				// Fall through to the documented default.
			}
		}
		return defaultValue;
	}

	private String withoutTrailingSlash(String value) {
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}
}
