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

import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.model.Message;
import lombok.Data;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This class is registered with the HAPI simple server that listens for incoming HL7 messages and is responsible
 * for handling the ORU_R01 messages (observations in response to an order)
 */
@Component
public class ORUR01MessageListener implements Application {

    @Setter
    private static DaemonToken daemonToken;

    private final Log log = LogFactory.getLog(getClass());
    private final ORUR01ToObsTranslator oruToObsTranslator;

    private RadiologyHl7MessageLog messageLog;

    public ORUR01MessageListener(@Autowired ORUR01ToObsTranslator oruToObsTranslator) {
        this.oruToObsTranslator = oruToObsTranslator;
    }

    @Autowired(required = false)
    public void setMessageLog(RadiologyHl7MessageLog messageLog) {
        this.messageLog = messageLog;
    }

    @Override
    public Message processMessage(Message message) {
        while (daemonToken == null) {
            if (log.isTraceEnabled()) {
                log.trace("Waiting for daemon token to be set prior to processing message");
            }
        }
        log.warn("Processing ORU_R01 message");
        HL7Response hl7Response = new HL7Response();
        Daemon.runInDaemonThreadAndWait(() -> {
            String messageText = null;
            Exception error = null;
            try {
                messageText = message.encode();
                oruToObsTranslator.fromORU_R01(messageText);
                hl7Response.setMessage(HL7Utils.generateAckMessage(message, null));
            }
            catch (Exception e) {
                log.error("Unable to parse incoming ORU_RO1 message", e);
                error = e;
                hl7Response.setMessage(HL7Utils.generateAckMessage(message, e));
            }
            logMessage(messageText, hl7Response.getMessage(), error);
        }, daemonToken);
        return hl7Response.getMessage();
    };

    /**
     * Keeps a copy of the message and of the answer given to PACS.  Failing to keep it never affects processing.
     */
    private void logMessage(String messageText, Message ack, Exception error) {
        if (messageLog == null || messageText == null) {
            return;
        }
        try {
            messageLog.logIncoming(messageText, ack == null ? null : ack.encode(), error);
        }
        catch (Exception e) {
            log.warn("Unable to keep a copy of the ORU_R01 message received from PACS", e);
        }
    }

    @Override
    public boolean canProcess(Message message) {
        return message != null && "ORU_R01".equals(message.getName());
    }

    @Data
    static class HL7Response {
        Message message;
    }
}
