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
import ca.uhn.hl7v2.model.v23.segment.PID;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.formatDate;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.trim;

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
}
