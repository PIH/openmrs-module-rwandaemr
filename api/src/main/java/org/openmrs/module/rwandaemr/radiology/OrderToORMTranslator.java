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
import ca.uhn.hl7v2.model.v23.message.ORM_O01;
import ca.uhn.hl7v2.model.v23.segment.OBR;
import ca.uhn.hl7v2.model.v23.segment.ORC;
import ca.uhn.hl7v2.model.v23.segment.PID;
import ca.uhn.hl7v2.model.v23.segment.PV1;
import ca.uhn.hl7v2.parser.Parser;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptSource;
import org.openmrs.Location;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.Provider;
import org.openmrs.TestOrder;
import org.openmrs.api.ConceptService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.formatDatetime;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.populateMshSegment;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.trim;

@Component
public class OrderToORMTranslator extends BaseHL7Translator {

    private final Log log = LogFactory.getLog(getClass());

    public OrderToORMTranslator(
            @Autowired AdtService adtService,
            @Autowired ConceptService conceptService,
            @Autowired RwandaEmrConfig rwandaEmrConfig) {
        super(adtService, conceptService, rwandaEmrConfig);
    }

    /**
     * For the given test order, generate an ORM^001 HL7 message
     * Used for a new unscheduled order creation or order cancellation
     */
    public String toORM_O01(TestOrder order) throws HL7Exception {
        ORM_O01 message = new ORM_O01();
        Date now = new Date();

        Date scheduledDate = order.getScheduledDate();
        if (scheduledDate == null) {
            scheduledDate = order.getDateActivated();
        }

        // MSH Segment
        populateMshSegment(message.getMSH(), getOrderLocation(order).getName(), now, "ORM", "O01");

        // PID Segment
        Patient patient = order.getPatient();
        PID pid = message.getPATIENT().getPID();
        setPatientIdentifier(pid, patient);
        setPatientName(pid, patient);
        setPatientBirthdate(pid, patient);
        setPatientGender(pid, patient);

        // TODO: Note, these seems like the wrong fields, but it is what is in the integration docs
        pid.getPhoneNumberHome(0).getXtn1_9999999X99999CAnyText().setValue(getPhoneNumber(patient));
        pid.getPhoneNumberHome(0).getXtn2_TelecommunicationUseCode().setValue(""); // TODO: We don't collect this data
        pid.getPhoneNumberHome(0).getXtn3_TelecommunicationEquipmentType().setValue(trim(order.getClinicalHistory(), 1000));
        // TODO: Admission ID is supported to send to PACS.  Determine if there is a value for this in OpenMRS.

        // PV1 Segment
        PV1 pv1 = message.getPATIENT().getPATIENT_VISIT().getPV1();
        pv1.getSetIDPatientVisit().setValue("1");
        // TODO: Patient Weight in Kgs is supported.  Do we care to send this?  If so, which weight to send?  Max length 3 in format "nnn"
        pv1.getPatientType().setValue(getPatientType(order));
        Provider provider = order.getOrderer();
        if (provider != null) {
            pv1.getReferringDoctor(0).getIDNumber().setValue(provider.getIdentifier());
            // Referring Doctor Name has a maximum length of 64 in the RIS.  Trim first and last to 30 to ensure it fits.
            pv1.getReferringDoctor(0).getFamilyName().setValue(trim(provider.getPerson().getFamilyName(), 30));
            pv1.getReferringDoctor(0).getGivenName().setValue(trim(provider.getPerson().getGivenName(), 30));
        }

        // ORC Segment
        ORC orc = message.getORDER().getORC();
        boolean isCancelled = BooleanUtils.isTrue(order.getVoided()) || order.getDateStopped() != null;
        orc.getOrderControl().setValue(isCancelled ? "CA" : "NW");

        // OBR Segment
        OBR obr = message.getORDER().getORDER_DETAIL().getOBR();
        obr.getSetIDObservationRequest().setValue("1");
        obr.getPlacerOrderNumber(0).getEntityIdentifier().setValue(order.getOrderNumber());
        // TODO: Note, this seems like the wrong field, but it is what is in the integration docs
        obr.getFillerOrderNumber().getEntityIdentifier().setValue(getOrderLocation(order).getName());
        obr.getUniversalServiceIdentifier().getIdentifier().setValue(getProcedureCode(order));
        obr.getUniversalServiceIdentifier().getText().setValue(trim(order.getConcept().getFullySpecifiedName(Locale.ENGLISH).getName(), 64));
        String modality = getModalityCode(order);
        obr.getDiagnosticServiceSectionID().setValue(modality);

        // Scheduled order information
        obr.getFillerField2().setValue(modality); // This is the AE Title of the Equipment.  Sending modality in the absence of specific equipment code.
        obr.getObr36_ScheduledDateTime().getTimeOfAnEvent().setValue(formatDatetime(scheduledDate));
        obr.getPriority().setValue(order.getUrgency() == Order.Urgency.STAT ? "STAT" : "ROUTINE");
        String orderReason = order.getOrderReasonNonCoded();
        if (order.getOrderReason() != null) {
            orderReason = order.getOrderReason().getDisplayString();
        }
        if (orderReason != null) {
            obr.getReasonForStudy(0).getText().setValue(trim(orderReason, 64));
        }

        HapiContext context = new DefaultHapiContext();
        Parser parser = context.getPipeParser();
        return parser.encode(message);
    }

    public Location getOrderLocation(TestOrder testOrder) {
        Location orderLocation = testOrder.getEncounter().getLocation();
        try {
            Location visitLocation = adtService.getLocationThatSupportsVisits(orderLocation);
            if (visitLocation != null) {
                return visitLocation;
            } else {
                log.debug("No visit location associated with " + orderLocation);
            }
        } catch (Exception e) {
            log.debug("Unable to retrieve a visit location for location: " + orderLocation);
        }
        return orderLocation;
    }

    public String getPhoneNumber(Patient patient) {
        PersonAttribute phoneNumber = patient.getAttribute(rwandaEmrConfig.getTelephoneNumber());
        return (phoneNumber == null ? "" : phoneNumber.getValue());
    }

    // Supported values are:  OP = Outpatient,IP = Inpatient, ER = Emergency
    public String getPatientType(Order order) {
        CareSetting careSetting = order.getCareSetting();
        if (careSetting != null) {
            CareSetting.CareSettingType careSettingType = careSetting.getCareSettingType();
            if (careSettingType == CareSetting.CareSettingType.INPATIENT) {
                return "IP";
            }
            else if (careSettingType == CareSetting.CareSettingType.OUTPATIENT) {
                return "OP";
            }
        }
        return "";
    }

    private String getProcedureCode(Order order) {
        List<String> codes = new ArrayList<>();
        ConceptSource conceptSource = conceptService.getConceptSourceByName("LOINC");
        ConceptMapType conceptMapType = conceptService.getConceptMapTypeByName("SAME-AS");
        if (conceptSource != null && conceptMapType != null) {
            for (ConceptMap cm : order.getConcept().getConceptMappings()) {
                if (cm.getConceptReferenceTerm().getConceptSource().equals(conceptSource)) {
                    if (cm.getConceptMapType().equals(conceptMapType)) {
                        codes.add(cm.getConceptReferenceTerm().getCode());
                    }
                }
            }
        }
        if (codes.isEmpty()) {
            throw new RuntimeException("No valid procedure code found for concept " + order.getConcept().getDisplayString());
        }
        return codes.get(0);
    }

    private String getModalityCode(Order order) {
        Map<Modality, Concept> modalities = rwandaEmrConfig.getRadiologyConfig().getModalityConceptSets();
        Map<Concept, Modality> orderables = rwandaEmrConfig.getRadiologyConfig().getOrderables(modalities);
        Modality radiologyModality = orderables.get(order.getConcept());
        return radiologyModality == null ? null : radiologyModality.name();
    }
}
