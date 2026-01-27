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
import java.nio.file.Files;
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

	private static volatile boolean processing = false;
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
	protected boolean isHieEnabled() {
		return integrationConfig.isHieEnabled();
	}

	@Override
	public void handlePatient(String patientUuid, MapMessage mapMessage) {
		if (!integrationConfig.isHieEnabled()) {
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
		if (!integrationConfig.isHieEnabled()) {
			log.debug("Integration with client registry is not enabled, returning");
			return;
		}
		if (!processing) {
			try {
				processing = true;
				initializeMessageDir();
				File[] files = Objects.requireNonNull(messagesDir.listFiles());
				// Limit processing to prevent long-running operations that could freeze the system
				int maxFilesPerRun = 100;
				if(files.length > maxFilesPerRun){
					log.warn("Limiting processing to " + maxFilesPerRun + " files out of " + files.length + " total to prevent system freeze");
				}
				int filesToProcess = Math.min(files.length, maxFilesPerRun);
				log.warn("Processing " + filesToProcess + " messages from " + messagesDir.getAbsolutePath());
				int numSuccess = 0;
				int numFailure = 0;
				for (int i = 0; i < filesToProcess; i++) {
					File file = files[i];
					ClientRegistryPatientQueueItem item = null;
					try {
						log.warn("Processing message file: " + file.getName());
						item = mapper.readValue(file, ClientRegistryPatientQueueItem.class);
						if (item.getNumAttempts() != null && item.getNumAttempts() > 5) {
							log.warn("Skipping and deleting file after " + item.getNumAttempts() + " failed attempts: " + file.getName());
							// Delete files that exceeded max attempts to prevent disk space issues
							FileUtils.deleteQuietly(file);
							numFailure++;
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
							// Increment attempt count
							if(item.getNumAttempts() == null){
								item.setNumAttempts(1);
							} else {
								item.setNumAttempts(item.getNumAttempts() + 1);
							}
							writeMessageToFile(item);
							// Delete old file to avoid duplicates
							FileUtils.deleteQuietly(file);
						}
						log.debug("Error processing file " + file.getName(), e);
						numFailure++;
					}
				}
				log.warn("Processing " + filesToProcess + " complete: " + numSuccess + " successful " + numFailure + " failed");
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
			File targetFile = new File(messagesDir, fileName);
			// Delete existing file if it exists to avoid duplicates
			if(targetFile.exists()){
				FileUtils.deleteQuietly(targetFile);
			}
			// Use Files.write() instead of deprecated FileUtils.writeStringToFile()
			Files.write(targetFile.toPath(), queueItemString.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception e) {
			log.error("Unable to write client registry patient queue item to file", e);
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
