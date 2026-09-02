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
import java.util.List;

@Component
public class RhipApprovalStatusService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	public static final int MAX_AUTOMATIC_STATUS_CHECKS = 12;

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

	public ApprovalStatusUpdate updateApprovalStatus(RhipApprovalRequestLogEntry entry, boolean automaticCheck) {
		String oldStatus = normalizeApprovalStatus(entry == null ? null : entry.getApprovalStatus());
		IntegrationResponse response = provider.checkApprovalStatus(entry.getInsuranceType(), entry.getApprovalCode(),
				entry.getFacilityFosaId(), resolveApprovalStatusPeriod(entry));
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		String status = resolveApprovalStatus(entry, root, response);
		String message = defaultIfBlank(text(root, "message"), response == null ? null : response.getErrorMessage());
		User currentUser = Context.getAuthenticatedUser();
		repository.updateStatus(entry.getId(), status, entry.getApprovalCode(), message, toJson(root),
				response == null ? null : response.getResponseCode(), currentUser == null ? null : currentUser.getUserId(),
				automaticCheck);
		return new ApprovalStatusUpdate(oldStatus, status, message);
	}

	public List<RhipApprovalRequestLogEntry> getPendingApprovalRequests(int limit) {
		return repository.getPendingWithApprovalCode(limit, MAX_AUTOMATIC_STATUS_CHECKS);
	}

	public int updatePendingApprovalStatuses(int limit) {
		int updated = 0;
		for (RhipApprovalRequestLogEntry entry : getPendingApprovalRequests(limit)) {
			updateApprovalStatus(entry, true);
			updated++;
		}
		return updated;
	}

	private String resolveApprovalStatusPeriod(RhipApprovalRequestLogEntry entry) {
		JsonNode requestPayload = parseJson(entry == null ? null : entry.getRequestPayload());
		String configuredPeriod = text(requestPayload, "period");
		return StringUtils.isBlank(configuredPeriod) ? null : configuredPeriod;
	}

	private String resolveApprovalStatus(RhipApprovalRequestLogEntry entry, JsonNode root, IntegrationResponse response) {
		String currentStatus = normalizeApprovalStatus(entry == null ? null : entry.getApprovalStatus());
		JsonNode data = root == null ? null : root.path("data");
		String status = normalizeApprovalStatus(text(data, "status"));
		if (StringUtils.isNotBlank(status)) {
			return status;
		}
		if (response == null || !response.isEnabled() || !response.isEndpointAccessible()) {
			return StringUtils.defaultIfBlank(currentStatus, RhipApprovalRequestLogEntry.STATUS_PENDING);
		}
		if (!isSuccess(root, response)) {
			return RhipApprovalRequestLogEntry.STATUS_FAILED;
		}
		if (isMmi(entry == null ? null : entry.getInsuranceType()) && hasPositiveAmount(data, "totalApprovalAmount")) {
			return RhipApprovalRequestLogEntry.STATUS_APPROVED;
		}
		return StringUtils.isBlank(currentStatus) ? RhipApprovalRequestLogEntry.STATUS_PENDING : currentStatus;
	}

	private boolean hasPositiveAmount(JsonNode node, String name) {
		if (node == null || node.isMissingNode() || node.path(name).isMissingNode() || node.path(name).isNull()) {
			return false;
		}
		try {
			return node.path(name).decimalValue().compareTo(BigDecimal.ZERO) > 0;
		}
		catch (Exception e) {
			return false;
		}
	}

	private boolean isSuccess(JsonNode root, IntegrationResponse response) {
		if (root != null && root.has("success")) {
			return root.path("success").asBoolean(false);
		}
		Integer code = response == null ? null : response.getResponseCode();
		return code != null && code >= 200 && code < 300 && StringUtils.isBlank(response.getErrorMessage());
	}

	private boolean isMmi(String insuranceType) {
		return "mmi".equalsIgnoreCase(StringUtils.trimToEmpty(insuranceType));
	}

	public String normalizeApprovalStatus(String status) {
		if (StringUtils.isBlank(status)) {
			return status;
		}
		String normalized = status.trim().toUpperCase();
		if ("CREATED".equals(normalized) || "EDITED".equals(normalized) || "REQUESTED".equals(normalized)) {
			return RhipApprovalRequestLogEntry.STATUS_PENDING;
		}
		return normalized;
	}

	public String getApprovalStatusLabel(String status) {
		String normalized = normalizeApprovalStatus(status);
		if (RhipApprovalRequestLogEntry.STATUS_PENDING.equals(normalized)) {
			return "Requested/Pending";
		}
		return StringUtils.defaultString(normalized);
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

	private String text(JsonNode node, String name) {
		if (node == null || node.isMissingNode() || node.path(name).isMissingNode() || node.path(name).isNull()) {
			return null;
		}
		return node.path(name).asText();
	}

	private String defaultIfBlank(String value, String defaultValue) {
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
