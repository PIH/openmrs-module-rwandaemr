package org.openmrs.module.rwandaemr.radiology;

import ca.uhn.hl7v2.app.HL7Service;
import ca.uhn.hl7v2.app.SimpleServer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.config.Setup;
import org.openmrs.module.rwandaemr.radiology.mock.MockPacsSystem;
import org.springframework.stereotype.Component;

/**
 * Starts up the HL7 listener, if enabled
 */
@Component
public class HL7ListenerSetup implements Setup {

    private static final Log log = LogFactory.getLog(HL7ListenerSetup.class);

    private static HL7Service hl7Service;

    @Override
    public void initialize() {
        Integer port = RadiologyConfig.getIncomingHl7Port();
        if (port != null) {
            try {
                log.info("Starting HL7 listener on port " + port);
                hl7Service = new SimpleServer(port);
                ORUR01MessageListener oruMessageListener = Context.getRegisteredComponents(ORUR01MessageListener.class).iterator().next();
                log.info("Listening for ORU_R01 messages enabled");
                hl7Service.registerApplication("ORU", "R01", oruMessageListener);
                if (RadiologyConfig.enableMockPacsSystem()) {
                    log.info("Listening for ORM_O01 messages enabled - mock PACS system");
                    MockPacsSystem mockPacsSystem = Context.getRegisteredComponents(MockPacsSystem.class).iterator().next();
                    hl7Service.registerApplication("ORM", "O01", mockPacsSystem);
                    mockPacsSystem.start();
                }
                hl7Service.start();
                log.info("HL7 listener started successfully");
            } catch (Exception e) {
                log.error("HL7 Listener failed to startup", e);
            }
        }
        else {
            log.info("Incoming HL7 listening for radiology is not enabled");
        }
    }

    @Override
    public void teardown() {
        if (hl7Service != null && hl7Service.isRunning()) {
            log.info("Shutting down HL7 listener");
            hl7Service.stop();
        }
    }
}
