/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * This supports a fragment that is intended to be used on a 2.x patient dashboard to display encounters
 * of a particular type within a particular visit, most commonly the active visit.
 * This accepts a fragment configuration like the following:
 * "fragmentConfig": {
 *   "encounterType": "the-uuid-of-the-encounter-type", // required
 *   "creatable": true, // optional, defaults to false
 *   "editIcon": "" // optional, the icon to display next to an encounter for editing
 *   "editProvider": "" // optional, defaults to "htmlformentryui".  the fragment provider for edit mode
 *   "editFragment": "" // optional, defaults to "htmlform/editHtmlFormWithStandardUi"
 *   "editParams": {}, // optional map of fragment parameters.  defaults to [patientId: patient.id, encounterId: "{encounterId}", definitionUiResource: definitionUiResource, returnUrl: returnUrl ].  supports {patientId}, {encounterId}, and {visitId} replacements.
 *   "creatable": true/false, // defaults to false.  if true, and no encounters exist, allows creating a new encounter
 *   "createIcon": "", // optional, the icon to display to create a new encounter
 *   "createProvider": ""  // optional, defaults to "htmlformentryui".  the fragment provider for enter mode
 *   "createFragment": ""  // optional, defaults to "htmlform/enterHtmlFormWithStandardUi"
 *   "createParams": {}, // optional map of fragment parameters.  defaults to [patientId: patient.id, visitId: visit?.id, createVisit: "true", definitionUiResource: definitionUiResource, returnUrl: returnUrl ].  supports {patientId}, {encounterId}, and {visitId} replacements.
 *   "returnUrl": "", // optional, sets the url to return to after navigating to create or edit an encounter
 *   "definitionUiResource": "file:configuration/htmlforms/my-form.xml", // the resource to use for view/enter/edit
 *   "noEncountersMessage", // optional, defaults to coreapps.clinicianfacing.noneRecorded
 *   "classToHide": "", // optional, class name to identify any elements that should be hidden on the rendered htmlform on the dashboard
 *   "classToShow": "", // optional, class name to identify any elements that should be shown on the rendered htmlform on the dashboard
 *   "sortAscending": true/false // defaults to false.  determines whether to order encounters by date asc or desc
 * }
 */
public class EncountersDuringVisitFragmentController {

	public void controller(FragmentConfiguration config, FragmentModel model, UiUtils ui,
						   @FragmentParam("patientId") Patient patient,
						   @FragmentParam(value = "visitId", required = false) Visit visit,
						   @FragmentParam("app") AppDescriptor app,
						   @SpringBean("visitService") VisitService visitService,
	                       @SpringBean("encounterService") EncounterService encounterService) {

		if (visit == null) {
			List<Visit> activeVisits = visitService.getActiveVisitsByPatient(patient);
			if (!activeVisits.isEmpty()) {
				if (activeVisits.size() > 1) {
					throw new IllegalStateException("you must pass in a visit since the patient has more than one active visit");
				}
				visit = activeVisits.get(0);
			}
		}

		List<Encounter> encounters = new ArrayList<>();
		if (visit != null) {
			String encounterTypeUuid = getConfigValue(config, "encounterType", null);
			if (encounterTypeUuid == null) {
				throw new IllegalStateException("encounterType app config parameter is required");
			}
			EncounterType encounterType = encounterService.getEncounterTypeByUuid(encounterTypeUuid);
			if (encounterType == null) {
				throw new IllegalStateException("encounterType unable to be found by uuid: " + encounterTypeUuid);
			}

			encounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
					.setPatient(patient)
					.setVisits(Collections.singletonList(visit))
					.setEncounterTypes(Collections.singletonList(encounterType))
					.setIncludeVoided(false)
					.createEncounterSearchCriteria()
			);

			String sortAscending = getConfigValue(config, "sortAscending", "false");
			Comparator<Encounter> encounterComparator = Comparator.comparing(Encounter::getEncounterDatetime);
			if (Boolean.parseBoolean(sortAscending)) {
				encounters.sort(encounterComparator);
			}
			else {
				encounters.sort(encounterComparator.reversed());
			}
		}

		model.addAttribute("app", app);
		model.addAttribute("patient", patient);
		model.addAttribute("visit", visit);
		model.addAttribute("encounters", encounters);
	}

	private String getConfigValue(Map<String, Object> m, String attribute, String defaultValue) {
		Object value = m.get(attribute);
		if (value == null) {
			return defaultValue;
		}
		if (value instanceof String) {
			return (String) value;
		}
		throw new IllegalStateException(attribute + " is not a String, it is a " + value.getClass().getSimpleName());
	}
}
