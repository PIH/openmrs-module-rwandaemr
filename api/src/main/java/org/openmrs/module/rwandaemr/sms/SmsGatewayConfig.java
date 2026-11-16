package org.openmrs.module.rwandaemr.sms;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

@Component
public class SmsGatewayConfig {

	public static final String ENABLED_PROPERTY = "rwandaemr.sms.enabled";
	public static final String POST_URL_PROPERTY = "rwandaemr.sms.postUrl";
	public static final String USERNAME_PROPERTY = "rwandaemr.sms.username";
	public static final String PASSWORD_PROPERTY = "rwandaemr.sms.password";
	public static final String SENDER_PROPERTY = "rwandaemr.sms.sender";
	public static final String CODING_PROPERTY = "rwandaemr.sms.coding";
	public static final String DLR_URL_PROPERTY = "rwandaemr.sms.dlrUrl";
	public static final String DLR_LEVEL_PROPERTY = "rwandaemr.sms.dlrLevel";
	public static final String CONNECT_TIMEOUT_PROPERTY = "rwandaemr.sms.connectionTimeout";
	public static final String READ_TIMEOUT_PROPERTY = "rwandaemr.sms.readTimeout";

	private static final String LEGACY_INTOUCH_URL_PROPERTY = "rwandaemr.labNotification.intouch.url";
	private static final String LEGACY_INTOUCH_USERNAME_PROPERTY = "rwandaemr.labNotification.intouch.username";
	private static final String LEGACY_INTOUCH_PASSWORD_PROPERTY = "rwandaemr.labNotification.intouch.password";
	private static final String LEGACY_INTOUCH_SENDER_PROPERTY = "rwandaemr.labNotification.intouch.sender";
	private static final String LEGACY_INTOUCH_CODING_PROPERTY = "rwandaemr.labNotification.intouch.coding";
	private static final String LEGACY_INTOUCH_DLR_URL_PROPERTY = "rwandaemr.labNotification.intouch.dlrUrl";
	private static final String LEGACY_INTOUCH_DLR_LEVEL_PROPERTY = "rwandaemr.labNotification.intouch.dlrLevel";

	public static final String DEFAULT_INTOUCH_URL = "http://41.186.44.171:1401/send";
	public static final String DEFAULT_CODING = "0";
	public static final String DEFAULT_DLR_LEVEL = "2";
	public static final int DEFAULT_CONNECT_TIMEOUT = 10000;
	public static final int DEFAULT_READ_TIMEOUT = 30000;

	public boolean isEnabled() {
		return getBoolean(ENABLED_PROPERTY, false);
	}

	public String getIntouchUrl() {
		return getString(POST_URL_PROPERTY, LEGACY_INTOUCH_URL_PROPERTY, DEFAULT_INTOUCH_URL);
	}

	public String getUsername() {
		return getString(USERNAME_PROPERTY, LEGACY_INTOUCH_USERNAME_PROPERTY, null);
	}

	public String getPassword() {
		return getString(PASSWORD_PROPERTY, LEGACY_INTOUCH_PASSWORD_PROPERTY, null);
	}

	public String getSender() {
		return getString(SENDER_PROPERTY, LEGACY_INTOUCH_SENDER_PROPERTY, null);
	}

	public String getCoding() {
		return getString(CODING_PROPERTY, LEGACY_INTOUCH_CODING_PROPERTY, DEFAULT_CODING);
	}

	public String getDlrUrl() {
		return getString(DLR_URL_PROPERTY, LEGACY_INTOUCH_DLR_URL_PROPERTY, null);
	}

	public String getDlrLevel() {
		return getString(DLR_LEVEL_PROPERTY, LEGACY_INTOUCH_DLR_LEVEL_PROPERTY, DEFAULT_DLR_LEVEL);
	}

	public int getConnectTimeout() {
		return getInteger(CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT);
	}

	public int getReadTimeout() {
		return getInteger(READ_TIMEOUT_PROPERTY, DEFAULT_READ_TIMEOUT);
	}

	public boolean hasRequiredSettings() {
		return StringUtils.isNotBlank(getIntouchUrl()) && StringUtils.isNotBlank(getUsername()) &&
				StringUtils.isNotBlank(getPassword()) && StringUtils.isNotBlank(getSender());
	}

	private String getString(String propertyName, String legacyPropertyName, String defaultValue) {
		String value = getProperty(propertyName);
		if (StringUtils.isNotBlank(value)) {
			return value.trim();
		}
		value = getProperty(legacyPropertyName);
		return StringUtils.isBlank(value) ? defaultValue : value.trim();
	}

	private boolean getBoolean(String propertyName, boolean defaultValue) {
		String value = getProperty(propertyName);
		return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
	}

	private int getInteger(String propertyName, int defaultValue) {
		String value = getProperty(propertyName);
		if (StringUtils.isBlank(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private String getProperty(String propertyName) {
		try {
			String value = ConfigUtil.getGlobalProperty(propertyName);
			if (StringUtils.isNotBlank(value)) {
				return value;
			}
		}
		catch (RuntimeException e) {
			// Unit tests and startup utilities may read settings before the OpenMRS service context is available.
		}
		String value = ConfigUtil.getRuntimeProperty(propertyName);
		return StringUtils.isBlank(value) ? ConfigUtil.getSystemProperty(propertyName) : value;
	}
}
