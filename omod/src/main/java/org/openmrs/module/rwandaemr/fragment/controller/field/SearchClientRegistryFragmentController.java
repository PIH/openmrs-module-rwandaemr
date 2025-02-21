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

package org.openmrs.module.rwandaemr.fragment.controller.field;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.NidaMpiProvider;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.fragment.action.FailureResult;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.action.ObjectResult;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

public class SearchClientRegistryFragmentController {

    public void controller(@SpringBean NidaMpiProvider mpiProvider, FragmentModel model) {
        model.addAttribute("clientRegistryEnabled", mpiProvider.isEnabled());
    }

    public FragmentActionResult findByIdentifier(HttpServletRequest request,
                                                 UiSessionContext uiSessionContext,
                                                 @SpringBean RwandaEmrConfig rwandaEmrConfig,
                                                 @SpringBean NidaMpiProvider mpiProvider) {

        Map<String, String> identifiersToSearch = new LinkedHashMap<>();
        for (Object parameter : request.getParameterMap().keySet()) {
            String paramName = (String) parameter;
            if (paramName.startsWith("identifier_")) {
                String[] split = paramName.split("_");
                String identifierType = split[1];
                String identifier = request.getParameter(paramName);
                if (StringUtils.isNotBlank(identifier)) {
                    identifiersToSearch.put(identifierType, identifier);
                }
            }
        }

        Patient patient = mpiProvider.fetchPatientFromClientOrPopulationRegistry(identifiersToSearch, uiSessionContext.getSessionLocation());
        if (patient == null) {
            return new FailureResult("rwandaemr.clientRegistry.patientNotFound");
        }
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("birthdate", patient.getBirthdate() == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthdate()));
        data.put("givenName", patient.getGivenName());
        data.put("familyName", patient.getFamilyName());
        data.put("gender", patient.getGender());
        if (patient.getBirthdate() != null) {
            Calendar c = Calendar.getInstance();
            c.setTime(patient.getBirthdate());
            data.put("birthdateDay", c.get(Calendar.DAY_OF_MONTH));
            data.put("birthdateMonth", c.get(Calendar.MONTH) + 1);
            data.put("birthdateYear", c.get(Calendar.YEAR));
        }

        // TODO: Retrieve and configure from registration config, not hard-coded

        for (PatientIdentifier pi : patient.getIdentifiers()) {
            if (pi.getIdentifierType().equals(rwandaEmrConfig.getNationalId())) {
                data.put("nationalId", pi.getIdentifier());
            }
            else if (pi.getIdentifierType().equals(rwandaEmrConfig.getNidApplicationNumber())) {
                data.put("applicationNumber", pi.getIdentifier());
            }
            else if (pi.getIdentifierType().equals(rwandaEmrConfig.getUPID())) {
                data.put("upid", pi.getIdentifier());
            }
            else if (pi.getIdentifierType().equals(rwandaEmrConfig.getNIN())) {
                data.put("nin", pi.getIdentifier());
            }
            else if (pi.getIdentifierType().equals(rwandaEmrConfig.getPassportNumber())) {
                data.put("passportNumber", pi.getIdentifier());
            }
        }

        for (PersonAttribute pa : patient.getAttributes()) {
            if (pa.getAttributeType().equals(rwandaEmrConfig.getTelephoneNumber())) {
                data.put("phoneNumber", pa.getValue());
            }
            else if (pa.getAttributeType().equals(rwandaEmrConfig.getMothersName())) {
                data.put("mothersName", pa.getValue());
            }
            else if (pa.getAttributeType().equals(rwandaEmrConfig.getFathersName())) {
                data.put("fathersName", pa.getValue());
            }
            else if (pa.getAttributeType().equals(rwandaEmrConfig.getEducationLevel())) {
                data.put("educationLevel", pa.getValue());
            }
            else if (pa.getAttributeType().equals(rwandaEmrConfig.getProfession())) {
                data.put("profession", pa.getValue());
            }
            else if (pa.getAttributeType().equals(rwandaEmrConfig.getReligion())) {
                data.put("religion", pa.getValue());
            }
        }

        PersonAddress pa = patient.getPersonAddress();
        if (pa != null) {
            data.put("country", pa.getCountry());
            data.put("stateProvince", pa.getStateProvince());
            data.put("countyDistrict", pa.getCountyDistrict());
            data.put("cityVillage", pa.getStateProvince());
            data.put("address3", pa.getAddress3());
            data.put("address1", pa.getAddress1());
        }

        return new ObjectResult(data);
    }

}
