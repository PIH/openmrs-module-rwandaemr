package org.openmrs.module.rwandaemr.event;

import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.event.EventListener;

public abstract class HieEventListener implements EventListener {
    protected final Log log = LogFactory.getLog(getClass());

    @Override
    public void onMessage(Message message){
        try {
            if(!(message instanceof MapMessage)){
                throw new IllegalArgumentException("Message is not a MapMessage object");
            }

            MapMessage mapMessage = (MapMessage) message;
            String uuid = mapMessage.getString("uuid");
            if(StringUtils.isBlank(uuid)){
                throw new IllegalArgumentException("Unable to retrieve encounter uuid from message: " + message);
            }
            // Queue-only path: run directly in the JMS thread. Avoid spawning a daemon thread per event.
            // This prevents thread accumulation and reduces freeze risk under high message volume.
            handle(uuid, mapMessage);
        } catch(Exception e){
            handleException(e);
        }
    }

    public abstract void handle(String encounterUuid, MapMessage mapMessage);
    public abstract void handleException(Exception e);
}
