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
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class UpdateShrEncounterListener extends HieEventListener {
    protected Log log = LogFactory.getLog(getClass());

    private final IntegrationConfig integrationConfig;
    private final ShrEncounterProvider shrEncounterProvider;

    private static DaemonToken daemonToken;
    private static final String ENCOUNTER_FORM_IDS_TO_PUSH_GP = "rwandaemr.hie.encounterFormIdToBePushed";
    private static final String ENCOUNTER_TYPE_IDS_TO_PUSH_GP = "rwandaemr.hie.encounterTypeIdToBePushed";
    private static final String ENCOUNTER_TYPE_ID_TO_PUSH_WITH_ALL_OBS_GP = "rwandaemr.hie.oneEncounterTypeIdToBePushedWithAllObs";
    private static final AtomicBoolean processing = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();
    private File messagesDir;

    public UpdateShrEncounterListener(
        @Autowired IntegrationConfig integrationConfig,
        @Autowired ShrEncounterProvider shrEncounterProvider
    ){
        this.integrationConfig = integrationConfig;
        this.shrEncounterProvider = shrEncounterProvider;
    }

    public static void setDaemonToken(DaemonToken daemonToken) {
        UpdateShrEncounterListener.daemonToken = daemonToken;
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
            throw new IllegalStateException("Daemon token is not set for UpdateShrEncounterListener");
        }
        Daemon.runInDaemonThread(() -> {
            try {
                Context.openSession();
                try {
                    if (!integrationConfig.isHieEnabled() || !integrationConfig.isShrPushEnabled()) {
                        log.debug("Skipping SHR encounter queue: HIE disabled or " + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + " is not true");
                        return;
                    }
                    if (shouldPushEncounter(uuid)) {
                        addEncounterToQueue(uuid, action);
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

    public void addEncounterToQueue(String encounterUuid, MapMessage mapMessage){
        try {
            String action = mapMessage.getString("action");
            addEncounterToQueue(encounterUuid, action);
        } catch(Exception e){
            throw new IllegalStateException("Error handling encounter message", e);
        }
    }

    private void addEncounterToQueue(String encounterUuid, String action){
        if (!integrationConfig.isHieEnabled() || !integrationConfig.isShrPushEnabled()) {
            log.debug("Skipping SHR encounter queue: HIE disabled or " + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + " is not true");
            return;
        }
        //handle the enccounter adding process into queue
        try{
            if (StringUtils.isEmpty(action)) {
                throw new IllegalArgumentException("Unable to retrieve action from MapMessage");
            }

            Date eventDate = new Date();
            ShrEncounterQueueItem queueItem = new ShrEncounterQueueItem();

            queueItem.setEncounterUuid(encounterUuid);
            queueItem.setEventDatetime(eventDate);
            queueItem.setEventType(action);
            writeMessafeToFile(queueItem);
        } catch(Exception e){
            throw new IllegalStateException("Error handling encounter message", e);
        }
    }

    public void processQueuedMessages(){
        //check if the integration is configured
        if(!integrationConfig.isHieEnabled()){
            log.debug("Integration with HIE is not enabled, returning");
			return;
        }
        if (!integrationConfig.isShrPushEnabled()) {
            log.debug("SHR push is disabled (" + IntegrationConfig.HIE_ENABLE_SHR_PUSH_PROPERTY + "), skipping encounter queue processing");
            return;
        }

        if(processing.compareAndSet(false, true)){
            long startedAt = System.currentTimeMillis();
            try{
                initializeMessageDir();

                //get the list of files not synced to HIE
                File[] files = Objects.requireNonNull(messagesDir.listFiles());
                Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
                int queueWarnThreshold = integrationConfig.getQueueWarnThreshold();
                int queueErrorThreshold = integrationConfig.getQueueErrorThreshold();
                if (files.length >= queueErrorThreshold) {
                    log.error("SHR encounter queue depth is very high: " + files.length + " files");
                } else if (files.length >= queueWarnThreshold) {
                    log.warn("SHR encounter queue depth is high: " + files.length + " files");
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
                    ShrEncounterQueueItem item = null;
                    try{
                        item = mapper.readValue(file, ShrEncounterQueueItem.class);
                        if(item.getNumAttempts() != null && item.getNumAttempts() > 5){
                            log.warn("Skipping and deleting file after " + item.getNumAttempts() + " failed attempts: " + file.getName());
                            // Delete files that exceeded max attempts to prevent disk space issues
                            FileUtils.deleteQuietly(file);
                            numFailure++;
                            continue;
                        }

                        //Here launch the process of processing the selected encounter
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
                        //if the processing process succeed delete the file
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
                            if (!file.equals(getMessageFile(item.getEncounterUuid()))) {
                                FileUtils.deleteQuietly(file);
                            }
                        }
                        //mark log message for later referance
                        log.debug("Error while processing the " + file.getName(), e);
                        numFailure++;
                    }
                }
                long durationMs = System.currentTimeMillis() - startedAt;
                log.info("\n++++++++++++++++++++++++++++++\nSHR Encounter sync run completed in " + durationMs + " ms; processed " + filesToProcess + " of " + files.length + " queued; " + numSuccess + " successful and " + numFailure + " failed\n++++++++++++++++++++++++++++++\n");
            } finally {
                processing.set(false);
            }
        }
    }

    public void processItem(ShrEncounterQueueItem item) throws Exception {
        Encounter encounter = Context.getEncounterService().getEncounterByUuid(item.getEncounterUuid());
        if (encounter == null) {
            log.warn("Skipping SHR encounter sync because encounter was not found: " + item.getEncounterUuid());
            return;
        }
        shrEncounterProvider.updateEncounterInShr(encounter);
    }

    private boolean shouldPushEncounter(String encounterUuid) {
        Encounter encounter = Context.getEncounterService().getEncounterByUuid(encounterUuid);
        if (encounter == null) {
            log.warn("Skipping SHR encounter queue because encounter was not found: " + encounterUuid);
            return false;
        }
        return isConfiguredEncounter(encounter);
    }

    private boolean isConfiguredEncounter(Encounter encounter) {
        Integer formId = encounter.getForm() == null ? null : encounter.getForm().getFormId();
        Integer encounterTypeId = encounter.getEncounterType() == null ? null : encounter.getEncounterType().getEncounterTypeId();

        if (isConfiguredId(formId, ENCOUNTER_FORM_IDS_TO_PUSH_GP) ||
                isConfiguredId(encounterTypeId, ENCOUNTER_TYPE_IDS_TO_PUSH_GP) ||
                isConfiguredId(encounterTypeId, ENCOUNTER_TYPE_ID_TO_PUSH_WITH_ALL_OBS_GP)) {
            return true;
        }

        log.debug("Skipping SHR encounter because form id " + formId + " is not listed in " + ENCOUNTER_FORM_IDS_TO_PUSH_GP +
                " and encounter type id " + encounterTypeId + " is not listed in " + ENCOUNTER_TYPE_IDS_TO_PUSH_GP +
                " or " + ENCOUNTER_TYPE_ID_TO_PUSH_WITH_ALL_OBS_GP);
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
            messagesDir = OpenmrsUtil.getDirectoryInApplicationDataDirectory("shr-encounter-queue");
            if(messagesDir.mkdirs()){
                log.debug("shr encounter queue directory created at: " + messagesDir.getAbsolutePath());
            }
        }
    }

    public void writeMessafeToFile(ShrEncounterQueueItem queueItem){
        try{
            initializeMessageDir();
            String queueItemString = mapper.writeValueAsString(queueItem);
            File targetFile = getMessageFile(queueItem.getEncounterUuid());
            // Delete existing file if it exists to avoid duplicates
            if(targetFile.exists()){
                FileUtils.deleteQuietly(targetFile);
            }
            // Use Files.write() instead of deprecated FileUtils.writeStringToFile()
            Files.write(targetFile.toPath(), queueItemString.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e){
            log.error("Unable to save shr encounter for later synchronization: ", e);
        }
    }

    private File getMessageFile(String encounterUuid) {
        return new File(messagesDir, encounterUuid + ".json");
    }

    @Data
    public static class ShrEncounterQueueItem {
        private String encounterUuid;
		private String eventType;
		private Date eventDatetime;
		private Integer numAttempts;
		private Date latestAttemptDatetime;
		private String latestAttemptResponse;
    }
}
