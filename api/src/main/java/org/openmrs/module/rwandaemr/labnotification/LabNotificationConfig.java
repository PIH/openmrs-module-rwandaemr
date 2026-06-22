package org.openmrs.module.rwandaemr.labnotification;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

@Component
public class LabNotificationConfig {

	public static final String ENABLED_PROPERTY = "rwandaemr.labNotification.enabled";
	public static final String INTOUCH_URL_PROPERTY = "rwandaemr.labNotification.intouch.url";
	public static final String INTOUCH_USERNAME_PROPERTY = "rwandaemr.labNotification.intouch.username";
	public static final String INTOUCH_PASSWORD_PROPERTY = "rwandaemr.labNotification.intouch.password";
	public static final String INTOUCH_SENDER_PROPERTY = "rwandaemr.labNotification.intouch.sender";
	public static final String INTOUCH_CODING_PROPERTY = "rwandaemr.labNotification.intouch.coding";
	public static final String INTOUCH_DLR_URL_PROPERTY = "rwandaemr.labNotification.intouch.dlrUrl";
	public static final String INTOUCH_DLR_LEVEL_PROPERTY = "rwandaemr.labNotification.intouch.dlrLevel";
	public static final String MESSAGE_TEMPLATE_PROPERTY = "rwandaemr.labNotification.messageTemplate";

	public static final String DEFAULT_INTOUCH_URL = "http://41.186.44.171:1401/send";
	public static final String DEFAULT_CODING = "0";
	public static final String DEFAULT_DLR_LEVEL = "2";
	public static final String DEFAULT_MESSAGE_TEMPLATE =
			"Your laboratory results are ready at {facility}. Please return to the facility or contact your provider.";

	public boolean isEnabled() {
		return getBoolean(ENABLED_PROPERTY, false);
	}

	public String getIntouchUrl() {
		return getString(INTOUCH_URL_PROPERTY, DEFAULT_INTOUCH_URL);
	}

	public String getUsername() {
		return ConfigUtil.getProperty(INTOUCH_USERNAME_PROPERTY);
	}

	public String getPassword() {
		return ConfigUtil.getProperty(INTOUCH_PASSWORD_PROPERTY);
	}

	public String getSender() {
		return ConfigUtil.getProperty(INTOUCH_SENDER_PROPERTY);
	}

	public String getCoding() {
		return getString(INTOUCH_CODING_PROPERTY, DEFAULT_CODING);
	}

	public String getDlrUrl() {
		return ConfigUtil.getProperty(INTOUCH_DLR_URL_PROPERTY);
	}

	public String getDlrLevel() {
		return getString(INTOUCH_DLR_LEVEL_PROPERTY, DEFAULT_DLR_LEVEL);
	}

	public String getMessageTemplate() {
		return getString(MESSAGE_TEMPLATE_PROPERTY, DEFAULT_MESSAGE_TEMPLATE);
	}

	public boolean hasRequiredSettings() {
		return StringUtils.isNotBlank(getIntouchUrl()) && StringUtils.isNotBlank(getUsername()) &&
				StringUtils.isNotBlank(getPassword()) && StringUtils.isNotBlank(getSender());
	}

	protected String getString(String propertyName, String defaultValue) {
		String value = ConfigUtil.getProperty(propertyName);
		return StringUtils.isBlank(value) ? defaultValue : value.trim();
	}

	protected boolean getBoolean(String propertyName, boolean defaultValue) {
		String value = ConfigUtil.getProperty(propertyName);
		return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
	}
}
