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
 * Scheduled task that will attempt to update the client registry if these attempts had previously failed
 */
public class UpdateClientRegistryTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static volatile boolean isExecuting = false; // Make volatile for thread safety

    @Override
    public void run() {
        if (isExecuting) {
            log.warn(getClass().getSimpleName() + " is still executing, skipping this run to prevent overlap");
            return;
        }
        isExecuting = true;
        try {
            // Check if HIE is enabled before doing any work
            List<IntegrationConfig> configs = Context.getRegisteredComponents(IntegrationConfig.class);
            if(configs == null || configs.isEmpty()){
                log.debug("IntegrationConfig not found, skipping " + getClass().getSimpleName());
                return;
            }
            IntegrationConfig integrationConfig = configs.get(0);
            if(!integrationConfig.isHieEnabled()){
                log.debug("HIE is not enabled, skipping " + getClass().getSimpleName());
                return;
            }
            
            log.info("Executing " + getClass());
            StopWatch sw = new StopWatch();
            sw.start();

            List<UpdateClientRegistryPatientListener> l = Context.getRegisteredComponents(UpdateClientRegistryPatientListener.class);
            if (l != null && !l.isEmpty()) {
                UpdateClientRegistryPatientListener listener = l.get(0);
                listener.processQueuedMessages();
            }

            sw.stop();
        } catch(Exception e){
            log.error("Error in " + getClass().getSimpleName(), e);
        }
        finally {
            isExecuting = false;
        }
    }
}
