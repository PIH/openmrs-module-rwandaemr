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
package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v23.segment.MSH;
import ca.uhn.hl7v2.model.v23.segment.PID;
import liquibase.pro.packaged.L;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public abstract class BaseHL7Translator {

    final AdtService adtService;
    final ConceptService conceptService;
    final RwandaEmrConfig rwandaEmrConfig;

    public BaseHL7Translator(
            @Autowired AdtService adtService,
            @Autowired ConceptService conceptService,
            @Autowired RwandaEmrConfig rwandaEmrConfig) {
        this.adtService = adtService;
        this.conceptService = conceptService;
        this.rwandaEmrConfig = rwandaEmrConfig;
    }

    String formatDate(Date date) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(date);
    }

    Date parseDate(String yyyyMMdd) {
        try {
            return new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).parse(yyyyMMdd);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not parse date using format yyyyMMdd: " + yyyyMMdd);
        }
    }

    String formatDatetime(Date date) {
        return new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).format(date);
    }

    Date parseDatetime(String yyyyMMddHHmmss) {
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH).parse(yyyyMMddHHmmss);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Could not parse datetime using format yyyyMMddHHmmss: " + yyyyMMddHHmmss);
        }
    }

    public static String trim(String value, int maxLength) {
        return (value == null ? null : StringUtils.substring(value.trim(), 0, maxLength));
    }

    void populateMshSegment(MSH msh, String sendingFacility, Date messageDate, String messageType, String triggerEvent) throws HL7Exception {
        msh.getFieldSeparator().setValue("|");
        msh.getEncodingCharacters().setValue("^~\\&");
        msh.getSendingApplication().getNamespaceID().setValue("OpenMRS");
        msh.getSendingFacility().getNamespaceID().setValue(sendingFacility);
        // Hard-coding PACS_APP and PACS_FACILITY per instruction from vendor
        msh.getReceivingApplication().getNamespaceID().setValue("PACS_APP");
        msh.getReceivingFacility().getNamespaceID().setValue("PACS_FACILITY");
        msh.getDateTimeOfMessage().getTimeOfAnEvent().setValue(formatDatetime(messageDate));
        msh.getMessageType().getMessageType().setValue(messageType);
        msh.getMessageType().getTriggerEvent().setValue(triggerEvent);
        msh.getMessageControlID().setValue(UUID.randomUUID().toString());
        msh.getProcessingID().getProcessingID().setValue("P"); // P=Production, D=Debugging, T=Testing
        msh.getVersionID().setValue("2.3"); // HL7 version targeted
    }

    /**
     * @return the primary care identifier for the patient with any "-" character removed
     */
    public String getPatientIdentifier(Patient patient) {
        PatientIdentifier patientIdentifier = patient.getPatientIdentifier(rwandaEmrConfig.getPrimaryCareIdentifierType());
        return patientIdentifier == null ? null : patientIdentifier.getIdentifier().replace("-", "");
    }

    /**
     * Patient Identifier should be trimmed
     */
    public void setPatientIdentifier(PID pid, Patient patient) throws HL7Exception {
        pid.getSetIDPatientID().setValue("1");
        String patientIdentifier = getPatientIdentifier(patient);
        if (StringUtils.isBlank(patientIdentifier)) {
            throw new IllegalStateException("Patient does not have a Primary Care ID");
        }
        pid.getPatientIDInternalID(0).getID().setValue(patientIdentifier);
    }

    /**
     * Patient Name has a maximum length of 64 in the RIS.  Trim first and last to 30 to ensure it fits.
     */
    public void setPatientName(PID pid, Patient patient) throws HL7Exception {
        pid.getPatientName(0).getFamilyName().setValue(trim(patient.getFamilyName(), 30));
        pid.getPatientName(0).getGivenName().setValue(trim(patient.getGivenName(), 30));
    }

    public void setPatientBirthdate(PID pid, Patient patient) throws HL7Exception {
        if (patient.getBirthdate() != null) {
            pid.getDateOfBirth().getTimeOfAnEvent().setValue(formatDate(patient.getBirthdate()));
        }
    }

    public void setPatientGender(PID pid, Patient patient) throws HL7Exception {
        pid.getSex().setValue(patient.getGender() == null ? "O" : patient.getGender().toUpperCase());
    }

    /**
     * This can be used as an alternative to HAPI FHIR API to retrieve a particular field segement
     */
    public String getField(String message, String segment, int fieldNumber) {
        for (String line : message.split("[\\r\\n]")) {
            String[] components = line.split("\\|");
            if (components[0].equals(segment)) {
                if (components.length >= (fieldNumber + 1)) {
                    return components[fieldNumber];
                }
                else {
                    return null;
                }
            }
        }
        return null;
    }
}
