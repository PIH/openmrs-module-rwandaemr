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
import ca.uhn.hl7v2.model.v23.message.ORM_O01;
import ca.uhn.hl7v2.model.v23.segment.OBR;
import ca.uhn.hl7v2.model.v23.segment.ORC;
import ca.uhn.hl7v2.model.v23.segment.PID;
import ca.uhn.hl7v2.model.v23.segment.PV1;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
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
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.formatDatetime;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.populateMshSegment;
import static org.openmrs.module.rwandaemr.radiology.HL7Utils.trim;

@Component
public class OrderToORMO01Translator extends BaseHL7Translator {

    private final Log log = LogFactory.getLog(getClass());

    public OrderToORMO01Translator(
            @Autowired AdtService adtService,
            @Autowired ConceptService conceptService,
            @Autowired RwandaEmrConfig rwandaEmrConfig) {
        super(adtService, conceptService, rwandaEmrConfig);
    }

    /**
     * @param order
     * @return true if the given order is a supported radiology order, false otherwise
     */
    public boolean isRadiologyOrder(Order order) {
        return getModalityCode(order) != null;
    }

    /**
     * The RIS takes several tests as one study when the procedure code carries this marker after the first test's code
     */
    public static final String COMBINED_PROCEDURE_MARKER = "-Combine";

    /**
     * For the given test order, generate an ORM^001 HL7 message
     * Used for a new unscheduled order creation or order cancellation
     */
    public ORM_O01 toORM_O01(TestOrder order) throws HL7Exception {
        String orderControl = isCancelled(order) ? "CA" : "NW";
        return toORM_O01(Collections.singletonList(order), order.getOrderNumber(), orderControl);
    }

    /**
     * Generate an ORM^O01 HL7 message for one study containing the given test orders, all for the same patient.
     * The RIS only accepts one OBR segment, so several tests are combined into it the way the RIS expects:
     * the procedure code is the first test's code followed by {@link #COMBINED_PROCEDURE_MARKER}, and the procedure name
     * lists the names of all the tests separated by "_".  The accession number identifies the study and is sent as the
     * placer order number, so results come back with it.  A single test is sent exactly as before.
     * @param orderControl NW to register the study, CA to cancel it
     */
    public ORM_O01 toORM_O01(List<TestOrder> orders, String accessionNumber, String orderControl) throws HL7Exception {
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("At least one order is required to generate an ORM^O01 message");
        }
        TestOrder order = orders.get(0);
        for (TestOrder o : orders) {
            if (!o.getPatient().equals(order.getPatient())) {
                throw new IllegalArgumentException("All orders in an ORM^O01 message must be for the same patient");
            }
        }
        boolean combined = orders.size() > 1;

        ORM_O01 message = new ORM_O01();
        Date now = new Date();

        // The study is scheduled for the earliest of the tests
        Date scheduledDate = null;
        for (TestOrder o : orders) {
            Date orderDate = o.getScheduledDate() != null ? o.getScheduledDate() : o.getDateActivated();
            if (scheduledDate == null || (orderDate != null && orderDate.before(scheduledDate))) {
                scheduledDate = orderDate;
            }
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
        // TODO: Second note, commenting these out entirely for now, as HAPI validation is enforcing a US phone number format
        //pid.getPhoneNumberHome(0).getXtn1_9999999X99999CAnyText().setValue(getPhoneNumber(patient));
        //pid.getPhoneNumberHome(0).getXtn2_TelecommunicationUseCode().setValue(""); // TODO: We don't collect this data
        String clinicalHistory = combined ? joinDistinct(orders, TestOrder::getClinicalHistory, "; ") : order.getClinicalHistory();
        pid.getPhoneNumberHome(0).getXtn3_TelecommunicationEquipmentType().setValue(trim(clinicalHistory, 1000));
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
        orc.getOrderControl().setValue(orderControl);

        // OBR Segment
        OBR obr = message.getORDER().getORDER_DETAIL().getOBR();
        obr.getSetIDObservationRequest().setValue("1");
        obr.getPlacerOrderNumber(0).getEntityIdentifier().setValue(accessionNumber);
        // TODO: Note, this seems like the wrong field, but it is what is in the integration docs
        obr.getFillerOrderNumber().getEntityIdentifier().setValue(getOrderLocation(order).getName());
        if (combined) {
            obr.getUniversalServiceIdentifier().getIdentifier().setValue(getProcedureCode(order) + COMBINED_PROCEDURE_MARKER);
            List<String> names = new ArrayList<>();
            for (TestOrder o : orders) {
                names.add(o.getConcept().getFullySpecifiedName(Locale.ENGLISH).getName());
            }
            obr.getUniversalServiceIdentifier().getText().setValue(trim(StringUtils.join(names, "_"), 200));
        }
        else {
            obr.getUniversalServiceIdentifier().getIdentifier().setValue(getProcedureCode(order));
            obr.getUniversalServiceIdentifier().getText().setValue(trim(order.getConcept().getFullySpecifiedName(Locale.ENGLISH).getName(), 64));
        }
        String modality = getModalityCode(order);
        obr.getDiagnosticServiceSectionID().setValue(modality);

        // Scheduled order information
        obr.getFillerField2().setValue(modality); // This is the AE Title of the Equipment.  Sending modality in the absence of specific equipment code.
        obr.getObr36_ScheduledDateTime().getTimeOfAnEvent().setValue(formatDatetime(scheduledDate));
        boolean stat = false;
        for (TestOrder o : orders) {
            stat = stat || o.getUrgency() == Order.Urgency.STAT;
        }
        obr.getPriority().setValue(stat ? "STAT" : "ROUTINE");
        String orderReason = joinDistinct(orders, o -> o.getOrderReason() != null ?
                o.getOrderReason().getDisplayString() : o.getOrderReasonNonCoded(), "; ");
        if (orderReason != null) {
            obr.getReasonForStudy(0).getText().setValue(trim(orderReason, 64));
        }
        return message;
    }

    /**
     * @return true if the order has been voided or stopped, in which case a cancellation (CA) is sent to PACS
     */
    public boolean isCancelled(Order order) {
        return BooleanUtils.isTrue(order.getVoided()) || order.getDateStopped() != null;
    }

    private String joinDistinct(List<TestOrder> orders, Function<TestOrder, String> property, String separator) {
        Set<String> values = new LinkedHashSet<>();
        for (TestOrder o : orders) {
            String value = property.apply(o);
            if (StringUtils.isNotBlank(value)) {
                values.add(value.trim());
            }
        }
        return values.isEmpty() ? null : StringUtils.join(values, separator);
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

    public String getModalityCode(Order order) {
        Map<Modality, Concept> modalities = rwandaEmrConfig.getRadiologyConfig().getModalityConceptSets();
        Map<Concept, Modality> orderables = rwandaEmrConfig.getRadiologyConfig().getOrderables(modalities);
        Modality radiologyModality = orderables.get(order.getConcept());
        return radiologyModality == null ? null : radiologyModality.name();
    }
}
