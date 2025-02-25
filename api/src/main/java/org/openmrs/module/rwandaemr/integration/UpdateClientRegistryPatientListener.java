package org.openmrs.module.rwandaemr.integration;

import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.rwandaemr.event.PatientEventListener;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.MapMessage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

/**
 * Updates the client registry
 */
@Component
public class UpdateClientRegistryPatientListener extends PatientEventListener {

	protected Log log = LogFactory.getLog(getClass());

	private final PatientService patientService;
	private final IntegrationConfig integrationConfig;
	private final ClientRegistryPatientProvider clientRegistryPatientProvider;

	private static boolean processing = false;
	private final ObjectMapper mapper = new ObjectMapper();
	private File messagesDir;

	public UpdateClientRegistryPatientListener(
			@Autowired PatientService patientService,
			@Autowired IntegrationConfig integrationConfig,
			@Autowired ClientRegistryPatientProvider clientRegistryPatientProvider) {
		this.patientService = patientService;
		this.integrationConfig = integrationConfig;
		this.clientRegistryPatientProvider = clientRegistryPatientProvider;
	}

	@Override
	public void handlePatient(String patientUuid, MapMessage mapMessage) {
		if (!integrationConfig.isMPIEnabled()) {
			log.debug("Integration with client registry is not enabled, returning");
			return;
		}
		log.warn("Updating client registry with: " + patientUuid);
		Patient patient = patientService.getPatientByUuid(patientUuid);
		try {
			clientRegistryPatientProvider.updatePatientInClientRegistry(patient);
		}
		catch (Exception e) {
			log.warn("Error updating client registry, adding to queue: " + patientUuid + "; " + e.getMessage());
			addPatientToQueue(patientUuid, mapMessage);
		}
	}

	@Override
	public void handleException(Exception e) {
		log.error("Unexpected exception in " + getClass(), e);
	}

	public void addPatientToQueue(String patientUuid, MapMessage mapMessage) {
		try {
			String action = mapMessage.getString("action");
			if (StringUtils.isEmpty(action)) {
				throw new IllegalArgumentException("Unable to retrieve action from MapMessage");
			}
			Date eventDate = new Date();
			ClientRegistryPatientQueueItem queueItem = new ClientRegistryPatientQueueItem();
			queueItem.setPatientUuid(patientUuid);
			queueItem.setEventType(action);
			queueItem.setEventDatetime(eventDate);
			writeMessageToFile(queueItem);
		}
		catch (Exception e) {
			throw new IllegalStateException("Error handling patient message", e);
		}
	}

	public void processQueuedMessages() {
		if (!integrationConfig.isMPIEnabled()) {
			log.debug("Integration with client registry is not enabled, returning");
			return;
		}
		if (!processing) {
			try {
				processing = true;
				initializeMessageDir();
				File[] files = Objects.requireNonNull(messagesDir.listFiles());
				log.warn("Processing " + files.length + " messages from " + messagesDir.getAbsolutePath());
				int numSuccess = 0;
				int numFailure = 0;
				initializeMessageDir();
				for (File file : files) {
					ClientRegistryPatientQueueItem item = null;
					try {
						log.warn("Processing message file: " + file.getName());
						item = mapper.readValue(file, ClientRegistryPatientQueueItem.class);
						if (item.getNumAttempts() != null && item.getNumAttempts() > 5) {
							log.warn("Skipping file, as number of attempts = " + item.getNumAttempts());
							continue;
						}
						processItem(item);
						log.warn("Deleting message file: " + file.getName());
						FileUtils.delete(file);
						numSuccess++;
					}
					catch (Exception e) {
						if (item != null) {
							item.setLatestAttemptDatetime(new Date());
							item.setLatestAttemptResponse(e.getMessage());
							writeMessageToFile(item);
						}
						log.debug("Error processing file " + file.getName(), e);
						numFailure++;
					}
				}
				log.warn("Processing " + files.length + " complete: " + numSuccess + " successful " + numFailure + " failed");
			}
			finally {
				processing = false;
			}
		}
	}

	public void processItem(ClientRegistryPatientQueueItem item) throws Exception {
		log.warn("Updating client registry with: " + item);
		Patient patient = patientService.getPatientByUuid(item.getPatientUuid());
		clientRegistryPatientProvider.updatePatientInClientRegistry(patient);
	}

	public void initializeMessageDir() {
		if (messagesDir == null) {
			messagesDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("client-registry-queue");
			if (messagesDir.mkdirs()) {
				log.warn("Created client registry queue directory at: " + messagesDir.getAbsolutePath());
			}
		}
	}

	public void writeMessageToFile(ClientRegistryPatientQueueItem item) {
		try {
			initializeMessageDir();
			String queueItemString = mapper.writeValueAsString(item);
			String fileName = item.getEventDatetime().getTime() + "_" + item.getPatientUuid() + ".json";
			FileUtils.writeStringToFile(new File(messagesDir, fileName), queueItemString, StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			log.error("Unable to  write client registry patient queue item to file", e);
		}
	}

	@Data
	public static class ClientRegistryPatientQueueItem {
		private String patientUuid;
		private String eventType;
		private Date eventDatetime;
		private Integer numAttempts;
		private Date latestAttemptDatetime;
		private String latestAttemptResponse;
	}
}
