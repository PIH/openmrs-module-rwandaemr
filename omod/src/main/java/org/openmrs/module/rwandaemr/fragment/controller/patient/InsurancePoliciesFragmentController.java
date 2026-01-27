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

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.contextmodel.PatientContextModel;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.List;

/**
 * Supports an insurance section fragment on the patient dashboard
 */
public class InsurancePoliciesFragmentController {

	public void controller(FragmentConfiguration config,
						   FragmentModel model,
						   UiSessionContext sessionContext,
						   @InjectBeans PatientDomainWrapper patientWrapper) {

		config.require("patient");
		Object patient = config.get("patient");
		if (patient instanceof Patient) {
			patientWrapper.setPatient((Patient) patient);
			config.addAttribute("patient", patientWrapper);
		}
		else if (patient instanceof PatientDomainWrapper) {
			patientWrapper = (PatientDomainWrapper) patient;
		}

		AppContextModel contextModel = sessionContext.generateAppContextModel();
		contextModel.put("patient", new PatientContextModel(patientWrapper.getPatient()));
		contextModel.put("patientId", patientWrapper.getPatient().getUuid());  // backwards-compatible for links that still specify patient uuid substitution with "{{patientId}}"

		BillingService billingService = Context.getService(BillingService.class);
		List<InsurancePolicy> policies = billingService.getAllInsurancePoliciesByPatient(patientWrapper.getPatient());
		policies.sort((a, b) -> a.getInsurance().getName().compareToIgnoreCase(b.getInsurance().getName()));
		model.addAttribute("insurancePolicies", policies);
	}
}
