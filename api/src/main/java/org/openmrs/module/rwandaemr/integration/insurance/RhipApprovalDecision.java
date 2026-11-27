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
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads RHIP's approval answers (RHIP Unified Insurance Integration Guide: MMI, section 7). MMI gives its statuses in its
 * own words and decides each procedure line on its own, possibly for a lower quantity or price than requested.
 */
public final class RhipApprovalDecision {

	private RhipApprovalDecision() {
	}

	/**
	 * @return one of the statuses of {@link RhipApprovalRequestLogEntry}, or null when the status is blank. Anything not
	 * recognised as a decision is treated as still pending.
	 */
	public static String normalizeStatus(String status) {
		if (StringUtils.isBlank(status)) {
			return null;
		}
		String normalized = status.trim().toUpperCase().replace(' ', '_').replace('-', '_');
		if ("APPROVED".equals(normalized) || "ACCEPTED".equals(normalized) || "APPROVE".equals(normalized)
				|| "ACCEPT".equals(normalized) || "AUTHORIZED".equals(normalized) || "AUTHORISED".equals(normalized)) {
			return RhipApprovalRequestLogEntry.STATUS_APPROVED;
		}
		if (normalized.startsWith("PARTIAL")) {
			return RhipApprovalRequestLogEntry.STATUS_PARTIALLY_APPROVED;
		}
		if ("REJECTED".equals(normalized) || "REJECT".equals(normalized) || "DENIED".equals(normalized)
				|| "DECLINED".equals(normalized) || "REFUSED".equals(normalized) || "CANCELLED".equals(normalized)
				|| "CANCELED".equals(normalized)) {
			return RhipApprovalRequestLogEntry.STATUS_REJECTED;
		}
		if ("FAILED".equals(normalized)) {
			return RhipApprovalRequestLogEntry.STATUS_FAILED;
		}
		return RhipApprovalRequestLogEntry.STATUS_PENDING;
	}

	/**
	 * @param lineStatus MMI's decision for the line
	 * @param overallStatus MMI's status for the whole approval, used when the line has none
	 * @param requestedQuantity what was asked for
	 * @param approvedQuantity what MMI approved
	 * @return the line's status. An approval for less than requested is a partial approval, and one for nothing a
	 * rejection.
	 */
	public static String lineStatus(String lineStatus, String overallStatus, BigDecimal requestedQuantity,
	                                BigDecimal approvedQuantity) {
		String status = normalizeStatus(lineStatus);
		if (status == null) {
			status = normalizeStatus(overallStatus);
		}
		if (status == null) {
			return RhipApprovalRequestLogEntry.STATUS_PENDING;
		}
		boolean approved = RhipApprovalRequestLogEntry.STATUS_APPROVED.equals(status)
				|| RhipApprovalRequestLogEntry.STATUS_PARTIALLY_APPROVED.equals(status);
		if (approved && approvedQuantity != null) {
			if (approvedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
				return RhipApprovalRequestLogEntry.STATUS_REJECTED;
			}
			if (requestedQuantity != null && approvedQuantity.compareTo(requestedQuantity) < 0) {
				return RhipApprovalRequestLogEntry.STATUS_PARTIALLY_APPROVED;
			}
		}
		return status;
	}

	/**
	 * @return the status label shown to users
	 */
	public static String label(String status) {
		String normalized = normalizeStatus(status);
		if (normalized == null || RhipApprovalRequestLogEntry.STATUS_PENDING.equals(normalized)) {
			return "Requested/Pending";
		}
		if (RhipApprovalRequestLogEntry.STATUS_PARTIALLY_APPROVED.equals(normalized)) {
			return "Partially approved";
		}
		if (RhipApprovalRequestLogEntry.STATUS_APPROVED.equals(normalized)) {
			return "Approved";
		}
		if (RhipApprovalRequestLogEntry.STATUS_REJECTED.equals(normalized)) {
			return "Rejected";
		}
		return "Failed";
	}

	/**
	 * @return true once MMI has decided, so the status no longer needs checking
	 */
	public static boolean isDecided(String status) {
		String normalized = normalizeStatus(status);
		return RhipApprovalRequestLogEntry.STATUS_APPROVED.equals(normalized)
				|| RhipApprovalRequestLogEntry.STATUS_PARTIALLY_APPROVED.equals(normalized)
				|| RhipApprovalRequestLogEntry.STATUS_REJECTED.equals(normalized);
	}

	/**
	 * @return RHIP's message followed by the reason for each line it refused, e.g. "Some procedures could not be
	 * requested; see details. RHIC-OPTH-072: Requested unit price 300,000 exceeds the maximum of 250,000"
	 */
	public static String describeRequestAnswer(JsonNode root, String fallback) {
		String message = text(root, "message");
		List<String> lineErrors = new ArrayList<String>();
		JsonNode errors = root == null ? null : root.get("errors");
		if (errors != null && errors.isArray()) {
			for (JsonNode error : errors) {
				String code = StringUtils.defaultIfBlank(text(error, "productCode"), text(error, "code"));
				String reason = StringUtils.defaultIfBlank(text(error, "reason"), text(error, "message"));
				if (StringUtils.isNotBlank(reason)) {
					lineErrors.add(StringUtils.isBlank(code) ? reason : code + ": " + reason);
				}
			}
		}
		StringBuilder ret = new StringBuilder(StringUtils.defaultIfBlank(message, StringUtils.defaultString(fallback)));
		if (!lineErrors.isEmpty()) {
			if (ret.length() > 0) {
				ret.append(' ');
			}
			ret.append(StringUtils.join(lineErrors, "; "));
		}
		return StringUtils.trimToNull(ret.toString());
	}

	static String text(JsonNode node, String name) {
		if (node == null || node.isMissingNode() || node.get(name) == null || node.get(name).isNull()) {
			return null;
		}
		return StringUtils.trimToNull(node.get(name).asText());
	}

	static BigDecimal decimal(JsonNode node, String name) {
		if (node == null || node.get(name) == null || node.get(name).isNull()) {
			return null;
		}
		try {
			return new BigDecimal(node.get(name).asText().trim());
		}
		catch (Exception e) {
			return null;
		}
	}

	static Boolean bool(JsonNode node, String name) {
		if (node == null || node.get(name) == null || node.get(name).isNull()) {
			return null;
		}
		return node.get(name).asBoolean();
	}
}
