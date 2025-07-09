package org.openmrs.module.rwandaemr.integration;

import java.util.List;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateShrEncounterTask implements Runnable {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static boolean isExecuting = false;

    @Override
    public void run() {
        if(isExecuting) {
            log.info(getClass() + "is still woring on the task please wait");
            return;
        }
        isExecuting = true;

        //make sure to update the SHR registry
        try{
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            //Get the list all compenent to handle shr encounter synhronization
            List<UpdateShrEncounterListener> updateShrEncounterListeners = Context.getRegisteredComponents(UpdateShrEncounterListener.class);
            if(updateShrEncounterListeners != null && !updateShrEncounterListeners.isEmpty()){
                UpdateShrEncounterListener listener = updateShrEncounterListeners.get(0);
                listener.processQueuedMessages();
            }
            //stop the counter
            stopWatch.stop();
        } finally {
            isExecuting = false;
        }
    }
    
}
