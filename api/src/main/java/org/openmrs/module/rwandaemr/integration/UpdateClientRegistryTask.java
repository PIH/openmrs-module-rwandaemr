package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Visit;
import org.openmrs.api.LocationService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.emrapi.adt.AdtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Custom implementation to close stale visits
 * This mimics the AdtService#CloseStaleVisitsTask, but is executed by Spring and adds additional logging
 */
public class UpdateClientRegistryTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static boolean isExecuting = false;

    @Override
    public void run() {
        if (isExecuting) {
            log.info(getClass() + " is still executing, not running again");
            return;
        }
        isExecuting = true;
        try {
            log.info("Executing " + getClass());
            StopWatch sw = new StopWatch();
            sw.start();

            List<UpdateClientRegistryPatientListener> l = Context.getRegisteredComponents(UpdateClientRegistryPatientListener.class);
            if (l != null && !l.isEmpty()) {
                UpdateClientRegistryPatientListener listener = l.get(0);
                listener.processQueuedMessages();
            }

            sw.stop();
        }
        finally {
            isExecuting = false;
        }
    }
}
