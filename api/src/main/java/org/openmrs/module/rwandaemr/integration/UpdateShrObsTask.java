package org.openmrs.module.rwandaemr.integration;

import java.util.List;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateShrObsTask implements Runnable {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static volatile boolean isExecuting = false; // Make volatile for thread safety

    @Override
    public void run() {
        if(isExecuting) {
            log.warn(getClass().getSimpleName() + " is still working on the task, skipping this run to prevent overlap");
            return;
        }
        isExecuting = true;

        //make sure to update the SHR registry
        try{
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
            
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            //Get the list all component to handle shr encounter synhronization
            List<UpdateShrObsListener> updateShrObsListeners = Context.getRegisteredComponents(UpdateShrObsListener.class);
            if(updateShrObsListeners != null && !updateShrObsListeners.isEmpty()){
                UpdateShrObsListener listener = updateShrObsListeners.get(0);
                listener.processQueuedMessages();
            }
            //stop the counter
            stopWatch.stop();
        } catch(Exception e){
            log.error("Error in " + getClass().getSimpleName(), e);
        } finally {
            isExecuting = false;
        }
    }
}
