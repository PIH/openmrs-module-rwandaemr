/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Follows MMI's decisions on approval requests (RHIP Unified Insurance Integration Guide: MMI, section 7.3). One
 * /approval/status call covers an approval, and MMI's decision is recorded on each of its lines.
 * <p>
 * A decision depends on a person at MMI, so approvals are checked for {@link #STATUS_CHECK_HOURS} hours after they
 * were last requested: every 5 minutes during the first hour, then every 30 minutes. When RHIP answers 429 (too many
 * calls), checks stop for the time it asks.
 */
@Component
public class RhipApprovalStatusService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public static final int STATUS_CHECK_HOURS = 72;

	static final long FIRST_HOUR_INTERVAL_MILLIS = 5L * 60 * 1000;

	static final long LATER_INTERVAL_MILLIS = 30L * 60 * 1000;

	private static final long DEFAULT_PAUSE_MILLIS = 10L * 60 * 1000;

	private static volatile long pausedUntil = 0;

	private final InsuranceEligibilityProvider provider;

	private final RhipApprovalRequestLogRepository repository;

	public RhipApprovalStatusService(@Autowired InsuranceEligibilityProvider provider,
	                                 @Autowired RhipApprovalRequestLogRepository repository) {
		this.provider = provider;
		this.repository = repository;
	}

	public ApprovalStatusUpdate updateApprovalStatus(RhipApprovalRequestLogEntry entry) {
		return updateApprovalStatus(entry, false);
	}

	/**
	 * Asks RHIP for MMI's decision on the entry's approval and records it on every line of that approval
	 */
	public ApprovalStatusUpdate updateApprovalStatus(RhipApprovalRequestLogEntry entry, boolean automaticCheck) {
		String oldStatus = normalizeApprovalStatus(entry == null ? null : entry.getApprovalStatus());
		if (entry == null || StringUtils.isBlank(entry.getApprovalCode())) {
			return new ApprovalStatusUpdate(oldStatus, oldStatus, "This request has no approval code yet.");
		}
		if (isPaused()) {
			return new ApprovalStatusUpdate(oldStatus, oldStatus, "RHIP asked to slow down status checks; try again after "
					+ new java.text.SimpleDateFormat("HH:mm").format(new Date(pausedUntil)) + ".");
		}
		IntegrationResponse response = provider.checkApprovalStatus(entry.getInsuranceType(), entry.getApprovalCode(),
				entry.getFacilityFosaId(), resolveApprovalStatusPeriod(entry));
		Integer responseCode = response == null ? null : response.getResponseCode();
		if (responseCode != null && responseCode == 429) {
			pause(response.getRetryAfter());
		}
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		JsonNode data = root == null ? null : root.get("data");
		String message = defaultIfBlank(text(root, "message"), response == null ? null : response.getErrorMessage());
		List<RhipApprovalRequestLogEntry> lines = repository.getByApprovalCode(entry.getApprovalCode());
		if (lines.isEmpty()) {
			lines = new ArrayList<RhipApprovalRequestLogEntry>();
			lines.add(entry);
		}
		Integer userId = currentUserId();

		if (!isSuccess(root, response) || data == null || data.isNull()) {
			boolean unknownToMmi = responseCode != null && responseCode == 404;
			for (RhipApprovalRequestLogEntry line : lines) {
				if (unknownToMmi) {
					repository.updateDecision(line.getId(), RhipApprovalRequestLogEntry.STATUS_FAILED,
							line.getApprovedQuantity(), line.getApprovedUnitPrice(), line.getDecisionReason(), line.getBilled(),
							line.getDecidedBy(), line.getDecidedAt(), message, toJson(root), responseCode, userId, automaticCheck);
				}
				else {
					repository.updateStatusCheckFailure(line.getId(), message, responseCode, automaticCheck);
				}
			}
			String newStatus = unknownToMmi ? RhipApprovalRequestLogEntry.STATUS_FAILED : oldStatus;
			return new ApprovalStatusUpdate(oldStatus, newStatus, message);
		}

		Map<String, JsonNode> decisionsByCode = decisionsByCode(data);
		String overallStatus = text(data, "status");
		String newStatus = oldStatus;
		for (RhipApprovalRequestLogEntry line : lines) {
			JsonNode decision = line.getProductCode() == null ? null
					: decisionsByCode.get(line.getProductCode().trim().toUpperCase());
			if (decision == null && !decisionsByCode.isEmpty()) {
				// MMI's approval does not hold this line (e.g. a line that failed and was never saved by MMI)
				continue;
			}
			BigDecimal approvedQuantity = RhipApprovalDecision.decimal(decision, "approvedQuantity");
			String status = RhipApprovalDecision.lineStatus(text(decision, "status"), overallStatus,
					line.getRequestedQuantity(), approvedQuantity);
			repository.updateDecision(line.getId(), status, approvedQuantity,
					RhipApprovalDecision.decimal(decision, "approvedUnitPrice"), text(decision, "reason"),
					RhipApprovalDecision.bool(decision, "billed"), text(data, "decidedBy"), text(data, "decidedAt"),
					message, toJson(root), responseCode, userId, automaticCheck);
			if (line.getId() != null && line.getId().equals(entry.getId())) {
				newStatus = status;
			}
		}
		return new ApprovalStatusUpdate(oldStatus, newStatus, message);
	}

	/**
	 * @return MMI's decisions in the status answer, by upper-case procedure code
	 */
	static Map<String, JsonNode> decisionsByCode(JsonNode data) {
		Map<String, JsonNode> ret = new HashMap<String, JsonNode>();
		JsonNode procedures = data == null ? null : data.get("procedures");
		if (procedures != null && procedures.isArray()) {
			for (JsonNode procedure : procedures) {
				String code = defaultIfBlank(text(procedure, "code"), text(procedure, "rhicCode"));
				if (StringUtils.isNotBlank(code)) {
					ret.put(code.trim().toUpperCase(), procedure);
				}
			}
		}
		return ret;
	}

	/**
	 * Checks the approvals still waiting for a decision that are due for a check
	 *
	 * @return how many approvals were checked
	 */
	public int updatePendingApprovalStatuses(int limit) {
		if (isPaused()) {
			return 0;
		}
		long now = System.currentTimeMillis();
		Date since = new Date(now - STATUS_CHECK_HOURS * 60L * 60 * 1000);
		int checked = 0;
		for (RhipApprovalRequestLogEntry entry : repository.getPendingApprovalsRequestedSince(since, 500)) {
			if (!isDue(entry, now)) {
				continue;
			}
			updateApprovalStatus(entry, true);
			checked++;
			if (isPaused() || (limit > 0 && checked >= limit)) {
				break;
			}
		}
		return checked;
	}

	/**
	 * @return true when the approval was never checked, or its last check is older than the interval for its age
	 */
	static boolean isDue(RhipApprovalRequestLogEntry entry, long now) {
		if (entry.getLastStatusCheck() == null) {
			return true;
		}
		Date requested = entry.getDateChanged() != null ? entry.getDateChanged() : entry.getDateCreated();
		long age = requested == null ? Long.MAX_VALUE : now - requested.getTime();
		long interval = age < 60L * 60 * 1000 ? FIRST_HOUR_INTERVAL_MILLIS : LATER_INTERVAL_MILLIS;
		// A little slack so a check is not skipped because the task ran a few seconds early
		return now - entry.getLastStatusCheck().getTime() >= interval - 30000;
	}

	static void pause(String retryAfter) {
		long millis = DEFAULT_PAUSE_MILLIS;
		try {
			if (StringUtils.isNotBlank(retryAfter)) {
				millis = Long.parseLong(retryAfter.trim()) * 1000;
			}
		}
		catch (NumberFormatException ignored) {
		}
		pausedUntil = System.currentTimeMillis() + Math.max(millis, 1000);
	}

	static boolean isPaused() {
		return System.currentTimeMillis() < pausedUntil;
	}

	static void resetPause() {
		pausedUntil = 0;
	}

	private String resolveApprovalStatusPeriod(RhipApprovalRequestLogEntry entry) {
		JsonNode requestPayload = parseJson(entry == null ? null : entry.getRequestPayload());
		String configuredPeriod = text(requestPayload, "period");
		return StringUtils.isBlank(configuredPeriod) ? null : configuredPeriod;
	}

	private boolean isSuccess(JsonNode root, IntegrationResponse response) {
		if (root != null && root.has("success")) {
			return root.path("success").asBoolean(false);
		}
		Integer code = response == null ? null : response.getResponseCode();
		return code != null && code >= 200 && code < 300 && StringUtils.isBlank(response.getErrorMessage());
	}

	public String normalizeApprovalStatus(String status) {
		String normalized = RhipApprovalDecision.normalizeStatus(status);
		return normalized == null ? status : normalized;
	}

	public String getApprovalStatusLabel(String status) {
		return RhipApprovalDecision.label(status);
	}

	private Integer currentUserId() {
		try {
			User user = Context.getAuthenticatedUser();
			return user == null ? null : user.getUserId();
		}
		catch (Exception e) {
			return null;
		}
	}

	private JsonNode parseJson(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readTree(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	private JsonNode asJsonNode(Object value) {
		if (value instanceof JsonNode) {
			return (JsonNode) value;
		}
		return null;
	}

	private static String text(JsonNode node, String name) {
		return RhipApprovalDecision.text(node, name);
	}

	private static String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.isBlank(value) ? defaultValue : value;
	}

	private String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	public static class ApprovalStatusUpdate {
		private final String oldStatus;
		private final String newStatus;
		private final String message;

		public ApprovalStatusUpdate(String oldStatus, String newStatus, String message) {
			this.oldStatus = StringUtils.defaultIfBlank(oldStatus, RhipApprovalRequestLogEntry.STATUS_PENDING);
			this.newStatus = StringUtils.defaultIfBlank(newStatus, RhipApprovalRequestLogEntry.STATUS_PENDING);
			this.message = message;
		}

		public String getOldStatus() {
			return oldStatus;
		}

		public String getNewStatus() {
			return newStatus;
		}

		public String getMessage() {
			return message;
		}

		public boolean isChanged() {
			return !StringUtils.equalsIgnoreCase(oldStatus, newStatus);
		}
	}
}
