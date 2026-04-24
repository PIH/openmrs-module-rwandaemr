package org.openmrs.module.rwandaemr.integration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.MapMessage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.api.context.Context;
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

    @Override
    public void handle(String uuid, MapMessage mapMessage) {
        // Queue-only: do not call HIE or OpenMRS read API in event path.
        // Scheduled task validates and processes in controlled daemon context.
        addEncounterToQueue(uuid, mapMessage);
    }

    @Override
    public void handleException(Exception e) {
        log.error("Unexpected exception in " + getClass(), e);

    }

    public void addEncounterToQueue(String encounterUuid, MapMessage mapMessage){
        //handle the enccounter adding process into queue
        try{
            String action = mapMessage.getString("action");
            if(StringUtils.isEmpty(action)){
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

        if(processing.compareAndSet(false, true)){
            long startedAt = System.currentTimeMillis();
            try{
                initializeMessageDir();

                //get the list of files not synced to HIE
                File[] files = Objects.requireNonNull(messagesDir.listFiles());
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
                        try {
                            Context.openSession();
                            processItem(item);
                        } finally {
                            Context.closeSession();
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
                            FileUtils.deleteQuietly(file);
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
        org.openmrs.Encounter encounter = Context.getEncounterService().getEncounterByUuid(item.getEncounterUuid());
        shrEncounterProvider.updateEncounterInShr(encounter);
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
            String fileName = queueItem.getEventDatetime().getTime() + "_" + queueItem.getEncounterUuid() + ".json";
            File targetFile = new File(messagesDir, fileName);
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
