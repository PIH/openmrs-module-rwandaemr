package org.openmrs.module.rwandaemr.integration;

import java.util.List;

import org.apache.commons.lang.time.StopWatch;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateShrObsTask implements Runnable {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static boolean isExecuting = false;


    @Override
    public void run() {
        if(isExecuting) {
            log.info(getClass() + "is still working on the task please wait");
            return;
        }
        isExecuting = true;

        //make sure to update the SHR registry
        try{
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
        } finally {
            isExecuting = false;
        }
    }
}
