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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.contextmodel.PatientContextModel;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;

import java.util.Comparator;
import java.util.List;

/**
 * Supports an encounters section fragment on the patient dashboard
 */
public class EncountersSectionFragmentController {

	protected final Log log = LogFactory.getLog(EncountersSectionFragmentController.class);

	public void controller(FragmentConfiguration config,
						   PageModel pageModel,
						   FragmentModel model,
						   UiUtils ui,
						   UiSessionContext sessionContext,
						   @FragmentParam("app") AppDescriptor appDescriptor,
                           @SpringBean("encounterService") EncounterService encounterService,
						   @InjectBeans PatientDomainWrapper patientWrapper) {
		config.require("patient");
		Object patient = config.get("patient");

		if (patient instanceof Patient) {
			patientWrapper.setPatient((Patient) patient);
			config.addAttribute("patient", patientWrapper);
		} else if (patient instanceof PatientDomainWrapper) {
			patientWrapper = (PatientDomainWrapper) patient;
		}

		AppContextModel contextModel = sessionContext.generateAppContextModel();
		contextModel.put("patient", new PatientContextModel(patientWrapper.getPatient()));
		contextModel.put("patientId", patientWrapper.getPatient().getUuid());  // backwards-compatible for links that still specify patient uuid substitution with "{{patientId}}"

		EncounterSearchCriteriaBuilder b = new EncounterSearchCriteriaBuilder();
		b.setPatient(patientWrapper.getPatient()).setIncludeVoided(false);
		List<Encounter> encounters = encounterService.getEncounters(b.createEncounterSearchCriteria());
		encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime).reversed());

		if (appDescriptor != null && appDescriptor.getConfig() != null && appDescriptor.getConfig().get("limit") != null) {
			try {
				int limit = Integer.parseInt(appDescriptor.getConfig().get("limit").getTextValue());
				if (encounters.size() > limit) {
					encounters = encounters.subList(0, limit);
				}
			}
			catch (Exception e) {
				log.warn("Invalid limit specified.  Not a valid integer: " + appDescriptor.getConfig().get("limit"));
			}
		}

		model.addAttribute("encounters", encounters);
	}
}
