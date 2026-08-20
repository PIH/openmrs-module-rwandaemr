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
import java.util.UUID;

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
            Map<String, String> identifiersToSearch = new LinkedHashMap<>();
            try {
                for (Object parameter : request.getParameterMap().keySet()) {
                    String paramName = (String) parameter;
                    if (paramName.startsWith("identifier_")) {
                        String[] split = paramName.split("_");
                        String identifierTypeUuid = split[1];
                        String identifier = request.getParameter(paramName);
                        if (StringUtils.isNotBlank(identifier)) {
                            PatientIdentifierType identifierType = rwandaEmrConfig.getPatientIdentifierTypeByUuid(identifierTypeUuid);
                            String identifierSystem = integrationConfig.getIdentifierSystem(identifierType);
                            identifiersToSearch.put(identifierSystem, identifier);
                        }
                    }
                }
            } catch (Exception e) {
                return noPatientResponse("rwandaemr.clientRegistry.invalidConfiguration", null, ui);
            }

            // First attempt to find a patient in the client registry by identifier
            for (String identifierSystem : identifiersToSearch.keySet()) {
                String identifier = identifiersToSearch.get(identifierSystem);
                log.debug("Searching client registry for " + identifierSystem + "=" + identifier);
                ClientRegistryPatient crPatient;
                try {
                    crPatient = clientRegistryPatientProvider.fetchPatientFromClientRegistry(identifier, identifierSystem);
                } catch (Exception e) {
                    return noPatientResponse("rwandaemr.clientRegistry.connectionError", e, ui);
                }
                if (crPatient != null) {
                    try {
                        Patient patient = clientRegistryPatientTranslator.toPatient(crPatient);
                        String fosaId = integrationConfig.getFosaId(uiSessionContext.getSessionLocation());
                        String photo = findPopulationRegistryPhoto(identifiersToSearch, patient, fosaId, integrationConfig, citizenProvider);
                        return patientResponse("rwandaemr.clientRegistry.matchFound", patient, photo, rwandaEmrConfig, ui);
                    } catch (Exception e) {
                        log.error("Failed to convert client registry patient for identifier "
                                + identifierSystem + "=" + identifier, e);
                        return noPatientResponse("rwandaemr.clientRegistry.patientConversionError", e, ui);
                    }
                }
            }

            // If no patient was found by identifier, look up in population registry
            Location sessionLocation = uiSessionContext.getSessionLocation();
            String fosaId = integrationConfig.getFosaId(sessionLocation);
            if (StringUtils.isBlank(fosaId)) {
                return noPatientResponse("rwandaemr.populationRegistry.invalidFosaIdConfiguration", null, ui);
            }

            // Default to generating a UPID from a tempid if needed
            identifiersToSearch.put(IntegrationConfig.IDENTIFIER_SYSTEM_TEMPID, UUID.randomUUID().toString());

            Exception lastPopulationRegistryError = null;
            for (String identifierSystem : identifiersToSearch.keySet()) {
                if (identifierSystem != null) {
                    String identifier = identifiersToSearch.get(identifierSystem);
                    Citizen citizen;
                    try {
                        citizen = citizenProvider.getCitizen(identifierSystem, identifier, fosaId);
                    }
                    catch (Exception e) {
                        // Don't abort on first identifier failure; try remaining identifiers (including TEMPID).
                        // This avoids false "connectionError" when one lookup fails but another may succeed.
                        lastPopulationRegistryError = e;
                        log.warn("Population registry lookup failed for "+ identifierSystem + "="+ identifier + ":" + e.getMessage());
                        continue;
                    }
                    if (citizen != null) {
                        try {
                            Patient patient = citizenTranslator.toPatient(citizen);
                            String message = identifierSystem.equals(IntegrationConfig.IDENTIFIER_SYSTEM_TEMPID) ? "rwandaemr.populationRegistry.upidGenerated" : "rwandaemr.populationRegistry.matchFound";
                            return patientResponse(message, patient, citizen.getPhoto(), rwandaEmrConfig, ui);
                        }
                        catch (Exception e) {
                            return noPatientResponse("rwandaemr.populationRegistry.patientConversionError", e, ui);
                        }
                    }
                }
            }
            if (lastPopulationRegistryError != null) {
                return noPatientResponse("rwandaemr.populationRegistry.connectionError", lastPopulationRegistryError, ui);
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
            String exceptionMessage = e.getMessage();
            if (StringUtils.isBlank(exceptionMessage)) {
                exceptionMessage = e.getClass().getName();
            }
            data.put("exception", exceptionMessage);
        }
        return new ObjectResult(data);
    }

    private ObjectResult patientResponse(String message, Patient patient, String photo, RwandaEmrConfig rwandaEmrConfig, UiUtils ui) {
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

            p.put("photo", normalizePhotoForBrowser(photo));
            data.put("patient", p);
        }
        return new ObjectResult(data);
    }

    private String findPopulationRegistryPhoto(Map<String, String> identifiersToSearch,
                                               Patient patient,
                                               String fosaId,
                                               IntegrationConfig integrationConfig,
                                               CitizenProvider citizenProvider) {
        if (StringUtils.isBlank(fosaId)) {
            log.warn("No FOSA ID configured; skipping population registry photo lookup for client registry match");
            return null;
        }

        Map<String, String> photoIdentifiersToSearch = new LinkedHashMap<>(identifiersToSearch);
        for (PatientIdentifier pi : patient.getIdentifiers()) {
            String identifierSystem = integrationConfig.getIdentifierSystem(pi.getIdentifierType());
            if (StringUtils.isNotBlank(identifierSystem) && StringUtils.isNotBlank(pi.getIdentifier())) {
                photoIdentifiersToSearch.putIfAbsent(identifierSystem, pi.getIdentifier());
            }
        }

        for (String identifierSystem : photoIdentifiersToSearch.keySet()) {
            if (IntegrationConfig.IDENTIFIER_SYSTEM_TEMPID.equals(identifierSystem)) {
                continue;
            }
            String identifier = photoIdentifiersToSearch.get(identifierSystem);
            try {
                Citizen citizen = citizenProvider.getCitizen(identifierSystem, identifier, fosaId);
                if (citizen != null && StringUtils.isNotBlank(citizen.getPhoto())) {
                    return citizen.getPhoto();
                }
            }
            catch (Exception e) {
                log.warn("Population registry photo lookup failed for client registry match using "
                        + identifierSystem + "=" + identifier + ": " + e.getMessage());
            }
        }
        return null;
    }

    private String normalizePhotoForBrowser(String photo) {
        if (StringUtils.isBlank(photo)) {
            return photo;
        }
        String trimmed = photo.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("data:image/") || lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        String compact = trimmed.replaceAll("\\s", "");
        if (isBase64Image(compact)) {
            return "data:image/jpeg;base64," + compact;
        }
        return trimmed;
    }

    private boolean isBase64Image(String value) {
        if (StringUtils.isBlank(value) || value.length() < 100) {
            return false;
        }
        if (!value.startsWith("/9j/") && !value.startsWith("iVBOR")) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '+'
                    || c == '/'
                    || c == '=';
            if (!valid) {
                return false;
            }
        }
        return true;
    }
}
