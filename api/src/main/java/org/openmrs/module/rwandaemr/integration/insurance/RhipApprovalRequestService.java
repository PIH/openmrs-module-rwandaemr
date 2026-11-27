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
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceEligibilityProvider.ApprovalLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends approval requests to RHIP (RHIP Unified Insurance Integration Guide: MMI, section 7.2).
 * <p>
 * MMI keeps one approval per reception, and a later request for the same reception changes it. So every MMI request
 * carries all the procedures the reception's approval holds: asking for a new procedure adds it to them, and asking
 * again for one already there changes its quantity. When MMI refuses a line nothing is saved on its side, so the lines
 * already held are left as they are.
 */
@Component
public class RhipApprovalRequestService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final InsuranceEligibilityProvider provider;

	private final RhipApprovalRequestLogRepository repository;

	public RhipApprovalRequestService(@Autowired InsuranceEligibilityProvider provider,
	                                  @Autowired RhipApprovalRequestLogRepository repository) {
		this.provider = provider;
		this.repository = repository;
	}

	/**
	 * Requests approval for one procedure.
	 *
	 * @param line the procedure, with the patient, insurance, practitioner, note and, for MMI, the reception filled in
	 */
	public Result submit(RhipApprovalRequestLogEntry line) {
		return submitAll(java.util.Collections.singletonList(line));
	}

	/**
	 * Requests approval for several procedures of one patient in one request, e.g. those ticked on a clinical form.
	 * All the lines share the insurance, reception, practitioner and note of the first one.
	 */
	public Result submitAll(List<RhipApprovalRequestLogEntry> requested) {
		if (requested == null || requested.isEmpty()) {
			return Result.failure("No procedure to request approval for.");
		}
		RhipApprovalRequestLogEntry first = requested.get(0);
		List<Planned> plan = new ArrayList<Planned>();
		if (first.isMmi()) {
			for (RhipApprovalRequestLogEntry held : repository.getActiveByVisitReference(first.getVisitReferenceNumber())) {
				plan.add(new Planned(held, held.getRequestedQuantity(), Kind.HELD));
			}
		}
		for (RhipApprovalRequestLogEntry line : requested) {
			BigDecimal quantity = wholeQuantity(line.getRequestedQuantity());
			line.setRequestedQuantity(quantity);
			Planned same = first.isMmi() ? findByCode(plan, line.getProductCode()) : null;
			if (same == null) {
				plan.add(new Planned(line, quantity, Kind.NEW));
			}
			else if (same.kind == Kind.NEW) {
				// The same procedure twice in one request: ask for the larger quantity
				same.quantity = same.quantity.max(quantity);
			}
			else {
				// Asking again for a procedure already on the approval changes its quantity
				same.quantity = quantity;
				same.kind = Kind.EDIT;
			}
		}
		return send(first, plan);
	}

	/**
	 * Sends a request again: a failed line, or for MMI the whole approval of the line's reception
	 */
	public Result resend(Integer approvalRequestId) {
		RhipApprovalRequestLogEntry entry = repository.getById(approvalRequestId);
		if (entry == null) {
			return Result.failure("Approval request was not found.");
		}
		boolean failed = RhipApprovalRequestLogEntry.STATUS_FAILED.equalsIgnoreCase(StringUtils.trimToEmpty(entry.getApprovalStatus()));
		List<Planned> plan = new ArrayList<Planned>();
		if (!entry.isMmi()) {
			if (!failed) {
				return Result.failure("Only failed approval requests can be sent again.");
			}
			plan.add(new Planned(entry, entry.getRequestedQuantity(), Kind.RETRY));
			return send(entry, plan);
		}
		if (StringUtils.isBlank(entry.getVisitReferenceNumber())) {
			entry.setVisitReferenceNumber(visitReferenceOf(entry));
		}
		if (StringUtils.isBlank(entry.getVisitReferenceNumber())) {
			return Result.failure("This approval request has no MMI reception number.");
		}
		for (RhipApprovalRequestLogEntry held : repository.getActiveByVisitReference(entry.getVisitReferenceNumber())) {
			Kind kind = held.getId() != null && held.getId().equals(entry.getId()) ? Kind.RETRY : Kind.HELD;
			plan.add(new Planned(held, held.getRequestedQuantity(), kind));
		}
		if (findById(plan, entry.getId()) == null) {
			if (findByCode(plan, entry.getProductCode()) != null) {
				return Result.failure(entry.getProductCode() + " is already on this reception's MMI approval.");
			}
			plan.add(new Planned(entry, entry.getRequestedQuantity(), Kind.RETRY));
		}
		return send(entry, plan);
	}

	/**
	 * @param context the line giving the insurance, reception, practitioner and note of the request
	 * @param plan every line to send. New lines are saved whatever RHIP answers (as failed when it refuses), a retried
	 * line is updated, and lines MMI already holds are only changed when RHIP accepts the request, since nothing is
	 * saved on MMI's side when it refuses one.
	 */
	private Result send(RhipApprovalRequestLogEntry context, List<Planned> plan) {
		List<ApprovalLine> approvalLines = new ArrayList<ApprovalLine>();
		Set<String> diagnosisIds = new LinkedHashSet<String>();
		boolean edited = false;
		for (Planned planned : plan) {
			approvalLines.add(new ApprovalLine(planned.entry.getProductCode(), planned.quantity));
			diagnosisIds.addAll(splitDiagnosisIds(planned.entry.getDiagnosisIds()));
			edited = edited || planned.kind == Kind.EDIT;
		}
		Map<String, Object> payload = provider.buildApprovalRequestPayload(context.getInsuranceType(),
				context.getFacilityFosaId(), context.getPatientIdentifier(), context.getVisitReferenceNumber(),
				context.getPractitionerLicenseNumber(), new ArrayList<String>(diagnosisIds),
				context.getClinicalKnowledge(), approvalLines);
		IntegrationResponse response = provider.requestApproval(payload);
		JsonNode root = response == null || !(response.getResponseEntity() instanceof JsonNode)
				? null : (JsonNode) response.getResponseEntity();
		boolean success = isSuccess(root, response);
		String approvalCode = RhipApprovalDecision.text(root == null ? null : root.get("data"), "approvalCode");
		String message = RhipApprovalDecision.describeRequestAnswer(root, response == null ? null : response.getErrorMessage());
		String requestPayload = toJson(payload);
		String responsePayload = toJson(root);
		Integer responseCode = response == null ? null : response.getResponseCode();
		Integer userId = currentUserId();

		if (!success) {
			String failure = StringUtils.defaultIfBlank(message, "RHIP approval request failed.");
			for (Planned planned : plan) {
				RhipApprovalRequestLogEntry entry = planned.entry;
				if (planned.kind == Kind.NEW) {
					entry.setApprovalStatus(RhipApprovalRequestLogEntry.STATUS_FAILED);
					entry.setMessage(failure);
					entry.setRequestPayload(requestPayload);
					entry.setResponsePayload(responsePayload);
					entry.setResponseCode(responseCode);
					repository.save(entry);
				}
				else if (planned.kind == Kind.RETRY && isFailed(entry)) {
					repository.updateRequestResult(entry.getId(), RhipApprovalRequestLogEntry.STATUS_FAILED,
							entry.getApprovalCode(), entry.getVisitReferenceNumber(), entry.getRequestedQuantity(), failure,
							requestPayload, responsePayload, responseCode, userId);
				}
			}
			return Result.failure(failure);
		}

		for (Planned planned : plan) {
			RhipApprovalRequestLogEntry entry = planned.entry;
			if (planned.kind == Kind.NEW) {
				entry.setApprovalStatus(RhipApprovalRequestLogEntry.STATUS_PENDING);
				entry.setApprovalCode(approvalCode);
				entry.setRequestedQuantity(planned.quantity);
				entry.setMessage(message);
				entry.setRequestPayload(requestPayload);
				entry.setResponsePayload(responsePayload);
				entry.setResponseCode(responseCode);
				repository.save(entry);
				continue;
			}
			boolean changed = planned.kind != Kind.HELD || isFailed(entry);
			String status = changed ? RhipApprovalRequestLogEntry.STATUS_PENDING
					: StringUtils.defaultIfBlank(RhipApprovalDecision.normalizeStatus(entry.getApprovalStatus()),
					RhipApprovalRequestLogEntry.STATUS_PENDING);
			repository.updateRequestResult(entry.getId(), status,
					StringUtils.defaultIfBlank(approvalCode, entry.getApprovalCode()),
					StringUtils.defaultIfBlank(entry.getVisitReferenceNumber(), context.getVisitReferenceNumber()),
					planned.quantity, message, requestPayload, responsePayload, responseCode, userId);
		}
		return Result.success(approvalCode, message, edited);
	}

	private boolean isFailed(RhipApprovalRequestLogEntry entry) {
		return RhipApprovalRequestLogEntry.STATUS_FAILED.equalsIgnoreCase(StringUtils.trimToEmpty(entry.getApprovalStatus()));
	}

	private enum Kind {
		/** A procedure not yet on the approval */
		NEW,
		/** A procedure on the approval whose quantity changes */
		EDIT,
		/** A procedure on the approval, sent again unchanged so MMI keeps it */
		HELD,
		/** A line sent again from the approvals page */
		RETRY
	}

	private static class Planned {

		private final RhipApprovalRequestLogEntry entry;

		private BigDecimal quantity;

		private Kind kind;

		private Planned(RhipApprovalRequestLogEntry entry, BigDecimal quantity, Kind kind) {
			this.entry = entry;
			this.quantity = quantity;
			this.kind = kind;
		}
	}

	/**
	 * @return the MMI reception of a line, also for lines saved before the reception had its own column
	 */
	static String visitReferenceOf(RhipApprovalRequestLogEntry entry) {
		if (entry == null) {
			return null;
		}
		if (StringUtils.isNotBlank(entry.getVisitReferenceNumber())) {
			return entry.getVisitReferenceNumber().trim();
		}
		try {
			JsonNode payload = StringUtils.isBlank(entry.getRequestPayload()) ? null
					: OBJECT_MAPPER.readTree(entry.getRequestPayload());
			return StringUtils.defaultIfBlank(RhipApprovalDecision.text(payload, "visitReferenceNumber"),
					RhipApprovalDecision.text(payload, "receptionNumber"));
		}
		catch (Exception e) {
			return null;
		}
	}

	static BigDecimal wholeQuantity(BigDecimal quantity) {
		return new BigDecimal(ApprovalLine.toWholeQuantity(quantity));
	}

	static List<String> splitDiagnosisIds(String diagnosisIds) {
		List<String> ret = new ArrayList<String>();
		if (StringUtils.isBlank(diagnosisIds)) {
			return ret;
		}
		for (String value : diagnosisIds.split("[,;\\n\\r]+")) {
			if (StringUtils.isNotBlank(value)) {
				ret.add(value.trim());
			}
		}
		return ret;
	}

	private Planned findByCode(List<Planned> plan, String code) {
		for (Planned planned : plan) {
			String lineCode = planned.entry.getProductCode();
			if (lineCode != null && code != null && lineCode.trim().equalsIgnoreCase(code.trim())) {
				return planned;
			}
		}
		return null;
	}

	private Planned findById(List<Planned> plan, Integer id) {
		for (Planned planned : plan) {
			if (planned.entry.getId() != null && planned.entry.getId().equals(id)) {
				return planned;
			}
		}
		return null;
	}

	private boolean isSuccess(JsonNode root, IntegrationResponse response) {
		if (root != null && root.has("success")) {
			return root.get("success").asBoolean(false);
		}
		Integer code = response == null ? null : response.getResponseCode();
		return code != null && code >= 200 && code < 300 && StringUtils.isBlank(response.getErrorMessage());
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

	private String toJson(Object value) {
		try {
			return value == null ? null : OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	public static class Result {

		private final boolean success;

		private final String approvalCode;

		private final String message;

		private final boolean edited;

		private Result(boolean success, String approvalCode, String message, boolean edited) {
			this.success = success;
			this.approvalCode = approvalCode;
			this.message = message;
			this.edited = edited;
		}

		static Result success(String approvalCode, String message, boolean edited) {
			return new Result(true, approvalCode, message, edited);
		}

		static Result failure(String message) {
			return new Result(false, null, message, false);
		}

		public boolean isSuccess() {
			return success;
		}

		public String getApprovalCode() {
			return approvalCode;
		}

		public String getMessage() {
			return message;
		}

		/**
		 * @return true when the request changed the quantity of a procedure already on the approval
		 */
		public boolean isEdited() {
			return edited;
		}
	}
}
