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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.Citizen;
import org.openmrs.module.rwandaemr.integration.CitizenProvider;
import org.openmrs.module.rwandaemr.integration.CitizenTranslator;
import org.openmrs.module.rwandaemr.integration.ClientRegistryPatient;
import org.openmrs.module.rwandaemr.integration.ClientRegistryPatientProvider;
import org.openmrs.module.rwandaemr.integration.ClientRegistryPatientTranslator;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.action.ObjectResult;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This method attempts to fulfill the workflow laid out in the Rwanda HIE guidelines, which is to first look up
 * an existing patient in the Client Registry with an eligible identifier.  If no matching patient is found,
 * then use the UPID generator to retrieve a UPID and retrieve patient details from the national population registry
 * The registrationLocation is what will be used to determine the FOSA ID to send with the UPID generation request
 */
public class SearchClientRegistryFragmentController {

    protected Log log = LogFactory.getLog(getClass());

    public void controller(@SpringBean IntegrationConfig integrationConfig, FragmentModel model) {
        model.addAttribute("clientRegistryEnabled", integrationConfig.isHieEnabled());
    }

    public FragmentActionResult findByIdentifier(HttpServletRequest request,
                                                 UiUtils ui,
                                                 UiSessionContext uiSessionContext,
                                                 @SpringBean RwandaEmrConfig rwandaEmrConfig,
                                                 @SpringBean IntegrationConfig integrationConfig,
                                                 @SpringBean ClientRegistryPatientProvider clientRegistryPatientProvider,
                                                 @SpringBean ClientRegistryPatientTranslator clientRegistryPatientTranslator,
                                                 @SpringBean CitizenProvider citizenProvider,
                                                 @SpringBean CitizenTranslator citizenTranslator) {

        try {
            if (!integrationConfig.isHieEnabled()) {
                return noPatientResponse("rwandaemr.clientregistry.notEnabled", null, ui);
            }

            // Retrieve all identifiers to search on from the request
            Map<PatientIdentifierType, String> identifiersToSearch = new LinkedHashMap<>();
            try {
                for (Object parameter : request.getParameterMap().keySet()) {
                    String paramName = (String) parameter;
                    if (paramName.startsWith("identifier_")) {
                        String[] split = paramName.split("_");
                        String identifierTypeUuid = split[1];
                        String identifier = request.getParameter(paramName);
                        if (StringUtils.isNotBlank(identifier)) {
                            PatientIdentifierType idType = rwandaEmrConfig.getPatientIdentifierTypeByUuid(identifierTypeUuid);
                            identifiersToSearch.put(idType, identifier);
                        }
                    }
                }
            } catch (Exception e) {
                return noPatientResponse("rwandaemr.clientRegistry.invalidConfiguration", null, ui);
            }

            if (identifiersToSearch.isEmpty()) {
                return noPatientResponse("rwandaemr.clientRegistry.noIdentifiersEntered", null, ui);
            }

            // First attempt to find a patient in the client registry by identifier
            for (PatientIdentifierType identifierType : identifiersToSearch.keySet()) {
                String identifier = identifiersToSearch.get(identifierType);
                log.debug("Searching client registry for " + identifierType.getName() + "=" + identifier);
                ClientRegistryPatient crPatient;
                try {
                    crPatient = clientRegistryPatientProvider.fetchPatientFromClientRegistry(identifier);
                } catch (Exception e) {
                    return noPatientResponse("rwandaemr.clientRegistry.connectionError", e, ui);
                }
                if (crPatient != null) {
                    String identifierSystem = integrationConfig.getIdentifierSystem(identifierType);
                    if (identifier.equalsIgnoreCase(crPatient.getIdentifierValue(identifierSystem))) {
                        try {
                            Patient patient = clientRegistryPatientTranslator.toPatient(crPatient);
                            return patientResponse("rwandaemr.clientRegistry.matchFound", patient, rwandaEmrConfig, ui);
                        } catch (Exception e) {
                            return noPatientResponse("rwandaemr.clientRegistry.patientConversionError", e, ui);
                        }
                    }
                }
            }

            // If no patient was found by identifier, look up in population registry
            Location sessionLocation = uiSessionContext.getSessionLocation();
            String fosaId = integrationConfig.getFosaId(sessionLocation);
            if (StringUtils.isBlank(fosaId)) {
                return noPatientResponse("rwandaemr.populationRegistry.invalidFosaIdConfiguration", null, ui);
            }
            for (PatientIdentifierType identifierType : identifiersToSearch.keySet()) {
                String identifierSystem = integrationConfig.getIdentifierSystem(identifierType);
                if (identifierSystem != null) {
                    String identifier = identifiersToSearch.get(identifierType);
                    Citizen citizen;
                    try {
                        citizen = citizenProvider.getCitizen(identifierSystem, identifier, fosaId);
                    }
                    catch (Exception e) {
                        return noPatientResponse("rwandaemr.populationRegistry.connectionError", e, ui);
                    }
                    if (citizen != null) {
                        try {
                            Patient patient = citizenTranslator.toPatient(citizen);
                            return patientResponse("rwandaemr.populationRegistry.matchFound", patient, rwandaEmrConfig, ui);
                        }
                        catch (Exception e) {
                            return noPatientResponse("rwandaemr.populationRegistry.patientConversionError", e, ui);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            return noPatientResponse("rwandaemr.clientregistry.unexpectedError", e, ui);
        }
        return noPatientResponse("rwandaemr.populationRegistry.noMatchFound", null, ui);
    }

    private ObjectResult noPatientResponse(String message, Exception e, UiUtils ui) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageCode", message);
        data.put("message", ui.message(message));
        if (e != null) {
            data.put("exception", e.getMessage());
        }
        return new ObjectResult(data);
    }

    private ObjectResult patientResponse(String message, Patient patient, RwandaEmrConfig rwandaEmrConfig, UiUtils ui) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageCode", message);
        data.put("message", ui.message(message));
        if (patient != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("birthdate", patient.getBirthdate() == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthdate()));
            p.put("givenName", patient.getGivenName());
            p.put("familyName", patient.getFamilyName());
            p.put("gender", patient.getGender());
            if (patient.getBirthdate() != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(patient.getBirthdate());
                p.put("birthdateDay", c.get(Calendar.DAY_OF_MONTH));
                p.put("birthdateMonth", c.get(Calendar.MONTH) + 1);
                p.put("birthdateYear", c.get(Calendar.YEAR));
            }

            // TODO: Retrieve and configure from registration config, not hard-coded

            for (PatientIdentifier pi : patient.getIdentifiers()) {
                if (pi.getIdentifierType().equals(rwandaEmrConfig.getNationalId())) {
                    p.put("nationalId", pi.getIdentifier());
                } else if (pi.getIdentifierType().equals(rwandaEmrConfig.getNidApplicationNumber())) {
                    p.put("applicationNumber", pi.getIdentifier());
                } else if (pi.getIdentifierType().equals(rwandaEmrConfig.getUPID())) {
                    p.put("upid", pi.getIdentifier());
                } else if (pi.getIdentifierType().equals(rwandaEmrConfig.getNIN())) {
                    p.put("nin", pi.getIdentifier());
                } else if (pi.getIdentifierType().equals(rwandaEmrConfig.getPassportNumber())) {
                    p.put("passportNumber", pi.getIdentifier());
                }
            }

            for (PersonAttribute pa : patient.getAttributes()) {
                if (pa.getAttributeType().equals(rwandaEmrConfig.getTelephoneNumber())) {
                    p.put("phoneNumber", pa.getValue());
                } else if (pa.getAttributeType().equals(rwandaEmrConfig.getMothersName())) {
                    p.put("mothersName", pa.getValue());
                } else if (pa.getAttributeType().equals(rwandaEmrConfig.getFathersName())) {
                    p.put("fathersName", pa.getValue());
                } else if (pa.getAttributeType().equals(rwandaEmrConfig.getEducationLevel())) {
                    p.put("educationLevel", pa.getValue());
                } else if (pa.getAttributeType().equals(rwandaEmrConfig.getProfession())) {
                    p.put("profession", pa.getValue());
                } else if (pa.getAttributeType().equals(rwandaEmrConfig.getReligion())) {
                    p.put("religion", pa.getValue());
                }
            }

            PersonAddress pa = patient.getPersonAddress();
            if (pa != null) {
                p.put("country", pa.getCountry());
                p.put("stateProvince", pa.getStateProvince());
                p.put("countyDistrict", pa.getCountyDistrict());
                p.put("cityVillage", pa.getStateProvince());
                p.put("address3", pa.getAddress3());
                p.put("address1", pa.getAddress1());
            }

            data.put("patient", p);
        }
        return new ObjectResult(data);
    }
}
