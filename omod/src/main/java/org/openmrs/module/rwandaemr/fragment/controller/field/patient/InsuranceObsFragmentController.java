/*
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

package org.openmrs.module.rwandaemr.fragment.controller.field.patient;

import org.openmrs.Concept;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InsuranceObsFragmentController {

    public void controller(FragmentConfiguration config, UiSessionContext sessionContext, FragmentModel model) {
        Patient patient = getPatient(config);
        List<Map<String, String>> policies = new ArrayList<>();
        List<InsurancePolicy> insurancePolicies = getBillingService().getAllInsurancePoliciesByPatient(patient);
        if (insurancePolicies != null) {
            insurancePolicies.sort((a, b) -> a.getInsurance().getName().compareToIgnoreCase(b.getInsurance().getName()));
            for (InsurancePolicy policy : insurancePolicies) {
                if (policy.getInsurance().getConcept() != null) {
                    Map<String, String> m = new HashMap<>();
                    m.put("policyId", policy.getInsurancePolicyId().toString());
                    m.put("insuranceConceptId", policy.getInsurance().getConcept().getId().toString());
                    m.put("insuranceName", policy.getInsurance().getName());
                    m.put("cardNumber", policy.getInsuranceCardNo());
                    m.put("coverageStartDate", formatDate(policy.getCoverageStartDate()));
                    m.put("coverageEndDate", formatDate(policy.getExpirationDate()));
                    policies.add(m);
                }
            }
        }
        String policyId = (String)config.get("initialPolicyId");
        Concept insuranceConcept = getConcept(config.get("initialInsuranceConcept"));
        String insuranceNumber = (String) config.get("initialInsuranceNumber");
        model.addAttribute("patient", patient);
        model.addAttribute("policies", policies);
        model.addAttribute("initialPolicyId", policyId == null ? "" : policyId);
        model.addAttribute("initialInsuranceConcept", insuranceConcept == null ? "" : insuranceConcept.getId().toString());
        model.addAttribute("initialInsuranceNumber", insuranceNumber == null ? "" : insuranceNumber);
        model.addAttribute("initialEncounterDate", getEncounterDate(config));
    }

    public String formatDate(Date date) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        if (date == null) {
            return "";
        }
        return df.format(date);
    }

    public Patient getPatient(FragmentConfiguration config) {
        config.require("patient");
        Object patient = config.get("patient");
        if (patient instanceof Patient) {
            return (Patient) patient;
        }
        else if (patient instanceof PatientDomainWrapper) {
            return ((PatientDomainWrapper) patient).getPatient();
        }
        else {
            throw new IllegalArgumentException("Invalid patient specified: " + patient);
        }
    }

    public String getEncounterDate(FragmentConfiguration config) {
        Object d = config.get("initialEncounterDate");
        if (d == null) {
            d = new Date();
        }
        if (d instanceof Date) {
            return formatDate((Date)d);
        }
        else if (d instanceof String) {
            return (String) d;
        }
        else {
            throw new IllegalArgumentException("Invalid date specified: " + d);
        }
    }

    public Concept getConcept(Object obj) {
        Concept ret = null;
        if (obj != null) {
            try {
                if (obj instanceof Concept) {
                    ret = (Concept) obj;
                } else {
                    ret = HtmlFormEntryUtil.getConcept(obj.toString());
                }
            }
            catch (Exception e) {
                throw new IllegalArgumentException("Invalid concept: " + obj);
            }
        }
        return ret;
    }

    protected BillingService getBillingService() {
        return Context.getService(BillingService.class);
    }
}
