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
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import ca.uhn.hl7v2.model.v23.segment.OBR;
import ca.uhn.hl7v2.model.v23.segment.OBX;
import ca.uhn.hl7v2.parser.Parser;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterRole;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Provider;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class ORUToObsTranslator extends BaseHL7Translator {

    private final Log log = LogFactory.getLog(getClass());

    final OrderService orderService;
    final RwandaEmrService rwandaEmrService;

    public ORUToObsTranslator(
            @Autowired AdtService adtService,
            @Autowired ConceptService conceptService,
            @Autowired RwandaEmrConfig rwandaEmrConfig,
            @Autowired OrderService orderService,
            @Autowired RwandaEmrService rwandaEmrService) {
        super(adtService, conceptService, rwandaEmrConfig);
        this.orderService = orderService;
        this.rwandaEmrService = rwandaEmrService;
    }

    public void fromORU_R01(String hl7MessageString) throws HL7Exception {
        HapiContext context = new DefaultHapiContext();
        context.getParserConfiguration().setValidating(false);
        Parser parser = context.getPipeParser();
        Message message = parser.parse(hl7MessageString);
        ORU_R01 oruR01 = (ORU_R01) message;
        OBR obr = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBR();

        // TODO: Do we want to create orders if not found, or fail?
        String orderNumber = obr.getObr2_PlacerOrderNumber(0).getEntityIdentifier().getValue();
        Order order = orderService.getOrderByOrderNumber(orderNumber);
        if (order == null) {
            throw new IllegalArgumentException("Order with order number " + orderNumber + " not found");
        }

        OBX obx1 = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBSERVATION(0).getOBX();
        OBX obx2 = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBSERVATION(1).getOBX();

        // Currently only text reports are expected, which are identified by format = TX
        String reportFormat = obx1.getObx2_ValueType().getValue();
        if (StringUtils.isNotBlank(reportFormat) && !"TX".equals(reportFormat)) {
            throw new IllegalArgumentException("Report format of TX expected, but '" + reportFormat + "' was received");
        }

        // Fields for both study and report
        String status = obx1.getObx11_ObservResultStatus().getValue();  // I=COMPLETED, R=READ, F=FINAL, P=PRINT
        String testIdentifier = obx1.getObx3_ObservationIdentifier().getIdentifier().getValue();
        Concept testPerformedValue = conceptService.getConceptByReference("LOINC:" + testIdentifier);
        if (testPerformedValue == null) {
            throw new IllegalArgumentException("No procedure found with LOINC code = " + testIdentifier);
        }

        // Study
        String dateTimeStudyPerformed = getField(hl7MessageString, "OBX", 19); // TODO: This is available in HL7 v2.5 only
        String dateTimeStudyReceivedOnPacs = obx1.getObx12_DateLastObsNormalValues().getTimeOfAnEvent().getValue();
        String imageViewerLink = obx2.getObx5_ObservationValue(0).getData().encode();

        // Report
        String reportText = obx1.getObx5_ObservationValue(0).getData().encode();
        String reportDateStr = obx1.getObx14_DateTimeOfTheObservation().getTimeOfAnEvent().getValue();
        String radiologistId = obx1.getObx16_ResponsibleObserver().getIDNumber().getValue();
        String radiologistGivenName = obx1.getObx16_ResponsibleObserver().getGivenName().getValue();
        String radiologistFamilyName = obx1.getObx16_ResponsibleObserver().getFamilyName().getValue();
        // TODO: Review whether we should use provider name at all, and whether we will have provider id here
        Provider radiologist = (StringUtils.isBlank(radiologistId) ? null : rwandaEmrConfig.getProviderByIdentifier(radiologistId));

        // Study date and report date must both be non-null.  Determine the best dates for each
        Date studyDate = null;
        Date dateStudyReceivedOnPacs = null;
        Date reportDate = null;
        if (StringUtils.isNotBlank(dateTimeStudyPerformed)) {
            studyDate = parseDatetime(dateTimeStudyPerformed);
        }
        if (StringUtils.isNotBlank(dateTimeStudyReceivedOnPacs)) {
            dateStudyReceivedOnPacs = parseDatetime(dateTimeStudyReceivedOnPacs);
        }
        if (StringUtils.isNotBlank(reportDateStr)) {
            reportDate = parseDatetime(reportDateStr);
        }
        if (studyDate == null && dateStudyReceivedOnPacs != null) {
            studyDate = dateStudyReceivedOnPacs;
        }
        if (studyDate == null && reportDate != null) {
            studyDate = reportDate;
        }
        if (studyDate == null && StringUtils.isNotBlank(imageViewerLink)) {
            studyDate = order.getEncounter().getEncounterDatetime();
        }
        if (reportDate == null && StringUtils.isNotBlank(reportText)) {
            reportDate = studyDate != null ? studyDate : order.getEncounter().getEncounterDatetime();
        }

        List<Encounter> encounters = new ArrayList<>();
        if (studyDate != null) {
            encounters.add(createOrUpdateRadiologyStudy(order, studyDate, testPerformedValue, imageViewerLink));
        }
        if (reportDate != null) {
            encounters.add(createOrUpdateRadiologyReport(order, reportDate, testPerformedValue, status, radiologist, reportText));
        }

        if (encounters.isEmpty()) {
            log.warn("No radiology study or report data to create or update for order number: " + orderNumber);
        }
        else {
            rwandaEmrService.saveEncounters(encounters);
        }
    }

    /**
     * @return the Radiology Study Encounter, updated with the given values
     */
    protected Encounter createOrUpdateRadiologyStudy(Order order, Date studyDate, Concept testPerformedValue, String imageViewerLink) {
        RadiologyConfig radiologyConfig = rwandaEmrConfig.getRadiologyConfig();;

        // Record an encounter representing the study, if not already present, or update existing one
        Encounter encounter = null;
        Obs radiologyStudyGroup = null;
        List<Obs> obsForOrder = rwandaEmrService.getObsByOrder(order);
        for (Obs obs : obsForOrder) {
            if (obs.getConcept().equals(radiologyConfig.getRadiologyStudyConstruct())) {
                radiologyStudyGroup = obs;
                encounter = radiologyStudyGroup.getEncounter();
            }
        }
        if (encounter == null) {
            encounter = new Encounter();
            encounter.setPatient(order.getPatient());
            encounter.setEncounterType(radiologyConfig.getRadiologyStudyEncounterType());
            encounter.setEncounterDatetime(studyDate);
            encounter.setLocation(rwandaEmrConfig.getUnknownLocation()); // TODO: What location to use here?
            addEncounterProvider(encounter, radiologyConfig.getRadiologyTechnicianEncounterRole(), null); // TODO: What provider to use here?
        }
        else {
            if (studyDate != null && !studyDate.equals(encounter.getEncounterDatetime())) {
                encounter.setEncounterDatetime(studyDate);
            }
        }

        // Create Radiology Study Obs Group if not present
        if (radiologyStudyGroup == null) {
            radiologyStudyGroup = new Obs();
            radiologyStudyGroup.setOrder(order);
            radiologyStudyGroup.setConcept(radiologyConfig.getRadiologyStudyConstruct());
            encounter.addObs(radiologyStudyGroup);
        }

        // Add or modify existing accession number obs in radiology study obs group
        Obs accessionNumberObs = getObsFromGroup(radiologyStudyGroup, radiologyConfig.getRadiologyAccessionNumber());
        if (accessionNumberObs != null) {
            if (!order.getOrderNumber().equals(accessionNumberObs.getValueText())) {
                accessionNumberObs.setValueText(order.getOrderNumber());
            }
        }
        else {
            accessionNumberObs = new Obs();
            accessionNumberObs.setOrder(order);
            accessionNumberObs.setConcept(radiologyConfig.getRadiologyAccessionNumber());
            accessionNumberObs.setValueText(order.getOrderNumber());
            radiologyStudyGroup.addGroupMember(accessionNumberObs);
        }

        // Add or modify existing procedure obs in radiology study obs group
        Obs procedureObs = getObsFromGroup(radiologyStudyGroup, radiologyConfig.getRadiologyProcedurePerformed());
        if (procedureObs != null) {
            if (!testPerformedValue.equals(procedureObs.getValueCoded())) {
                procedureObs.setValueCoded(testPerformedValue);
            }
        }
        else {
            procedureObs = new Obs();
            procedureObs.setOrder(order);
            procedureObs.setConcept(radiologyConfig.getRadiologyProcedurePerformed());
            procedureObs.setValueCoded(testPerformedValue);
            radiologyStudyGroup.addGroupMember(procedureObs);
        }

        // Add or modify existing images available obs in radiology study obs group
        boolean hasImages = StringUtils.isNotBlank(imageViewerLink);
        Concept hasImagesValueCoded = hasImages ? conceptService.getTrueConcept() : conceptService.getFalseConcept();
        Obs hasImagesAvailableObs = getObsFromGroup(radiologyStudyGroup, radiologyConfig.getRadiologyImagesAvailable());
        if (hasImagesAvailableObs != null) {
            Concept existingValue = hasImagesAvailableObs.getValueCoded();
            if (existingValue == null || !existingValue.equals(hasImagesValueCoded)) {
                hasImagesAvailableObs.setValueCoded(hasImagesValueCoded);
            }
            if (!OpenmrsUtil.nullSafeEquals(hasImagesAvailableObs.getComment(), imageViewerLink)) {
                hasImagesAvailableObs.setComment(imageViewerLink);
            }
        }
        else {
            hasImagesAvailableObs = new Obs();
            hasImagesAvailableObs.setOrder(order);
            hasImagesAvailableObs.setConcept(radiologyConfig.getRadiologyImagesAvailable());
            hasImagesAvailableObs.setValueCoded(hasImagesValueCoded);
            hasImagesAvailableObs.setComment(imageViewerLink);
            radiologyStudyGroup.addGroupMember(hasImagesAvailableObs);
        }

        return encounter;
    }

    /**
     * @return the Radiology Report Encounter, updated with the given values
     */
    protected Encounter createOrUpdateRadiologyReport(Order order, Date reportDate, Concept testPerformedValue, String status, Provider radiologist, String reportText) {
        RadiologyConfig radiologyConfig = rwandaEmrConfig.getRadiologyConfig();;

        // Record an encounter representing the report, if not already present, or update existing one
        Encounter encounter = null;
        Obs radiologyReportGroup = null;
        List<Obs> obsForOrder = rwandaEmrService.getObsByOrder(order);
        for (Obs obs : obsForOrder) {
            if (obs.getConcept().equals(radiologyConfig.getRadiologyReportConstruct())) {
                radiologyReportGroup = obs;
                encounter = radiologyReportGroup.getEncounter();
            }
        }
        radiologist = (radiologist == null ? rwandaEmrConfig.getUnknownProvider() : radiologist);
        if (encounter == null) {
            encounter = new Encounter();
            encounter.setPatient(order.getPatient());
            encounter.setEncounterType(radiologyConfig.getRadiologyReportEncounterType());
            encounter.setEncounterDatetime(reportDate);
            encounter.setLocation(rwandaEmrConfig.getUnknownLocation()); // TODO: What location to use here?
            addEncounterProvider(encounter, radiologyConfig.getPrincipalResultsInterpreterEncounterRole(), radiologist);
        }
        else {
            if (reportDate != null && !reportDate.equals(encounter.getEncounterDatetime())) {
                encounter.setEncounterDatetime(reportDate);
            }
            if (!radiologist.equals(rwandaEmrConfig.getUnknownProvider())) {
                addEncounterProvider(encounter, radiologyConfig.getPrincipalResultsInterpreterEncounterRole(), radiologist);
            }
        }

        // Create Radiology Report Obs Group if not present
        if (radiologyReportGroup == null) {
            radiologyReportGroup = new Obs();
            radiologyReportGroup.setOrder(order);
            radiologyReportGroup.setConcept(radiologyConfig.getRadiologyReportConstruct());
            encounter.addObs(radiologyReportGroup);
        }

        // Add or modify existing accession number obs in radiology report obs group
        Obs accessionNumberObs = getObsFromGroup(radiologyReportGroup, radiologyConfig.getRadiologyAccessionNumber());
        if (accessionNumberObs != null) {
            if (!order.getOrderNumber().equals(accessionNumberObs.getValueText())) {
                accessionNumberObs.setValueText(order.getOrderNumber());
            }
        }
        else {
            accessionNumberObs = new Obs();
            accessionNumberObs.setOrder(order);
            accessionNumberObs.setConcept(radiologyConfig.getRadiologyAccessionNumber());
            accessionNumberObs.setValueText(order.getOrderNumber());
            radiologyReportGroup.addGroupMember(accessionNumberObs);
        }

        // Add or modify existing procedure obs in radiology report obs group
        Obs procedureObs = getObsFromGroup(radiologyReportGroup, radiologyConfig.getRadiologyProcedurePerformed());
        if (procedureObs != null) {
            if (!testPerformedValue.equals(procedureObs.getValueCoded())) {
                procedureObs.setValueCoded(testPerformedValue);
            }
        }
        else {
            procedureObs = new Obs();
            procedureObs.setOrder(order);
            procedureObs.setConcept(radiologyConfig.getRadiologyProcedurePerformed());
            procedureObs.setValueCoded(testPerformedValue);
            radiologyReportGroup.addGroupMember(procedureObs);
        }

        // Add or modify existing comment obs in radiology report obs group
        boolean commentsChanged = false;
        Obs commentsObs = getObsFromGroup(radiologyReportGroup, radiologyConfig.getRadiologyReportComments());
        if (commentsObs != null) {
            if (!reportText.equals(commentsObs.getValueText())) {
                commentsChanged = StringUtils.isNotBlank(commentsObs.getComment());
                commentsObs.setValueText(reportText);
            }
        }
        else {
            commentsObs = new Obs();
            commentsObs.setOrder(order);
            commentsObs.setConcept(radiologyConfig.getRadiologyReportComments());
            commentsObs.setValueText(reportText);
            radiologyReportGroup.addGroupMember(commentsObs);
        }

        // Get status concept  I=COMPLETED, R=READ, F=FINAL, P=PRINT
        Concept statusConcept = null;
        if ("R".equalsIgnoreCase(status)) {
            statusConcept = radiologyConfig.getPreliminaryStatusConcept();
        }
        else if ("F".equalsIgnoreCase(status) || "P".equalsIgnoreCase(status)) {
            statusConcept = commentsChanged ? radiologyConfig.getCorrectionStatusConcept() : radiologyConfig.getFinalStatusConcept();
        }

        // Add or modify existing status obs in radiology report obs group
        Obs statusObs = getObsFromGroup(radiologyReportGroup, radiologyConfig.getRadiologyReportType());
        if (statusObs != null) {
            if (statusConcept == null) {
                statusObs.setVoided(true);
                statusObs.setVoidReason("Updated status from incoming HL7");
            }
            else {
                if (!statusObs.getValueCoded().equals(statusConcept)) {
                    statusObs.setValueCoded(statusConcept);
                }
            }
        }
        else {
            if (statusConcept != null) {
                statusObs = new Obs();
                statusObs.setOrder(order);
                statusObs.setConcept(radiologyConfig.getRadiologyReportType());
                statusObs.setValueCoded(statusConcept);
                radiologyReportGroup.addGroupMember(statusObs);
            }
        }

        return encounter;
    }


    private Obs getObsFromGroup(Obs obsGroup, Concept concept) {
        if (obsGroup != null && obsGroup.hasGroupMembers()) {
            for (Obs o : obsGroup.getGroupMembers()) {
                if (o.getConcept().equals(concept)) {
                    return o;
                }
            }
        }
        return null;
    }

    private void addEncounterProvider(Encounter encounter, EncounterRole role, Provider provider) {
        if (provider == null) {
            provider = rwandaEmrConfig.getUnknownProvider();
        }
        for (EncounterProvider ep : encounter.getEncounterProviders()) {
            if (ep.getEncounterRole().equals(role) && ep.getProvider().equals(provider) && !ep.getVoided()) {
                return;
            }
        }
        EncounterProvider ep = new EncounterProvider();
        ep.setEncounter(encounter);
        ep.setEncounterRole(role);
        ep.setProvider(provider);
        encounter.getEncounterProviders().add(ep);
    }
}
