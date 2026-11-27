/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.htmlformentry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.element.ObsSubmissionElement;
import org.openmrs.module.htmlformentry.element.OrderSubmissionElement;
import org.openmrs.module.htmlformentry.widget.OrderWidgetValue;
import org.openmrs.module.htmlformentry.widget.Widget;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceApprovalCheck;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceApprovalCheck.Procedure;
import org.openmrs.module.rwandaemr.integration.insurance.RhipApprovalRequestService;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * &lt;insuranceApprovalCheck/&gt;: on a form where clinicians choose procedures (medical acts, or orders such as
 * radiology studies), warns the clinician when one needs the patient's insurer's approval, and on save requests that
 * approval for the ones the clinician kept ticked (RHIP Unified Insurance Integration Guide: MMI, section 7). Such a
 * procedure is not billed to the insurer until it approves it. The insurances concerned are those of
 * mohbilling.approvalRequired.insurances.
 * <p>
 * Medical acts are found the way the bill is made from the form (CreateBillOnHtmlFormSubmissionAction): a coded answer
 * through its answer concept, a checkbox or number through its question concept. Orders through their concept. The tag
 * goes at the end of the form, after the fields it watches. It shows nothing for other patients.
 */
public class InsuranceApprovalCheckElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	static final String REQUEST_PARAMETER = "insuranceApprovalRequest";

	static final String NOTE_PARAMETER = "insuranceApprovalNote";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final Log log = LogFactory.getLog(getClass());

	private InsuranceApprovalCheck check;

	/**
	 * Form field name to the procedures needing approval it can bill
	 */
	private final Map<String, List<Map<String, String>>> proceduresByField = new LinkedHashMap<String, List<Map<String, String>>>();

	private final List<OrderSubmissionElement> orderElements = new ArrayList<OrderSubmissionElement>();

	public InsuranceApprovalCheckElement(FormEntrySession session) {
		FormEntryContext.Mode mode = session.getContext().getMode();
		if (mode != FormEntryContext.Mode.ENTER && mode != FormEntryContext.Mode.EDIT) {
			return;
		}
		try {
			check = InsuranceApprovalCheck.forPatient(session.getPatient());
			if (check != null) {
				findFields(session);
			}
		}
		catch (Exception e) {
			log.warn("Unable to check which procedures of this form need insurance approval", e);
			check = null;
		}
	}

	private void findFields(FormEntrySession session) {
		for (FormSubmissionControllerAction action : session.getSubmissionController().getActions()) {
			if (action instanceof OrderSubmissionElement) {
				orderElements.add((OrderSubmissionElement) action);
				continue;
			}
			if (!(action instanceof ObsSubmissionElement)) {
				continue;
			}
			ObsSubmissionElement<?> obs = (ObsSubmissionElement<?>) action;
			String fieldName = fieldName(session.getContext(), obs);
			if (fieldName == null || obs.getConcept() == null) {
				continue;
			}
			List<Map<String, String>> procedures = new ArrayList<Map<String, String>>();
			Set<Concept> answers = new HashSet<Concept>();
			if (obs.getConceptAnswers() != null) {
				answers.addAll(obs.getConceptAnswers());
			}
			if (obs.getAnswerConcept() != null) {
				answers.add(obs.getAnswerConcept());
			}
			for (Concept answer : answers) {
				addProcedure(procedures, check.procedureFor(answer), answer.getConceptId().toString());
			}
			if (answers.isEmpty()) {
				addProcedure(procedures, check.procedureFor(obs.getConcept()), "*");
			}
			if (!procedures.isEmpty()) {
				proceduresByField.put(fieldName, procedures);
			}
		}
	}

	private void addProcedure(List<Map<String, String>> procedures, Procedure procedure, String value) {
		if (procedure != null) {
			procedures.add(describe(procedure, value));
		}
	}

	private Map<String, String> describe(Procedure procedure, String value) {
		Map<String, String> ret = new LinkedHashMap<String, String>();
		if (value != null) {
			ret.put("value", value);
		}
		ret.put("code", procedure.getCode());
		ret.put("name", procedure.getName());
		return ret;
	}

	/**
	 * @return the name of the obs field's input on the page
	 */
	private String fieldName(FormEntryContext context, ObsSubmissionElement<?> obs) {
		try {
			Field field = ObsSubmissionElement.class.getDeclaredField("valueWidget");
			field.setAccessible(true);
			Widget widget = (Widget) field.get(obs);
			return widget == null ? null : context.getFieldNameIfRegistered(widget);
		}
		catch (Exception e) {
			return null;
		}
	}

	private boolean isActive() {
		return check != null && (!proceduresByField.isEmpty() || !orderElements.isEmpty());
	}

	@Override
	public String generateHtml(FormEntryContext context) {
		if (!isActive()) {
			return "";
		}
		Map<String, Map<String, String>> orderConcepts = new LinkedHashMap<String, Map<String, String>>();
		if (!orderElements.isEmpty()) {
			for (Map.Entry<Integer, Procedure> entry : check.getApprovalRequiredConcepts().entrySet()) {
				orderConcepts.put(entry.getKey().toString(), describe(entry.getValue(), null));
			}
		}
		if (proceduresByField.isEmpty() && orderConcepts.isEmpty()) {
			return "";
		}
		String fieldsJson;
		String orderConceptsJson;
		try {
			fieldsJson = OBJECT_MAPPER.writeValueAsString(proceduresByField).replace("</", "<\\/");
			orderConceptsJson = OBJECT_MAPPER.writeValueAsString(orderConcepts).replace("</", "<\\/");
		}
		catch (Exception e) {
			return "";
		}
		boolean canRequest = check.canRequest();
		String insurer = escapeHtml(check.getInsuranceName());
		StringBuilder html = new StringBuilder();
		html.append("<div id=\"insurance-approval-check\" style=\"display:none; margin:12px 0; padding:12px 14px; border:1px solid #e0a800;")
				.append(" border-left:5px solid #e0a800; background:#fff8e1; border-radius:4px;\">")
				.append("<strong>These procedures need ").append(insurer).append("'s approval</strong>")
				.append("<div style=\"margin:4px 0 8px;\">They will not be billed to ").append(insurer)
				.append(" until it approves them.");
		if (canRequest) {
			html.append(" The ticked ones are sent to ").append(insurer).append(" for approval when you save; untick a procedure you will not do.");
		}
		else {
			html.append(" <span style=\"color:#b00020;\">This patient has no MMI reception yet, so approval cannot be requested."
					+ " Register the MMI reception first.</span>");
		}
		html.append("</div><div id=\"insurance-approval-check-list\"></div>");
		if (canRequest) {
			html.append("<div style=\"margin-top:8px;\"><label for=\"insurance-approval-note\">Note for the approver (optional)</label><br/>")
					.append("<textarea id=\"insurance-approval-note\" name=\"").append(NOTE_PARAMETER)
					.append("\" rows=\"2\" style=\"width:100%; max-width:520px;\"></textarea></div>");
		}
		html.append("</div>");
		html.append("<script type=\"text/javascript\">(function() {")
				.append("var fields = ").append(fieldsJson).append(";")
				.append("var orderConcepts = ").append(orderConceptsJson).append(";")
				.append("var canRequest = ").append(canRequest).append(";")
				.append("var unticked = {}; var ordered = {};")
				.append("function isActive(name, value) {")
				.append("  var active = false;")
				.append("  jQuery('[name=\"' + name + '\"]').each(function() {")
				.append("    var el = jQuery(this); var type = (el.attr('type') || '').toLowerCase(); var v = el.val();")
				.append("    if (type === 'checkbox' || type === 'radio') { if (!el.is(':checked')) return; }")
				.append("    if (v === null || v === undefined || v === '' || v === 'false') return;")
				.append("    if (value === '*') { if (type === 'checkbox' || type === 'radio' || isNaN(parseFloat(v)) || parseFloat(v) > 0) active = true; }")
				.append("    else if (jQuery.isArray(v) ? jQuery.inArray(value, v) >= 0 : String(v) === value) { active = true; }")
				.append("  });")
				.append("  return active;")
				.append("}")
				// A study chosen in an order stays listed: on save, only the orders really placed are requested
				.append("function readOrders() {")
				.append("  jQuery('.order-field-widget.order-concept :input').each(function() {")
				.append("    var v = jQuery(this).val(); if (v && orderConcepts[String(v)]) { ordered[orderConcepts[String(v)].code] = orderConcepts[String(v)].name; } });")
				.append("}")
				.append("function refresh() {")
				.append("  readOrders();")
				.append("  var found = {}; var codes = [];")
				.append("  jQuery.each(fields, function(name, procedures) { jQuery.each(procedures, function(i, p) {")
				.append("    if (!found[p.code] && isActive(name, p.value)) { found[p.code] = p.name; codes.push(p.code); } }); });")
				.append("  jQuery.each(ordered, function(code, name) { if (!found[code]) { found[code] = name; codes.push(code); } });")
				.append("  var list = jQuery('#insurance-approval-check-list').empty();")
				.append("  jQuery.each(codes, function(i, code) {")
				.append("    var label = jQuery('<label style=\"display:block;\"/>');")
				.append("    if (canRequest) { var box = jQuery('<input type=\"checkbox\" name=\"").append(REQUEST_PARAMETER).append("\"/>').val(code).prop('checked', !unticked[code]);")
				.append("      box.on('change', function() { unticked[code] = !jQuery(this).is(':checked'); }); label.append(box).append(' '); }")
				.append("    label.append(document.createTextNode((found[code] || code) + ' (' + code + ')')); list.append(label); });")
				.append("  jQuery('#insurance-approval-check').toggle(codes.length > 0);")
				.append("}")
				.append("jQuery(function() {")
				.append("  jQuery.each(fields, function(name) { jQuery(document).on('change keyup', '[name=\"' + name + '\"]', refresh); });")
				.append("  jQuery(document).on('change', '.order-field-widget.order-concept :input', refresh);")
				.append("  jQuery(document).on('click', '.order-action-button, button, input[type=button]', function() { setTimeout(refresh, 0); });")
				.append("  refresh();")
				.append("});")
				.append("})();</script>");
		return html.toString();
	}

	@Override
	public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest submission) {
		return Collections.emptyList();
	}

	@Override
	public void handleSubmission(FormEntrySession session, HttpServletRequest submission) {
		if (!isActive() || !check.canRequest()) {
			return;
		}
		String[] values = submission.getParameterValues(REQUEST_PARAMETER);
		if (values == null || values.length == 0) {
			return;
		}
		Set<String> requestedCodes = new HashSet<String>();
		for (String value : values) {
			if (StringUtils.isNotBlank(value)) {
				requestedCodes.add(value.trim().toUpperCase());
			}
		}
		// The orders placed, read now while the request is at hand; the procedures are requested once the encounter is saved
		List<Concept> orderedConcepts = new ArrayList<Concept>();
		for (OrderSubmissionElement element : orderElements) {
			try {
				for (OrderWidgetValue value : element.getOrderWidget().getValue(session.getContext(), submission)) {
					Order order = value == null ? null : value.getNewOrder();
					if (order != null && order.getConcept() != null && order.getAction() == Order.Action.NEW) {
						orderedConcepts.add(order.getConcept());
					}
				}
			}
			catch (Exception e) {
				log.debug("Unable to read the orders of the form for insurance approval", e);
			}
		}
		String note = StringUtils.trimToNull(submission.getParameter(NOTE_PARAMETER));
		session.getSubmissionActions().addCustomFormSubmissionAction(
				s -> requestApproval(s, requestedCodes, orderedConcepts, note));
	}

	/**
	 * Runs once the encounter is saved. Only procedures the form really records are requested, whatever the page sent.
	 * A failure is recorded on the RHIP approvals page and never stops the form from saving.
	 */
	private void requestApproval(FormEntrySession session, Set<String> requestedCodes, List<Concept> orderedConcepts,
	                             String note) {
		try {
			Map<Procedure, BigDecimal> procedures = InsuranceApprovalCheck.newProcedureMap();
			List<Obs> recorded = session.getSubmissionActions().getObsToCreate();
			for (Obs obs : recorded == null ? Collections.<Obs>emptyList() : recorded) {
				if (obs.getValueCoded() != null) {
					InsuranceApprovalCheck.add(procedures, check.procedureFor(obs.getValueCoded()), BigDecimal.ONE, requestedCodes);
				}
				else if (obs.getValueNumeric() != null && obs.getValueNumeric() > 0) {
					InsuranceApprovalCheck.add(procedures, check.procedureFor(obs.getConcept()),
							BigDecimal.valueOf(obs.getValueNumeric()), requestedCodes);
				}
				else if (Boolean.TRUE.equals(obs.getValueBoolean())) {
					InsuranceApprovalCheck.add(procedures, check.procedureFor(obs.getConcept()), BigDecimal.ONE, requestedCodes);
				}
			}
			for (Concept concept : orderedConcepts) {
				InsuranceApprovalCheck.add(procedures, check.procedureFor(concept), BigDecimal.ONE, requestedCodes);
			}
			if (procedures.isEmpty()) {
				return;
			}
			RhipApprovalRequestService.Result result = check.request(procedures, note, session.getEncounter(), null);
			if (result != null && !result.isSuccess()) {
				log.warn("Insurance approval request from form " + session.getForm().getName() + " failed: " + result.getMessage());
			}
		}
		catch (Exception e) {
			log.error("Unable to request insurance approval from form " + session.getForm().getName(), e);
		}
	}

	private static String escapeHtml(String value) {
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
