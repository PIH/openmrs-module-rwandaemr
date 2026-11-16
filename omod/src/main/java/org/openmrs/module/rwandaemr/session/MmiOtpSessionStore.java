package org.openmrs.module.rwandaemr.session;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.context.Context;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class MmiOtpSessionStore {

	private static final String SESSION_ATTRIBUTE = "rwandaemr.mmiOtpCodes";
	private static final String GP_OTP_REUSE_MINUTES = "rwandaemr.insuranceReception.mmiOtpReuseMinutes";
	private static final int DEFAULT_OTP_REUSE_MINUTES = 10;

	private MmiOtpSessionStore() {
	}

	public static void rememberOtp(HttpServletRequest request, Integer patientId, String identifier, String otpCode) {
		if (request == null || patientId == null || StringUtils.isBlank(otpCode)) {
			return;
		}
		HttpSession session = request.getSession();
		Map<String, OtpEntry> entries = getEntries(session, true);
		removeExpired(entries);
		entries.put(key(patientId, identifier), new OtpEntry(StringUtils.trimToNull(otpCode), System.currentTimeMillis()));
		entries.put(patientKey(patientId), new OtpEntry(StringUtils.trimToNull(otpCode), System.currentTimeMillis()));
	}

	public static String getOtp(HttpServletRequest request, Integer patientId, String identifier) {
		if (request == null || patientId == null) {
			return null;
		}
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Map<String, OtpEntry> entries = getEntries(session, false);
		if (entries == null) {
			return null;
		}
		removeExpired(entries);
		String exactKey = key(patientId, identifier);
		OtpEntry entry = entries.get(exactKey);
		if (entry == null) {
			entry = entries.get(patientKey(patientId));
		}
		return entry == null ? null : entry.getOtpCode();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, OtpEntry> getEntries(HttpSession session, boolean create) {
		Object existing = session.getAttribute(SESSION_ATTRIBUTE);
		if (existing instanceof Map) {
			return (Map<String, OtpEntry>) existing;
		}
		if (!create) {
			return null;
		}
		Map<String, OtpEntry> entries = new HashMap<>();
		session.setAttribute(SESSION_ATTRIBUTE, entries);
		return entries;
	}

	private static void removeExpired(Map<String, OtpEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			return;
		}
		long maxAgeMillis = resolveMaxAgeMillis();
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<String, OtpEntry>> iterator = entries.entrySet().iterator(); iterator.hasNext();) {
			Map.Entry<String, OtpEntry> entry = iterator.next();
			if (entry.getValue() == null || now - entry.getValue().getCreatedAtMillis() > maxAgeMillis) {
				iterator.remove();
			}
		}
	}

	private static long resolveMaxAgeMillis() {
		String configured = null;
		try {
			configured = Context.getAdministrationService().getGlobalProperty(GP_OTP_REUSE_MINUTES);
		}
		catch (Exception ignored) {
		}
		int minutes = DEFAULT_OTP_REUSE_MINUTES;
		if (StringUtils.isNotBlank(configured)) {
			try {
				minutes = Integer.parseInt(configured.trim());
			}
			catch (NumberFormatException ignored) {
			}
		}
		return Math.max(1, minutes) * 60L * 1000L;
	}

	private static String key(Integer patientId, String identifier) {
		return patientKey(patientId) + ":" + StringUtils.defaultString(identifier).trim().toLowerCase();
	}

	private static String patientKey(Integer patientId) {
		return "patient:" + patientId;
	}

	private static final class OtpEntry implements Serializable {

		private static final long serialVersionUID = 1L;

		private final String otpCode;
		private final long createdAtMillis;

		private OtpEntry(String otpCode, long createdAtMillis) {
			this.otpCode = otpCode;
			this.createdAtMillis = createdAtMillis;
		}

		private String getOtpCode() {
			return otpCode;
		}

		private long getCreatedAtMillis() {
			return createdAtMillis;
		}
	}
}
