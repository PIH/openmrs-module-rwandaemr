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

import lombok.Data;
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
 * Translation layer between the Patient json returned from the UPID generator / population registry and an OpenMRS patient
 */
@Component
public class UpidPatientTranslator {

    protected Log log = LogFactory.getLog(getClass());

    private final RwandaEmrConfig rwandaEmrConfig;
    private final IntegrationConfig integrationConfig;

    public UpidPatientTranslator(@Autowired RwandaEmrConfig rwandaEmrConfig, @Autowired IntegrationConfig integrationConfig) {
        this.rwandaEmrConfig = rwandaEmrConfig;
        this.integrationConfig = integrationConfig;
    }

    public Patient toOpenmrsType(@Nonnull UpidPatient upidPatient) {
        Patient p = new Patient();

        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NID, upidPatient.getNid());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NIN, upidPatient.getNin());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_UPI, upidPatient.getUpi());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_NID_APPLICATION_NUMBER, upidPatient.getApplicationNumber());
        addPatientIdentifier(p, IntegrationConfig.IDENTIFIER_SYSTEM_PASSPORT, upidPatient.getPassportNumber());

        if (StringUtils.isNotBlank(upidPatient.getSurName()) || StringUtils.isNotBlank(upidPatient.getPostNames())) {
            PersonName name = new PersonName();
            name.setPerson(p);
            name.setGivenName(upidPatient.getPostNames());
            name.setFamilyName(upidPatient.getSurName());
            name.setPreferred(true);
            p.addName(name);
        }

        if (StringUtils.isNotBlank(upidPatient.getSex())) {
            if (upidPatient.getSex().equalsIgnoreCase("MALE")) {
                p.setGender("M");
            }
            else if (upidPatient.getSex().equalsIgnoreCase("FEMALE")) {
                p.setGender("F");
            }
            else {
                log.warn("Unable to set gender, unknown value: " + upidPatient.getSex());
            }
        }

        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");
        if (StringUtils.isNotBlank(upidPatient.getDateOfBirth())) {
            try {
                p.setBirthdate(df.parse(upidPatient.getDateOfBirth()));
            }
            catch (Exception e) {
                log.warn("Unable to set date of birth, unexpected format: " + upidPatient.getDateOfBirth());
            }
        }

        addPersonAttribute(p, rwandaEmrConfig.getMothersName(), upidPatient.getMotherName());
        addPersonAttribute(p, rwandaEmrConfig.getFathersName(), upidPatient.getFatherName());

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

    @Data
    public static class UpidResponse {
        private String status;
        private UpidPatient data;
    }

    @Data
    public static class UpidPatient {
        private String fosaid;
        private String documentType; // NID, PASSPORT
        private String documentNumber;
        private String issueNumber;
        private String dateOfIssue;  // dd/MM/yyyy
        private String dateOfExpiry; // dd/MM/yyyy
        private String placeOfIssue;
        private String applicationNumber;
        private String nin;
        private String nid;
        private String upi;
        private String passportNumber;
        private String surName;
        private String postNames;
        private String fatherName;
        private String motherName;
        private String sex;  // MALE or
        private String dateOfBirth; // dd/MM/yyyy
        private String placeOfBirth;
        private String countryOfBirth;
        private String birthCountry;
        private String villageId;
        private String domicileCountry;
        private String domicileDistrict;
        private String domicileProvince;
        private String domicileSector;
        private String domicileCell;
        private String domicileVillage;
        private String civilStatus; // S
        private String maritalStatus;  // SINGLE
        private String spouse;
        private String photo; // Path to file
        private String citizenStatus;
        private String nationality;
        private String applicantType;
    }

}