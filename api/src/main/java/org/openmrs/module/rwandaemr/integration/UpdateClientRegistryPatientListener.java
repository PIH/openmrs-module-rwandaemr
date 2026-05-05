package org.openmrs.module.rwandaemr.integration;

import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.api.context.Context;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Updates the client registry
 */
@Component
public class UpdateClientRegistryPatientListener extends PatientEventListener {

	protected Log log = LogFactory.getLog(getClass());

	private final IntegrationConfig integrationConfig;
	private final ClientRegistryPatientProvider clientRegistryPatientProvider;

	private static final AtomicBoolean processing = new AtomicBoolean(false);
	private final ObjectMapper mapper = new ObjectMapper();
	private File messagesDir;

	public UpdateClientRegistryPatientListener(
			@Autowired IntegrationConfig integrationConfig,
			@Autowired ClientRegistryPatientProvider clientRegistryPatientProvider) {
		this.integrationConfig = integrationConfig;
		this.clientRegistryPatientProvider = clientRegistryPatientProvider;
	}

    @Override
    public void handlePatient(String patientUuid, MapMessage mapMessage) {
        // Queue-only: do not call HIE or OpenMRS read API in event path.
        addPatientToQueue(patientUuid, mapMessage);
    }

	@Override
	public void handleException(Exception e) {
		log.error("Unexpected exception in " + getClass(), e);
	}

	public void addPatientToQueue(String patientUuid, MapMessage mapMessage) {
		if (!integrationConfig.isHieEnabled() || !integrationConfig.isClientRegistryPushEnabled()) {
			log.debug("Skipping client registry queue: HIE disabled or " + IntegrationConfig.HIE_ENABLE_CR_PUSH_PROPERTY + " is not true");
			return;
		}
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
		if (!integrationConfig.isClientRegistryPushEnabled()) {
			log.debug("Client registry push is disabled (" + IntegrationConfig.HIE_ENABLE_CR_PUSH_PROPERTY + "), skipping queue processing");
			return;
		}
		if (processing.compareAndSet(false, true)) {
			long startedAt = System.currentTimeMillis();
			try {
				initializeMessageDir();
				File[] files = Objects.requireNonNull(messagesDir.listFiles());
				int queueWarnThreshold = integrationConfig.getQueueWarnThreshold();
				int queueErrorThreshold = integrationConfig.getQueueErrorThreshold();
				if (files.length >= queueErrorThreshold) {
					log.error("Client registry queue depth is very high: " + files.length + " files");
				} else if (files.length >= queueWarnThreshold) {
					log.warn("Client registry queue depth is high: " + files.length + " files");
				}
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
						log.warn("Loaded queue item - file: " + file.getName() + ", patientUuid: " + item.getPatientUuid() +
								", eventType: " + item.getEventType() + ", currentAttempts: " +
								(item.getNumAttempts() == null ? 0 : item.getNumAttempts()));
						if (item.getNumAttempts() != null && item.getNumAttempts() > 5) {
							log.warn("Skipping and deleting file after " + item.getNumAttempts() + " failed attempts: " + file.getName());
							// Delete files that exceeded max attempts to prevent disk space issues
							FileUtils.deleteQuietly(file);
							numFailure++;
							continue;
						}
						try {
							Context.openSession();
							processItem(item);
						} finally {
							Context.closeSession();
						}
						log.warn("Successfully synced patient to client registry - patientUuid: " + item.getPatientUuid() +
								", file: " + file.getName());
						log.warn("Deleting message file: " + file.getName());
						FileUtils.delete(file);
						numSuccess++;
					}
					catch (Exception e) {
						String patientUuidForLog = item != null ? item.getPatientUuid() : "unknown";
						log.error("Failed processing client registry queue item - file: " + file.getName() +
								", patientUuid: " + patientUuidForLog + ", reason: " + e.getMessage(), e);
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
							log.warn("Re-queued failed item - patientUuid: " + item.getPatientUuid() +
									", newAttempts: " + item.getNumAttempts() + ", latestAttemptResponse: " +
									item.getLatestAttemptResponse());
							// Delete old file to avoid duplicates
							FileUtils.deleteQuietly(file);
						}
						numFailure++;
					}
				}
				long durationMs = System.currentTimeMillis() - startedAt;
				log.warn("Client registry sync run completed in " + durationMs + " ms; processed " + filesToProcess +
						" of " + files.length + " queued; " + numSuccess + " successful " + numFailure + " failed");
			}
			finally {
				processing.set(false);
			}
		}
	}

	public void processItem(ClientRegistryPatientQueueItem item) throws Exception {
		log.warn("Updating client registry with: " + item);
		org.openmrs.Patient patient = Context.getPatientService().getPatientByUuid(item.getPatientUuid());
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
