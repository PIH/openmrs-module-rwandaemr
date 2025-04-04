package org.openmrs.module.rwandaemr.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.event.EventListener;

import javax.jms.MapMessage;
import javax.jms.Message;

/**
 * Listener that can be registered with Patient events and which will create a
 * default private insurance for that patient if they do not already have one
 */
public class ExampleJmsEventListener implements EventListener {

	protected final Log log = LogFactory.getLog(getClass());

	@Override
	public void onMessage(Message message) {
		try {
			MapMessage mapMessage = (MapMessage) message;
			Long timestamp = mapMessage.getLong("timestamp");
			String table = mapMessage.getString("table");
			String operation = mapMessage.getString("operation");
			Object key = mapMessage.getObject("key");
			log.trace(timestamp + "--" + table + "--" + operation + "--" + key);
			message.acknowledge();
		}
		catch (Exception e) {
			log.error(e);
		}
	}
}
