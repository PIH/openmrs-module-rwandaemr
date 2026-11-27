/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceApprovalCheck;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceApprovalCheck.Procedure;
import org.openmrs.module.rwandaemr.integration.insurance.RhipApprovalRequestService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Insurance approval check for order pages that save through REST, such as the pihapps lab order page (RHIP Unified Insurance
 * Integration Guide: MMI, section 7): which orderable concepts need the patient's insurer's approval, and the approval
 * request for the orders placed.
 */
@Controller
public class InsuranceApprovalCheckRestController {

	private final Log log = LogFactory.getLog(getClass());

	/**
	 * @return whether the patient's insurance needs approvals (and its name), whether approval can be requested (an
	 * MMI reception is open), and the concepts needing approval by uuid, with their code and name
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/insuranceApproval/procedures", method = RequestMethod.GET)
	@ResponseBody
	public SimpleObject getProcedures(@RequestParam("patient") String patientUuid) {
		SimpleObject ret = new SimpleObject();
		Patient patient = Context.getPatientService().getPatientByUuid(patientUuid);
		InsuranceApprovalCheck check = patient == null ? null : InsuranceApprovalCheck.forPatient(patient);
		ret.add("checked", check != null);
		ret.add("insuranceName", check == null ? null : check.getInsuranceName());
		ret.add("canRequest", check != null && check.canRequest());
		Map<String, Map<String, String>> procedures = new LinkedHashMap<String, Map<String, String>>();
		if (check != null) {
			for (Map.Entry<Integer, Procedure> entry : check.getApprovalRequiredConcepts().entrySet()) {
				Concept concept = Context.getConceptService().getConcept(entry.getKey());
				if (concept != null) {
					Map<String, String> procedure = new LinkedHashMap<String, String>();
					procedure.put("code", entry.getValue().getCode());
					procedure.put("name", entry.getValue().getName());
					procedures.put(concept.getUuid(), procedure);
				}
			}
		}
		ret.add("procedures", procedures);
		return ret;
	}

	/**
	 * Requests MMI's approval for orders just placed. Body: encounter (uuid), concepts (the uuids the clinician kept
	 * ticked) and an optional note. Only active new orders of that encounter are requested.
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/insuranceApproval/request", method = RequestMethod.POST)
	@ResponseBody
	public SimpleObject request(@RequestBody Map<String, Object> body) {
		SimpleObject ret = new SimpleObject();
		ret.add("success", false);
		Encounter encounter = Context.getEncounterService().getEncounterByUuid(text(body.get("encounter")));
		if (encounter == null) {
			ret.add("message", "Encounter not found");
			return ret;
		}
		Set<String> conceptUuids = new HashSet<String>();
		if (body.get("concepts") instanceof List) {
			for (Object value : (List<?>) body.get("concepts")) {
				if (value != null) {
					conceptUuids.add(value.toString());
				}
			}
		}
		InsuranceApprovalCheck check = InsuranceApprovalCheck.forPatient(encounter.getPatient());
		if (check == null || !check.canRequest()) {
			ret.add("message", check == null ? "The patient's insurance does not need approvals" : "The patient has no MMI reception");
			return ret;
		}
		Map<Procedure, BigDecimal> procedures = InsuranceApprovalCheck.newProcedureMap();
		org.openmrs.Provider orderer = null;
		for (Order order : encounter.getOrders()) {
			if (order.getVoided() || order.getAction() != Order.Action.NEW || order.getConcept() == null
					|| !conceptUuids.contains(order.getConcept().getUuid())) {
				continue;
			}
			InsuranceApprovalCheck.add(procedures, check.procedureFor(order.getConcept()), BigDecimal.ONE, null);
			orderer = orderer == null ? order.getOrderer() : orderer;
		}
		if (procedures.isEmpty()) {
			ret.add("message", "None of these orders needs the insurer's approval");
			return ret;
		}
		try {
			RhipApprovalRequestService.Result result = check.request(procedures, text(body.get("note")), encounter, orderer);
			ret.put("success", result != null && result.isSuccess());
			ret.add("message", result == null ? null : result.getMessage());
			ret.add("approvalCode", result == null ? null : result.getApprovalCode());
		}
		catch (Exception e) {
			log.error("Unable to request insurance approval for the orders of encounter " + encounter.getUuid(), e);
			ret.add("message", "Unable to request insurance approval: " + e.getMessage());
		}
		return ret;
	}

	private String text(Object value) {
		return value == null ? null : StringUtils.trimToNull(value.toString());
	}
}
