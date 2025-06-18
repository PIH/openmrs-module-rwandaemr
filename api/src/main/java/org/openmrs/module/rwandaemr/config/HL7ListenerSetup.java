package org.openmrs.module.rwandaemr.config;

import ca.uhn.hl7v2.app.HL7Service;
import ca.uhn.hl7v2.app.SimpleServer;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.radiology.ORUMessageListener;
import org.openmrs.util.ConfigUtil;

/**
 * Starts up the HL7 listener, if enabled
 */
public class HL7ListenerSetup {

    private static final Log log = LogFactory.getLog(HL7ListenerSetup.class);

    private static HL7Service hl7Service;

    public static void startup() {
        String property = ConfigUtil.getProperty("rwandaemr.radiology.hl7ListenerPort", null);
        if (StringUtils.isNotEmpty(property)) {
            try {
                log.info("Starting HL7 listener on port " + property);
                hl7Service = new SimpleServer(Integer.parseInt(property));
                ORUMessageListener oruMessageListener = Context.getRegisteredComponents(ORUMessageListener.class).iterator().next();
                hl7Service.registerApplication("ORU", "R01", oruMessageListener);
                hl7Service.start();
                log.info("HL7 listener started successfully");
            } catch (Exception e) {
                log.error("HL7 Listener failed to startup", e);
            }
        }
    }

    public static void shutdown() {
        if (hl7Service != null && hl7Service.isRunning()) {
            log.info("Shutting down HL7 listener");
            hl7Service.stop();
        }
    }
}
