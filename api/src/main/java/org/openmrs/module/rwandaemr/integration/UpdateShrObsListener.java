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
import org.openmrs.Obs;
import org.openmrs.api.ObsService;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class UpdateShrObsListener extends HieEventListener {

    private Log log = LogFactory.getLog(getClass());

    private final ObsService obsService;
    private final IntegrationConfig integrationConfig;
    private final ShrObsProvider shrObsProvider;

    private static boolean processing = false;
    private final ObjectMapper mapper = new ObjectMapper();
    private File messagesDir;

    public UpdateShrObsListener(
        @Autowired ObsService obsService,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired ShrObsProvider shrObsProvider
    ){
        this.obsService = obsService;
        this.integrationConfig = integrationConfig;
        this.shrObsProvider = shrObsProvider;
    }
    
    @Override
    public void handle(String uuid, MapMessage mapMessage) {
        //Handle Obs operation and synchronize to SHR
        if(!integrationConfig.isHieEnabled()){
            log.debug("Integration with HIE is not enabled");
            return;
        }

        //If the Observation is available in EMR Database 
        Obs obs = obsService.getObsByUuid(uuid);
        if(obs != null){
            try{
                /**
                 * Here Make sure we can filter only required observation otherwise send them all
                 */

                //Pass the provider information so that the processing continues from their
                shrObsProvider.updateObsInShr(obs);

            } catch(Exception e){
                log.warn("Unable to update SHR, adding to queue for later retry: " + uuid + "; message info: " + e.getMessage());
                addObsToQueue(uuid, mapMessage);
            }
        }
    }

    @Override
    public void handleException(Exception e) {
        log.error("Unexpected exception in " + getClass(), e);

    }

    public void addObsToQueue(String obsUuid, MapMessage mapMessage){
        try {
            String action = mapMessage.getString("action");
            if(StringUtils.isEmpty(action)){
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

        if(!processing){
            try{
                processing = true;
                initializeMessageDir();
                File[] files = Objects.requireNonNull(messagesDir.listFiles());
                log.warn("Processing " + files.length + " messages from " + messagesDir.getAbsolutePath());

                int numSuccess = 0;
                int numFailure = 0;

                for(File file : files){
                    ShrObsQueueItem item = null;
                    try{
                        item = mapper.readValue(file, ShrObsQueueItem.class);
                        if(item.getNumAttempts() != null && item.getNumAttempts() > 5){
                            log.warn("Skipping file, as number of attempts = " + item.getNumAttempts());
                            continue;
                        }
                        processItem(item);
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

    public void processItem(ShrObsQueueItem item) throws Exception {
        Obs obs = obsService.getObsByUuid(item.getObsUuid());
        shrObsProvider.updateObsInShr(obs);
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
            initializeMessageDir();
            String queueItemString = mapper.writeValueAsString(queueItem);
            String fileName = queueItem.getEventDatetime().getTime() + "_" + queueItem.getObsUuid() + ".json";
            File targetFile = new File(messagesDir, fileName);
            // Delete existing file if it exists to avoid duplicates
            if(targetFile.exists()){
                FileUtils.deleteQuietly(targetFile);
            }
            // Use Files.write() instead of deprecated FileUtils.writeStringToFile()
            Files.write(targetFile.toPath(), queueItemString.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e){
            log.error("Unable to save shr obs for later synchronization: ", e);
        }
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
