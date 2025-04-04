/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;

/**
 * Translation layer between an HIE citizen and an OpenMRS patient
 */
@Component
public class CitizenTranslator {

    protected Log log = LogFactory.getLog(getClass());

    private final RwandaEmrConfig rwandaEmrConfig;
    private final IntegrationConfig integrationConfig;

    public CitizenTranslator(@Autowired RwandaEmrConfig rwandaEmrConfig, @Autowired IntegrationConfig integrationConfig) {
        this.rwandaEmrConfig = rwandaEmrConfig;
        this.integrationConfig = integrationConfig;
    }

    public Patient toPatient(@Nonnull Citizen citizen) {
        Patient p = new Patient();

        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NID, citizen.getNid());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NIN, citizen.getNin());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_UPI, citizen.getUpi());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NID_APPLICATION_NUMBER, citizen.getApplicationNumber());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_PASSPORT, citizen.getPassportNumber());

        if (StringUtils.isNotBlank(citizen.getSurName()) || StringUtils.isNotBlank(citizen.getPostNames())) {
            PersonName name = new PersonName();
            name.setPerson(p);
            name.setGivenName(citizen.getPostNames());
            name.setFamilyName(citizen.getSurName());
            name.setPreferred(true);
            p.addName(name);
        }

        if (StringUtils.isNotBlank(citizen.getSex())) {
            if (citizen.getSex().equalsIgnoreCase("MALE")) {
                p.setGender("M");
            }
            else if (citizen.getSex().equalsIgnoreCase("FEMALE")) {
                p.setGender("F");
            }
            else {
                log.warn("Unable to set gender, unknown value: " + citizen.getSex());
            }
        }

        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        if (StringUtils.isNotBlank(citizen.getDateOfBirth())) {
            try {
                p.setBirthdate(df.parse(citizen.getDateOfBirth()));
            }
            catch (Exception e) {
                log.warn("Unable to set date of birth, unexpected format: " + citizen.getDateOfBirth());
            }
        }

        addPersonAttribute(p, rwandaEmrConfig.getMothersName(), citizen.getMotherName());
        addPersonAttribute(p, rwandaEmrConfig.getFathersName(), citizen.getFatherName());

        return p;
    }

    void addPatientIdentifier(Patient patient, String identifierSystem, String identifier) {
        if (StringUtils.isNotEmpty(identifier)) {
            PatientIdentifierType identifierType = integrationConfig.getIdentifierSystems().get(identifierSystem);
            if (identifierType != null) {
                PatientIdentifier pi = new PatientIdentifier();
                pi.setPatient(patient);
                pi.setIdentifierType(identifierType);
                pi.setIdentifier(identifier);
                patient.addIdentifier(pi);
            } else {
                log.debug("Not adding identifier of type: " + identifierSystem);
            }
        }
    }

    void addPersonAttribute(Patient patient, PersonAttributeType personAttributeType, String value) {
        if (StringUtils.isNotEmpty(value)) {
            if (personAttributeType != null) {
                PersonAttribute personAttribute = new PersonAttribute();
                personAttribute.setPerson(patient);
                personAttribute.setAttributeType(personAttributeType);
                personAttribute.setValue(value);
                patient.addAttribute(personAttribute);
            }
            else {
                log.warn("Unable to add person attribute, as type is not found");
            }
        }
    }

}