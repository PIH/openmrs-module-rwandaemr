package org.openmrs.module.rwandaemr.integration;


import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.MapMessage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Obs;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class UpdateShrObsListener extends HieEventListener {

    private Log log = LogFactory.getLog(getClass());

    private final IntegrationConfig integrationConfig;
    private final ShrObsProvider shrObsProvider;

    private static DaemonToken daemonToken;
    private static final String OBS_CONCEPT_IDS_TO_PUSH_GP = "rwandaemr.hie.obsConceptIdToBePushed";
    private static final String ENCOUNTER_TYPE_ID_TO_PUSH_WITH_ALL_OBS_GP = "rwandaemr.hie.oneEncounterTypeIdToBePushedWithAllObs";
    private static final AtomicBoolean processing = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();
    private File messagesDir;

    public UpdateShrObsListener(
        @Autowired IntegrationConfig integrationConfig,
        @Autowired ShrObsProvider shrObsProvider
    ){
        this.integrationConfig = integrationConfig;
        this.shrObsProvider = shrObsProvider;
    }

    public static void setDaemonToken(DaemonToken daemonToken) {
        UpdateShrObsListener.daemonToken = daemonToken;
    }
    
    @Override
    public void handle(String uuid, MapMessage mapMessage) {
        String action;
        try {
            action = mapMessage.getString("action");
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to retrieve action from MapMessage", e);
        }
        if (StringUtils.isEmpty(action)) {
            throw new IllegalArgumentException("Unable to retrieve action from MapMessage");
        }
        if (daemonToken == null) {
            throw new IllegalStateException("Daemon token is not set for UpdateShrObsListener");
        }
        Daemon.runInDaemonThread(() -> {
            try {
                Context.openSession();
                try {
                    if (!integrationConfig.isHieEnabled() || !integrationConfig.isShrPushEnabled()) {
                        log.debug("Skipping SHR obs queue: HIE disabled or " + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + " is not true");
                        return;
                    }
                    if (shouldPushObs(uuid)) {
                        addObsToQueue(uuid, action);
                    }
                } finally {
                    Context.closeSession();
                }
            }
            catch (Exception e) {
                handleException(e);
            }
        }, daemonToken);
    }

    @Override
    public void handleException(Exception e) {
        log.error("Unexpected exception in " + getClass(), e);

    }

    public void addObsToQueue(String obsUuid, MapMessage mapMessage){
        try {
            String action = mapMessage.getString("action");
            addObsToQueue(obsUuid, action);
        } catch(Exception e){
            throw new IllegalStateException ("Error handling Obs message", e);
        }
    }

    private void addObsToQueue(String obsUuid, String action){
        if (!integrationConfig.isHieEnabled() || !integrationConfig.isShrPushEnabled()) {
            log.debug("Skipping SHR obs queue: HIE disabled or " + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + " is not true");
            return;
        }
        try {
            if (StringUtils.isEmpty(action)) {
                throw new IllegalArgumentException("Unable to retrieve action from MapMessage");
            }

            Date eventDate = new Date();
            ShrObsQueueItem queueItem = new ShrObsQueueItem();

            queueItem.setObsUuid(obsUuid);
            queueItem.setEventDatetime(eventDate);
            queueItem.setEventType(action);

            writeMessafeToFile(queueItem);
        } catch(Exception e){
            throw new IllegalStateException ("Error handling Obs message", e);
        }
    }

    public void processQueuedMessages(){
        if(!integrationConfig.isHieEnabled()){
            log.debug("Integration with HIE is not enabled, returning");
			return;
        }
        if (!integrationConfig.isShrPushEnabled()) {
            log.debug("SHR push is disabled (" + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + "), skipping obs queue processing");
            return;
        }

        if(processing.compareAndSet(false, true)){
            long startedAt = System.currentTimeMillis();
            try{
                initializeMessageDir();
                File[] files = Objects.requireNonNull(messagesDir.listFiles());
                Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
                int queueWarnThreshold = integrationConfig.getQueueWarnThreshold();
                int queueErrorThreshold = integrationConfig.getQueueErrorThreshold();
                if (files.length >= queueErrorThreshold) {
                    log.error("SHR obs queue depth is very high: " + files.length + " files");
                } else if (files.length >= queueWarnThreshold) {
                    log.warn("SHR obs queue depth is high: " + files.length + " files");
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

                for(int i = 0; i < filesToProcess; i++){
                    File file = files[i];
                    ShrObsQueueItem item = null;
                    try{
                        item = mapper.readValue(file, ShrObsQueueItem.class);
                        if(item.getNumAttempts() != null && item.getNumAttempts() > 5){
                            log.warn("Skipping and deleting file after " + item.getNumAttempts() + " failed attempts: " + file.getName());
                            // Delete files that exceeded max attempts to prevent disk space issues
                            FileUtils.deleteQuietly(file);
                            numFailure++;
                            continue;
                        }
                        boolean openedSession = false;
                        try {
                            if (!Context.isSessionOpen()) {
                                Context.openSession();
                                openedSession = true;
                            }
                            processItem(item);
                        } finally {
                            if (openedSession) {
                                Context.closeSession();
                            }
                        }
                        FileUtils.delete(file);
                        numSuccess++;
                    } catch(Exception e){
                        //If any exception happens log the error and skip it for later retry
                        if(item != null){
                            item.setLatestAttemptDatetime(new Date());
                            item.setLatestAttemptResponse(e.getMessage());
                            // Increment attempt count
                            if(item.getNumAttempts() == null){
                                item.setNumAttempts(1);
                            } else {
                                item.setNumAttempts(item.getNumAttempts() + 1);
                            }
                            writeMessafeToFile(item);
                            // Delete old file to avoid duplicates
                            if (!file.equals(getMessageFile(item.getObsUuid()))) {
                                FileUtils.deleteQuietly(file);
                            }
                        }
                        //mark log message for later referance
                        log.debug("Error while processing the " + file.getName(), e);
                        numFailure++;
                    }
                }
                long durationMs = System.currentTimeMillis() - startedAt;
                log.info("\n++++++++++++++++++++++++++++++\nSHR Obs sync run completed in " + durationMs + " ms; processed " + filesToProcess + " of " + files.length + " queued; " + numSuccess + " successful and " + numFailure + " failed\n++++++++++++++++++++++++++++++\n");
            } finally {
                processing.set(false);
            }
        }
    }

    public void processItem(ShrObsQueueItem item) throws Exception {
        Obs obs = Context.getObsService().getObsByUuid(item.getObsUuid());
        if (obs == null) {
            log.warn("Skipping SHR obs sync because obs was not found: " + item.getObsUuid());
            return;
        }
        //if (isConfiguredConcept(obs)) {
            shrObsProvider.updateObsInShr(obs);
        //}
    }

    private boolean shouldPushObs(String obsUuid) {
        Obs obs = Context.getObsService().getObsByUuid(obsUuid);
        if (obs == null) {
            log.warn("Skipping SHR obs queue because obs was not found: " + obsUuid);
            return false;
        }
        return isConfiguredConcept(obs);
    }

    private boolean isConfiguredConcept(Obs obs) {
        Integer encounterTypeId = obs.getEncounter() == null || obs.getEncounter().getEncounterType() == null ?
                null : obs.getEncounter().getEncounterType().getEncounterTypeId();
        if (isConfiguredId(encounterTypeId, ENCOUNTER_TYPE_ID_TO_PUSH_WITH_ALL_OBS_GP)) {
            return true;
        }

        if (obs.getConcept() == null || obs.getConcept().getConceptId() == null) {
            log.warn("Skipping SHR obs because obs has no concept: " + obs.getUuid());
            return false;
        }

        int conceptId = obs.getConcept().getConceptId();
        String configuredConceptIds = Context.getAdministrationService().getGlobalProperty(OBS_CONCEPT_IDS_TO_PUSH_GP);
        if (StringUtils.isBlank(configuredConceptIds)) {
            log.debug("Skipping SHR obs because " + OBS_CONCEPT_IDS_TO_PUSH_GP + " is blank");
            return false;
        }

        for (String configuredConceptId : configuredConceptIds.split(",")) {
            if (StringUtils.isBlank(configuredConceptId)) {
                continue;
            }
            try {
                if (conceptId == Integer.parseInt(configuredConceptId.trim())) {
                    return true;
                }
            }
            catch (NumberFormatException e) {
                log.warn("Ignoring invalid concept id in " + OBS_CONCEPT_IDS_TO_PUSH_GP + ": " + configuredConceptId);
            }
        }
        log.debug("Skipping SHR obs because concept id " + conceptId + " is not listed in " + OBS_CONCEPT_IDS_TO_PUSH_GP);
        return false;
    }

    private boolean isConfiguredId(Integer id, String propertyName) {
        if (id == null) {
            return false;
        }

        String configuredIds = Context.getAdministrationService().getGlobalProperty(propertyName);
        if (StringUtils.isBlank(configuredIds)) {
            return false;
        }

        for (String configuredId : configuredIds.split(",")) {
            if (StringUtils.isBlank(configuredId)) {
                continue;
            }
            try {
                if (id == Integer.parseInt(configuredId.trim())) {
                    return true;
                }
            }
            catch (NumberFormatException e) {
                log.warn("Ignoring invalid id in " + propertyName + ": " + configuredId);
            }
        }
        return false;
    }

    public void initializeMessageDir(){
        if(messagesDir == null){
            messagesDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("shr-obs-queue");
            if(messagesDir.mkdirs()){
                log.debug("shr obs queue directory created at: " + messagesDir.getAbsolutePath());
            }
        }
    }

    public void writeMessafeToFile(ShrObsQueueItem queueItem){
        try{
            // This runs in the JMS event path too, where OpenMRS Context may not be open.
            initializeMessageDir();
            String queueItemString = mapper.writeValueAsString(queueItem);
            File targetFile = getMessageFile(queueItem.getObsUuid());
            // Delete existing file if it exists to avoid duplicates
            if (targetFile.exists()) {
                FileUtils.deleteQuietly(targetFile);
            }
            // Use Files.write() instead of deprecated FileUtils.writeStringToFile()
            Files.write(targetFile.toPath(), queueItemString.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e){
            log.error("Unable to save shr obs for later synchronization: ", e);
        }
    }

    private File getMessageFile(String obsUuid) {
        return new File(messagesDir, obsUuid + ".json");
    }

    @Data
    public static class ShrObsQueueItem {
        private String obsUuid;
		private String eventType;
		private Date eventDatetime;
		private Integer numAttempts;
		private Date latestAttemptDatetime;
		private String latestAttemptResponse;
    }
}
