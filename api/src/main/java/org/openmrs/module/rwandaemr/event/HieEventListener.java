package org.openmrs.module.rwandaemr.event;

import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;

import lombok.Setter;

public abstract class HieEventListener implements EventListener{
    protected final Log log = LogFactory.getLog(getClass());
    @Setter private static DaemonToken daemonToken;

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

            Daemon.runInDaemonThread(() -> handle(uuid, mapMessage), daemonToken);
        } catch(Exception e){
            handleException(e);
        }
    }

    public abstract void handle(String encounterUuid, MapMessage mapMessage);
    public abstract void handleException(Exception e);
}
