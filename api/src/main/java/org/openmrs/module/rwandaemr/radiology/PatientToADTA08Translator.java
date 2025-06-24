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

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.v23.message.ADT_A08;
import ca.uhn.hl7v2.model.v23.segment.EVN;
import ca.uhn.hl7v2.model.v23.segment.PID;
import ca.uhn.hl7v2.parser.Parser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.formatDatetime;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.populateMshSegment;

@Component
public class PatientToADTA08Translator extends BaseHL7Translator {

    private final Log log = LogFactory.getLog(getClass());

    public PatientToADTA08Translator(
            @Autowired AdtService adtService,
            @Autowired ConceptService conceptService,
            @Autowired RwandaEmrConfig rwandaEmrConfig) {
        super(adtService, conceptService, rwandaEmrConfig);
    }

    /**
     * For the given patient, generate an ADT^A08 HL7 message
     * Used for patient demographics updates of specific fields
     */
    public String toADT_A08(Patient patient) throws HL7Exception {
        ADT_A08 message = new ADT_A08();
        Date now = new Date();

        // MSH Segment
        populateMshSegment(message.getMSH(), "HIS_FACILITY", now, "ADT", "A08");

        // EVN Segment  -  EVN|A08|20130614131415
        Date changeDate = patient.getPersonDateChanged() == null ? patient.getPersonDateCreated() : patient.getPersonDateChanged();
        EVN evn = message.getEVN();
        evn.getEvn1_EventTypeCode().setValue("A08");
        evn.getEvn2_RecordedDateTime().getTimeOfAnEvent().setValue(formatDatetime(changeDate == null ? now : changeDate));

        // PID Segment  -  PID|1||PatientID||PatientLast^First^Middle^^Title||20000514|F
        PID pid = message.getPID();

        setPatientIdentifier(pid, patient);
        setPatientName(pid, patient);
        setPatientBirthdate(pid, patient);
        setPatientGender(pid, patient);

        HapiContext context = new DefaultHapiContext();
        Parser parser = context.getPipeParser();
        return parser.encode(message);
    }
}
