package org.openmrs.module.rwandaemr.sms;

import org.junit.After;
import org.junit.Test;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmsGatewayConfigTest {

	private final SmsGatewayConfig config = new SmsGatewayConfig();

	@After
	public void tearDown() {
		clear(SmsGatewayConfig.POST_URL_PROPERTY);
		clear(SmsGatewayConfig.USERNAME_PROPERTY);
		clear(SmsGatewayConfig.PASSWORD_PROPERTY);
		clear(SmsGatewayConfig.SENDER_PROPERTY);
		clear(SmsGatewayConfig.CODING_PROPERTY);
		clear(SmsGatewayConfig.DLR_URL_PROPERTY);
		clear(SmsGatewayConfig.DLR_LEVEL_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_URL_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_USERNAME_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_PASSWORD_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_SENDER_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_CODING_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_DLR_URL_PROPERTY);
		clear(LabNotificationConfig.INTOUCH_DLR_LEVEL_PROPERTY);
	}

	@Test
	public void shouldPreferSharedSmsProperties() {
		System.setProperty(SmsGatewayConfig.POST_URL_PROPERTY, "http://sms.example/send");
		System.setProperty(SmsGatewayConfig.USERNAME_PROPERTY, "shared-user");
		System.setProperty(SmsGatewayConfig.PASSWORD_PROPERTY, "shared-password");
		System.setProperty(SmsGatewayConfig.SENDER_PROPERTY, "RwandaEMR");
		System.setProperty(SmsGatewayConfig.CODING_PROPERTY, "8");
		System.setProperty(SmsGatewayConfig.DLR_URL_PROPERTY, "http://sms.example/dlr");
		System.setProperty(SmsGatewayConfig.DLR_LEVEL_PROPERTY, "3");
		System.setProperty(LabNotificationConfig.INTOUCH_USERNAME_PROPERTY, "legacy-user");

		assertEquals("http://sms.example/send", config.getIntouchUrl());
		assertEquals("shared-user", config.getUsername());
		assertEquals("shared-password", config.getPassword());
		assertEquals("RwandaEMR", config.getSender());
		assertEquals("8", config.getCoding());
		assertEquals("http://sms.example/dlr", config.getDlrUrl());
		assertEquals("3", config.getDlrLevel());
		assertTrue(config.hasRequiredSettings());
	}

	@Test
	public void shouldFallBackToLegacyLabNotificationProperties() {
		System.setProperty(LabNotificationConfig.INTOUCH_URL_PROPERTY, "http://legacy.example/send");
		System.setProperty(LabNotificationConfig.INTOUCH_USERNAME_PROPERTY, "legacy-user");
		System.setProperty(LabNotificationConfig.INTOUCH_PASSWORD_PROPERTY, "legacy-password");
		System.setProperty(LabNotificationConfig.INTOUCH_SENDER_PROPERTY, "LegacySender");

		assertEquals("http://legacy.example/send", config.getIntouchUrl());
		assertEquals("legacy-user", config.getUsername());
		assertEquals("legacy-password", config.getPassword());
		assertEquals("LegacySender", config.getSender());
		assertTrue(config.hasRequiredSettings());
	}

	private void clear(String propertyName) {
		System.clearProperty(propertyName);
	}
}
