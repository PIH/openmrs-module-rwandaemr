package org.openmrs.module.rwandaemr.event;

import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;

import javax.jms.MapMessage;
import javax.jms.Message;

/**
 * Listener that can be registered with Patient events and which will create a
 * default private insurance for that patient if they do not already have one
 */
public abstract class PatientEventListener implements EventListener {

	protected final Log log = LogFactory.getLog(getClass());
	@Setter private static DaemonToken daemonToken;

	@Override
	public void onMessage(Message message) {
		try {
			if (!(message instanceof MapMessage)) {
				throw new IllegalArgumentException("message is not a MapMessage");
			}
			MapMessage mapMessage = (MapMessage) message;
			String patientUuid = mapMessage.getString("uuid");
			if (StringUtils.isBlank(patientUuid)) {
				throw new IllegalArgumentException("unable to retrieve patient uuid from message: " + message);
			}
			
			// Check if HIE is enabled BEFORE creating daemon thread to avoid thread pool exhaustion
			// This check is done here to prevent creating unnecessary threads when HIE is disabled
			if(!isHieEnabled()){
				log.debug("HIE is not enabled, skipping event processing for patient uuid: " + patientUuid);
				return;
			}
			
			Daemon.runInDaemonThread(() -> handlePatient(patientUuid, mapMessage), daemonToken);
		}
		catch (Exception e) {
			handleException(e);
		}
	}
	
	/**
	 * Check if HIE is enabled. This method should be implemented by subclasses
	 * to avoid creating unnecessary daemon threads when HIE is disabled.
	 */
	protected abstract boolean isHieEnabled();

	public abstract void handlePatient(String patientUuid, MapMessage mapMessage);

	public abstract void handleException(Exception e);
}
