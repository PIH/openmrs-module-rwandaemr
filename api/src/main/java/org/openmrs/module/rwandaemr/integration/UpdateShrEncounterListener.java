package org.openmrs.module.rwandaemr.integration;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Objects;

import javax.jms.MapMessage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Encounter;
import org.openmrs.api.EncounterService;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class UpdateShrEncounterListener extends HieEventListener {
    protected Log log = LogFactory.getLog(getClass());

    private final EncounterService encounterService;
    private final IntegrationConfig integrationConfig;
    private final ShrEncounterProvider shrEncounterProvider;

    private static boolean processing = false;
    private final ObjectMapper mapper = new ObjectMapper();
    private File messagesDir;

    public UpdateShrEncounterListener(
        @Autowired EncounterService encounterService,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired ShrEncounterProvider shrEncounterProvider
    ){
        this.encounterService = encounterService;
        this.integrationConfig = integrationConfig;
        this.shrEncounterProvider = shrEncounterProvider;
    }

    @Override
    public void handle(String uuid, MapMessage mapMessage) {
        //make sure to handle the encounter operation and check if we can synchronize

        //is the HOE fully configured
        if(!integrationConfig.isHieEnabled()){
            log.debug("Integration with HIE is not enabled");
            return;
        }

        //Is the encounter available in OpenMRS
        Encounter encounter = encounterService.getEncounterByUuid(uuid);

        //if the encounter is ready now we can initialize the request to SHR
        if(encounter != null){
            try{
                /***
                 * Make sure to filter Encounter which can be submitted to HIE to reduce the data synchronized to HIE id application
                 */
                shrEncounterProvider.updateEncounterInShr(encounter);
            } catch(Exception e){
                log.warn("Unable to update SHR, adding to queue for later retry: " + uuid + "; message info: " + e.getMessage());
                //The next line will be re-enabled after finalizing the OBS synchronization too
                addEncounterToQueue(uuid, mapMessage);
            }
        }
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

        if(!processing){
            try{
                processing = true;
                initializeMessageDir();

                //get the list of files not synced to HIE
                File[] files = Objects.requireNonNull(messagesDir.listFiles());
                log.warn("Processing " + files.length + " messages from " + messagesDir.getAbsolutePath());

                int numSuccess = 0;
                int numFailure = 0;

                for(File file : files){
                    ShrEncounterQueueItem item = null;
                    try{
                        item = mapper.readValue(file, ShrEncounterQueueItem.class);
                        if(item.getNumAttempts() != null && item.getNumAttempts() > 5){
                            log.warn("Skipping file, as number of attempts = " + item.getNumAttempts());
                            continue;
                        }

                        //Here launch the process of processing the selected encounter
                        processItem(item);
                        //if the processing process succeed delete the file
                        FileUtils.delete(file);
                        numSuccess++;
                    } catch(Exception e){
                        //If any exception happens log the error and skip it for later retry
                        if(item != null){
                            item.setLatestAttemptDatetime(new Date());
                            item.setLatestAttemptResponse(e.getMessage());
                            writeMessafeToFile(item);
                        }
                        //mark log message for later referance
                        log.debug("Error while processing the " + file.getName(), e);
                        numFailure++;
                    }
                }
                log.info("\n++++++++++++++++++++++++++++++\nProcessing " + files.length + " completed with " + numSuccess + " successful sync and " + numFailure + "failed sync\n++++++++++++++++++++++++++++++\n");
            } finally {
                processing = false;
            }
        }
    }

    public void processItem(ShrEncounterQueueItem item) throws Exception {
        Encounter encounter = encounterService.getEncounterByUuid(item.getEncounterUuid());
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
