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
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v23.message.ACK;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

import static org.openmrs.module.rwandaemr.radiology.HL7Utils.populateMshSegment;

@Component
public class ORUR01MessageListener implements Application {

    private final Log log = LogFactory.getLog(getClass());
    private final ORUR01ToObsTranslator oruToObsTranslator;

    public ORUR01MessageListener(@Autowired ORUR01ToObsTranslator oruToObsTranslator) {
        this.oruToObsTranslator = oruToObsTranslator;
    }

    @Override
    public Message processMessage(Message message) throws HL7Exception {
        String errorMessage = null;
        ORU_R01 oruR01 = null;
        try {
            oruR01 = (ORU_R01) message;
            oruToObsTranslator.fromORU_R01(message.encode());
        }
        catch (Exception e) {
            log.error("Unable to parse incoming ORU_RO1 message", e);
            errorMessage = e.getMessage();
        }
        Date now = new Date();
        ACK ack = new ACK();
        populateMshSegment(ack.getMSH(), oruR01.getMSH().getReceivingFacility().getName(), now, "ACK", "O01");
        ack.getMSA().getMessageControlID().setValue(oruR01.getMSH().getMessageControlID().getValue());
        if (errorMessage == null) {
            ack.getMSA().getAcknowledgementCode().setValue("AA");
        }
        else {
            ack.getMSA().getAcknowledgementCode().setValue("AR");
            ack.getMSA().getTextMessage().setValue(errorMessage);
        }
        return ack;
    }

    @Override
    public boolean canProcess(Message message) {
        return  message != null && "ORU_R01".equals(message.getName());
    }
}
