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
package org.openmrs.module.rwandaemr.radiology.mock;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.app.Connection;
import ca.uhn.hl7v2.app.Initiator;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v23.message.ORM_O01;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import ca.uhn.hl7v2.model.v23.segment.OBR;
import ca.uhn.hl7v2.model.v23.segment.OBX;
import ca.uhn.hl7v2.model.v23.segment.ORC;
import ca.uhn.hl7v2.model.v23.segment.PID;
import ca.uhn.hl7v2.model.v23.segment.PV1;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.radiology.HL7Utils;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.populateMshSegment;

@Component
public class MockPacsSystem implements Application {

    private static final Log log = LogFactory.getLog(MockPacsSystem.class);

    public MockPacsSystem() {
    }

    @Override
    public Message processMessage(Message message) {
        try {
            log.warn("MockPacs:  Processing incoming message");
            ORM_O01 ormO01 = (ORM_O01) message;
            ResponseThread responseThread = new ResponseThread(ormO01);
            responseThread.run();
            log.warn("MockPacs:  Generating success ACK");
            return HL7Utils.generateAckMessage(message,null);
        }
        catch (Exception e) {
            log.error("MockPacs:  Generating failure ACK", e);
            return HL7Utils.generateAckMessage(message, e);
        }
    }

    @Override
    public boolean canProcess(Message message) {
        return  message != null && "ORM_O01".equals(message.getName());
    }

    static class ResponseThread implements Runnable {

        private final ORM_O01 ormO01;

        public ResponseThread(ORM_O01 ormO01) {
            this.ormO01 = ormO01;
        }

        @Override
        public void run() {
            try {
                log.warn("Sleeping for 5 seconds before sending first response message");
                Thread.sleep(5000);
                ORU_R01 oruR01 = new ORU_R01();
                Date now = new Date();

                // MSH Segment
                populateMshSegment(oruR01.getMSH(), "Butaro", now, "ORU", "R01");

                // PID Segment
                PID incomingPID = ormO01.getPATIENT().getPID();
                PID pid = oruR01.getRESPONSE().getPATIENT().getPID();
                pid.getSetIDPatientID().setValue(incomingPID.getSetIDPatientID().getValue());
                pid.getPatientIDInternalID(0).getID().setValue(incomingPID.getPatientIDInternalID(0).getID().getValue());
                pid.getPatientName(0).getFamilyName().setValue(incomingPID.getPatientName(0).getFamilyName().getValue());
                pid.getPatientName(0).getGivenName().setValue(incomingPID.getPatientName(0).getGivenName().getValue());
                pid.getDateOfBirth().getTimeOfAnEvent().setValue(incomingPID.getDateOfBirth().getTimeOfAnEvent().getValue());
                pid.getSex().setValue(incomingPID.getSex().getValue());
                pid.getPhoneNumberHome(0).getXtn1_9999999X99999CAnyText().setValue(incomingPID.getPhoneNumberHome(0).getXtn1_9999999X99999CAnyText().getValue());
                pid.getPhoneNumberHome(0).getXtn2_TelecommunicationUseCode().setValue(incomingPID.getPhoneNumberHome(0).getXtn2_TelecommunicationUseCode().getValue());
                pid.getPhoneNumberHome(0).getXtn3_TelecommunicationEquipmentType().setValue(incomingPID.getPhoneNumberHome(0).getXtn3_TelecommunicationEquipmentType().getValue());

                // PV1 Segment
                PV1 pv1 = oruR01.getRESPONSE().getPATIENT().getVISIT().getPV1();
                PV1 incomingPv1 = ormO01.getPATIENT().getPATIENT_VISIT().getPV1();
                pv1.getSetIDPatientVisit().setValue(incomingPv1.getSetIDPatientVisit().getValue());
                pv1.getPatientType().setValue(incomingPv1.getPatientType().getValue());
                pv1.getReferringDoctor(0).getIDNumber().setValue(incomingPv1.getReferringDoctor(0).getIDNumber().getValue());
                pv1.getReferringDoctor(0).getFamilyName().setValue(incomingPv1.getReferringDoctor(0).getFamilyName().getValue());
                pv1.getReferringDoctor(0).getGivenName().setValue(incomingPv1.getReferringDoctor(0).getGivenName().getValue());

                // ORC Segment
                ORC orc = oruR01.getRESPONSE().getORDER_OBSERVATION().getORC();
                orc.getOrderControl().setValue(ormO01.getORDER().getORC().getOrderControl().getValue());

                // OBR Segment
                OBR obr = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBR();
                OBR incomingObr = ormO01.getORDER().getORDER_DETAIL().getOBR();
                String incomingTestId = incomingObr.getUniversalServiceIdentifier().getIdentifier().getValue();
                String incomingTestName = incomingObr.getUniversalServiceIdentifier().getText().getValue();
                obr.getSetIDObservationRequest().setValue(incomingObr.getSetIDObservationRequest().getValue());
                obr.getPlacerOrderNumber(0).getEntityIdentifier().setValue(incomingObr.getPlacerOrderNumber(0).getEntityIdentifier().getValue());
                obr.getFillerOrderNumber().getEntityIdentifier().setValue(incomingObr.getFillerOrderNumber().getEntityIdentifier().getValue());
                obr.getUniversalServiceIdentifier().getIdentifier().setValue(incomingTestId);
                obr.getUniversalServiceIdentifier().getText().setValue(incomingTestName);
                obr.getDiagnosticServiceSectionID().setValue(incomingObr.getDiagnosticServiceSectionID().getValue());
                obr.getFillerField2().setValue(incomingObr.getFillerField2().getValue());
                obr.getObr36_ScheduledDateTime().getTimeOfAnEvent().setValue(incomingObr.getObr36_ScheduledDateTime().getTimeOfAnEvent().getValue());
                obr.getPriority().setValue(incomingObr.getPriority().getValue());
                obr.getReasonForStudy(0).getText().setValue(incomingObr.getReasonForStudy(0).getText().getValue());

                // First OBX Segment
                OBX obx1 = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBSERVATION(0).getOBX();
                obx1.getObx1_SetIDOBX().setValue("1");
                obx1.getObx2_ValueType().setValue("TX");
                obx1.getObx3_ObservationIdentifier().getIdentifier().setValue(incomingTestId);
                obx1.getObx3_ObservationIdentifier().getText().setValue(incomingTestName);
                obx1.getObx11_ObservResultStatus().setValue("I");
                obx1.getObx12_DateLastObsNormalValues().getTimeOfAnEvent().setValue(HL7Utils.formatDatetime(new Date()));

                log.warn("Sending ORU_R01");
                sendMessage(oruR01);

                log.warn("Sleeping for 5 seconds before sending second response message");
                obx1.getObx11_ObservResultStatus().setValue("F");
                obx1.getObx5_ObservationValue(0).getData().parse("Radiology report message text");
                obx1.getObx14_DateTimeOfTheObservation().getTimeOfAnEvent().setValue(HL7Utils.formatDatetime(new Date()));
                obx1.getObx16_ResponsibleObserver().getFamilyName().setValue("pacs");

                OBX obx2 = oruR01.getRESPONSE().getORDER_OBSERVATION().getOBSERVATION(1).getOBX();
                obx2.getObx1_SetIDOBX().setValue("2");
                obx2.getObx2_ValueType().setValue("RP");
                obx2.getObx5_ObservationValue(0).getData().parse("PACS_IMAGE_VIEWER_URL");
                sendMessage(oruR01);
            }
            catch (Exception e) {
                log.warn("Error in MockPacsSystem: " + e.getMessage());
            }
        }

        void sendMessage(ORU_R01 oruR01) {
            if (RadiologyConfig.getIncomingHl7Port() != null) {
                try {
                    HapiContext context = new DefaultHapiContext();
                    Connection connection = context.newClient("localhost", RadiologyConfig.getIncomingHl7Port(), false);
                    Initiator initiator = connection.getInitiator();
                    ca.uhn.hl7v2.model.Message response = initiator.sendAndReceive(oruR01);
                    log.warn("Response following submission of ORU_R01 from Mock PACS: " + response);
                    log.info("Radiology results sent successfully");
                }
                catch (Exception e) {
                    log.error("Error sending radiology result message from Mock PACS: " + e);
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
