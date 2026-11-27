package org.openmrs.module.rwandaemr.labnotification;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.rwandaemr.sms.SmsGatewayConfig;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

@Component
public class LabNotificationConfig {

	public static final String ENABLED_PROPERTY = "rwandaemr.labNotification.enabled";
	/** When true, an SMS is also sent automatically each time a lab order is completed (one SMS per order). */
	/**
	 * Which SMS service sends lab notifications: "rbcmessaging" (default) uses the rbcmessaging module and its
	 * configured gateway (rbcmessaging.gateway, rbcmessaging.mohpih.* for the MOH FDI Biz API, as the old lab module did);
	 * "intouch" uses the Intouch gateway directly with the rwandaemr.sms / rwandaemr.labNotification.intouch settings.
	 */
	public static final String PROVIDER_PROPERTY = "rwandaemr.labNotification.provider";
	public static final String PROVIDER_RBCMESSAGING = "rbcmessaging";
	public static final String PROVIDER_INTOUCH = "intouch";
	public static final String AUTO_SEND_PROPERTY = "rwandaemr.labNotification.autoSend";
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
			"{patientName}. Ibisubizo by'ibizamini byawe byabonetse. Wasubira aho muganga wagusuzumye akorera.";

	private final SmsGatewayConfig smsGatewayConfig;

	public LabNotificationConfig() {
		this(new SmsGatewayConfig());
	}

	public LabNotificationConfig(SmsGatewayConfig smsGatewayConfig) {
		this.smsGatewayConfig = smsGatewayConfig;
	}

	public boolean isEnabled() {
		return getBoolean(ENABLED_PROPERTY, false);
	}

	public String getProvider() {
		return getString(PROVIDER_PROPERTY, PROVIDER_RBCMESSAGING).toLowerCase();
	}

	public boolean isIntouchProvider() {
		return PROVIDER_INTOUCH.equals(getProvider());
	}

	public boolean isAutoSendEnabled() {
		return getBoolean(AUTO_SEND_PROPERTY, false);
	}

	public String getIntouchUrl() {
		return smsGatewayConfig.getIntouchUrl();
	}

	public String getUsername() {
		return smsGatewayConfig.getUsername();
	}

	public String getPassword() {
		return smsGatewayConfig.getPassword();
	}

	public String getSender() {
		return smsGatewayConfig.getSender();
	}

	public String getCoding() {
		return smsGatewayConfig.getCoding();
	}

	public String getDlrUrl() {
		return smsGatewayConfig.getDlrUrl();
	}

	public String getDlrLevel() {
		return smsGatewayConfig.getDlrLevel();
	}

	public String getMessageTemplate() {
		return getString(MESSAGE_TEMPLATE_PROPERTY, DEFAULT_MESSAGE_TEMPLATE);
	}

	/**
	 * Only the Intouch settings are checked here; rbcmessaging checks its own settings when it sends.
	 */
	public boolean hasRequiredSettings() {
		if (!isIntouchProvider()) {
			return true;
		}
		return smsGatewayConfig.hasRequiredSettings();
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
