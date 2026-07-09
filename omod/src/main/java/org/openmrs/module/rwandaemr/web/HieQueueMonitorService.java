/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr.web;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.rwandaemr.integration.UpdateClientRegistryTask;
import org.openmrs.module.rwandaemr.integration.UpdateShrEncounterTask;
import org.openmrs.module.rwandaemr.integration.UpdateShrObsTask;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and manages the HIE file-backed queues used by the integration listeners.
 */
@Component
public class HieQueueMonitorService {

    private static final Log log = LogFactory.getLog(HieQueueMonitorService.class);

    public static final String QUEUE_ALL = "all";
    public static final String STATUS_ALL = "all";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXHAUSTED = "exhausted";
    public static final String STATUS_UNREADABLE = "unreadable";

    private static final int MAX_ATTEMPTS = 5;
    private static final int RESPONSE_PREVIEW_LENGTH = 500;
    private static final TypeReference<Map<String, Object>> QUEUE_ITEM_TYPE = new TypeReference<Map<String, Object>>() {};
    private static DaemonToken daemonToken;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, QueueDefinition> queueDefinitions = new LinkedHashMap<String, QueueDefinition>();

    public static void setDaemonToken(DaemonToken daemonToken) {
        HieQueueMonitorService.daemonToken = daemonToken;
    }

    public HieQueueMonitorService() {
        queueDefinitions.put("shr-encounter", new QueueDefinition(
                "shr-encounter",
                "SHR Encounters",
                "shr-encounter-queue",
                "encounterUuid",
                UpdateShrEncounterTask.class));
        queueDefinitions.put("shr-obs", new QueueDefinition(
                "shr-obs",
                "SHR Observations",
                "shr-obs-queue",
                "obsUuid",
                UpdateShrObsTask.class));
        queueDefinitions.put("client-registry", new QueueDefinition(
                "client-registry",
                "Client Registry Patients",
                "client-registry-queue",
                "patientUuid",
                UpdateClientRegistryTask.class));
    }

    public QueueDashboard getDashboard(String queueType, String status, String search) {
        List<QueueItem> allItems = readQueueItems(QUEUE_ALL);
        List<QueueItem> filteredItems = new ArrayList<QueueItem>();
        String normalizedQueueType = StringUtils.isBlank(queueType) ? QUEUE_ALL : queueType;
        String normalizedStatus = normalizeStatus(status);
        String normalizedSearch = StringUtils.trimToNull(search);

        for (QueueItem item : allItems) {
            if (!QUEUE_ALL.equals(normalizedQueueType) && !normalizedQueueType.equals(item.getDefinition().getType())) {
                continue;
            }
            if (!STATUS_ALL.equals(normalizedStatus) && !normalizedStatus.equals(item.getStatus())) {
                continue;
            }
            if (normalizedSearch != null && !matchesSearch(item, normalizedSearch)) {
                continue;
            }
            filteredItems.add(item);
        }

        return new QueueDashboard(getQueueSummaries(allItems), filteredItems);
    }

    public Collection<QueueDefinition> getQueueDefinitions() {
        return queueDefinitions.values();
    }

    public List<String> getStatusOptions() {
        return Arrays.asList(STATUS_ALL, STATUS_PENDING, STATUS_FAILED, STATUS_EXHAUSTED, STATUS_UNREADABLE);
    }

    public String retryItem(String queueType, String fileName) throws Exception {
        QueueDefinition definition = getQueueDefinition(queueType);
        File file = resolveQueueFile(definition, fileName);
        Map<String, Object> payload = mapper.readValue(file, QUEUE_ITEM_TYPE);
        payload.put("numAttempts", 0);
        payload.remove("latestAttemptDatetime");
        payload.remove("latestAttemptResponse");
        mapper.writeValue(file, payload);
        return "Retry enabled for " + definition.getDisplayName() + " item " + file.getName();
    }

    public String deleteItem(String queueType, String fileName) throws Exception {
        QueueDefinition definition = getQueueDefinition(queueType);
        File file = resolveQueueFile(definition, fileName);
        if (!file.delete() && file.exists()) {
            throw new IllegalStateException("Unable to delete queue file " + file.getName());
        }
        return "Deleted " + definition.getDisplayName() + " item " + file.getName();
    }

    public String processQueue(String queueType) {
        String selectedQueueType = StringUtils.isBlank(queueType) ? QUEUE_ALL : queueType;
        if (QUEUE_ALL.equals(selectedQueueType)) {
            processAllQueues();
            return "Started processing for all HIE queues in dependency order";
        }

        QueueDefinition definition = getQueueDefinition(selectedQueueType);
        processQueue(definition);
        return "Started processing for " + definition.getDisplayName();
    }

    private List<QueueSummary> getQueueSummaries(List<QueueItem> items) {
        Map<String, QueueSummary> summariesByQueue = new LinkedHashMap<String, QueueSummary>();
        List<QueueSummary> summaries = new ArrayList<QueueSummary>();
        for (QueueDefinition definition : queueDefinitions.values()) {
            QueueSummary summary = new QueueSummary(definition);
            summariesByQueue.put(definition.getType(), summary);
            summaries.add(summary);
        }
        for (QueueItem item : items) {
            QueueSummary summary = summariesByQueue.get(item.getDefinition().getType());
            if (summary != null) {
                summary.increment(item.getStatus());
            }
        }
        return summaries;
    }

    private List<QueueItem> readQueueItems(String queueType) {
        List<QueueDefinition> definitions = new ArrayList<QueueDefinition>();
        if (StringUtils.isBlank(queueType) || QUEUE_ALL.equals(queueType)) {
            definitions.addAll(queueDefinitions.values());
        } else {
            definitions.add(getQueueDefinition(queueType));
        }

        List<QueueItem> items = new ArrayList<QueueItem>();
        for (QueueDefinition definition : definitions) {
            File queueDirectory = getQueueDirectory(definition);
            File[] files = queueDirectory.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".json")) {
                    continue;
                }
                items.add(readQueueItem(definition, file));
            }
        }
        items.sort(new Comparator<QueueItem>() {
            @Override
            public int compare(QueueItem left, QueueItem right) {
                return Long.compare(left.getSortTime(), right.getSortTime());
            }
        });
        return items;
    }

    private QueueItem readQueueItem(QueueDefinition definition, File file) {
        try {
            Map<String, Object> payload = mapper.readValue(file, QUEUE_ITEM_TYPE);
            String entityUuid = stringValue(payload.get(definition.getUuidField()));
            Date eventDatetime = toDate(payload.get("eventDatetime"));
            Date latestAttemptDatetime = toDate(payload.get("latestAttemptDatetime"));
            Integer numAttempts = toInteger(payload.get("numAttempts"));
            String latestAttemptResponse = stringValue(payload.get("latestAttemptResponse"));
            String status = getStatus(numAttempts);
            return new QueueItem(
                    definition,
                    file.getName(),
                    entityUuid,
                    stringValue(payload.get("eventType")),
                    eventDatetime,
                    numAttempts,
                    latestAttemptDatetime,
                    latestAttemptResponse,
                    status,
                    new Date(file.lastModified()),
                    file.length());
        }
        catch (Exception e) {
            return QueueItem.unreadable(definition, file, e.getMessage());
        }
    }

    private void processQueue(QueueDefinition definition) {
        if (daemonToken == null) {
            throw new IllegalStateException("Daemon token is not set for " + getClass().getSimpleName());
        }
        Daemon.runInDaemonThread(() -> {
            try {
                processQueueNow(definition);
            }
            catch (Exception e) {
                log.error("An error occurred while processing " + definition.getDisplayName() + " manually", e);
            }
        }, daemonToken);
    }

    private void processAllQueues() {
        if (daemonToken == null) {
            throw new IllegalStateException("Daemon token is not set for " + getClass().getSimpleName());
        }
        Daemon.runInDaemonThread(() -> {
            long startedAt = System.currentTimeMillis();
            log.info("Starting manual HIE processing for all queues in dependency order");
            for (QueueDefinition definition : getAllQueueProcessingOrder()) {
                try {
                    processQueueNow(definition);
                }
                catch (Exception e) {
                    log.error("An error occurred while processing " + definition.getDisplayName() + " manually", e);
                }
            }
            log.info("Completed manual HIE processing for all queues in " +
                    (System.currentTimeMillis() - startedAt) + " ms");
        }, daemonToken);
    }

    private List<QueueDefinition> getAllQueueProcessingOrder() {
        List<QueueDefinition> orderedDefinitions = new ArrayList<QueueDefinition>();
        addQueueDefinitionIfPresent(orderedDefinitions, "client-registry");
        addQueueDefinitionIfPresent(orderedDefinitions, "shr-encounter");
        addQueueDefinitionIfPresent(orderedDefinitions, "shr-obs");
        return orderedDefinitions;
    }

    private void addQueueDefinitionIfPresent(List<QueueDefinition> definitions, String queueType) {
        QueueDefinition definition = queueDefinitions.get(queueType);
        if (definition != null) {
            definitions.add(definition);
        }
    }

    private void processQueueNow(QueueDefinition definition) throws Exception {
        long startedAt = System.currentTimeMillis();
        Context.openSession();
        try {
            log.info("Starting manual HIE queue processing for " + definition.getDisplayName());
            Runnable taskInstance = definition.getTaskClass().getDeclaredConstructor().newInstance();
            taskInstance.run();
            log.info("Completed manual HIE queue processing for " + definition.getDisplayName() +
                    " in " + (System.currentTimeMillis() - startedAt) + " ms");
        } finally {
            if (Context.isSessionOpen()) {
                Context.closeSession();
            }
        }
    }

    private boolean matchesSearch(QueueItem item, String search) {
        String lowerSearch = search.toLowerCase(Locale.ENGLISH);
        return containsIgnoreCase(item.getEntityUuid(), lowerSearch) ||
                containsIgnoreCase(item.getEventType(), lowerSearch) ||
                containsIgnoreCase(item.getFileName(), lowerSearch) ||
                containsIgnoreCase(item.getLatestAttemptResponse(), lowerSearch);
    }

    private boolean containsIgnoreCase(String value, String lowerSearch) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(lowerSearch);
    }

    private String normalizeStatus(String status) {
        String normalized = (StringUtils.isBlank(status) ? STATUS_ALL : status).trim().toLowerCase(Locale.ENGLISH);
        if (getStatusOptions().contains(normalized)) {
            return normalized;
        }
        return STATUS_ALL;
    }

    private QueueDefinition getQueueDefinition(String queueType) {
        QueueDefinition definition = queueDefinitions.get(queueType);
        if (definition == null) {
            throw new IllegalArgumentException("Unsupported HIE queue type: " + queueType);
        }
        return definition;
    }

    private File getQueueDirectory(QueueDefinition definition) {
        File directory = OpenmrsUtil.getDirectoryInApplicationDataDirectory(definition.getDirectoryName());
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private File resolveQueueFile(QueueDefinition definition, String fileName) throws Exception {
        if (StringUtils.isBlank(fileName) || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid queue file name");
        }
        File queueDirectory = getQueueDirectory(definition);
        File file = new File(queueDirectory, fileName);
        String directoryPath = queueDirectory.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        if (!filePath.startsWith(directoryPath + File.separator)) {
            throw new IllegalArgumentException("Invalid queue file path");
        }
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Queue file does not exist: " + fileName);
        }
        return file;
    }

    private String getStatus(Integer numAttempts) {
        if (numAttempts == null || numAttempts == 0) {
            return STATUS_PENDING;
        }
        if (numAttempts > MAX_ATTEMPTS) {
            return STATUS_EXHAUSTED;
        }
        return STATUS_FAILED;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    private Date toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        String dateString = String.valueOf(value).trim();
        if (StringUtils.isBlank(dateString)) {
            return null;
        }
        try {
            return new Date(Long.parseLong(dateString));
        }
        catch (NumberFormatException e) {
            return parseDate(dateString);
        }
    }

    private Date parseDate(String dateString) {
        List<String> dateFormats = Arrays.asList(
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ss");
        for (String format : dateFormats) {
            try {
                return new SimpleDateFormat(format, Locale.ENGLISH).parse(dateString);
            }
            catch (ParseException e) {
                // Try the next known format.
            }
        }
        return null;
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(date);
    }

    private static String preview(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        if (value.length() <= RESPONSE_PREVIEW_LENGTH) {
            return value;
        }
        return value.substring(0, RESPONSE_PREVIEW_LENGTH) + "...";
    }

    public static class QueueDashboard {
        private final List<QueueSummary> summaries;
        private final List<QueueItem> items;

        public QueueDashboard(List<QueueSummary> summaries, List<QueueItem> items) {
            this.summaries = summaries;
            this.items = items;
        }

        public List<QueueSummary> getSummaries() {
            return summaries;
        }

        public List<QueueItem> getItems() {
            return items;
        }
    }

    public static class QueueDefinition {
        private final String type;
        private final String displayName;
        private final String directoryName;
        private final String uuidField;
        private final Class<? extends Runnable> taskClass;

        public QueueDefinition(String type, String displayName, String directoryName, String uuidField, Class<? extends Runnable> taskClass) {
            this.type = type;
            this.displayName = displayName;
            this.directoryName = directoryName;
            this.uuidField = uuidField;
            this.taskClass = taskClass;
        }

        public String getType() {
            return type;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public String getUuidField() {
            return uuidField;
        }

        public Class<? extends Runnable> getTaskClass() {
            return taskClass;
        }
    }

    public static class QueueSummary {
        private final QueueDefinition definition;
        private int total;
        private int pending;
        private int failed;
        private int exhausted;
        private int unreadable;

        public QueueSummary(QueueDefinition definition) {
            this.definition = definition;
        }

        public void increment(String status) {
            total++;
            if (STATUS_PENDING.equals(status)) {
                pending++;
            } else if (STATUS_FAILED.equals(status)) {
                failed++;
            } else if (STATUS_EXHAUSTED.equals(status)) {
                exhausted++;
            } else if (STATUS_UNREADABLE.equals(status)) {
                unreadable++;
            }
        }

        public QueueDefinition getDefinition() {
            return definition;
        }

        public int getTotal() {
            return total;
        }

        public int getPending() {
            return pending;
        }

        public int getFailed() {
            return failed;
        }

        public int getExhausted() {
            return exhausted;
        }

        public int getUnreadable() {
            return unreadable;
        }
    }

    public static class QueueItem {
        private final QueueDefinition definition;
        private final String fileName;
        private final String entityUuid;
        private final String eventType;
        private final Date eventDatetime;
        private final Integer numAttempts;
        private final Date latestAttemptDatetime;
        private final String latestAttemptResponse;
        private final String status;
        private final Date lastModifiedDatetime;
        private final long fileSize;

        public QueueItem(QueueDefinition definition, String fileName, String entityUuid, String eventType, Date eventDatetime,
                         Integer numAttempts, Date latestAttemptDatetime, String latestAttemptResponse, String status,
                         Date lastModifiedDatetime, long fileSize) {
            this.definition = definition;
            this.fileName = fileName;
            this.entityUuid = entityUuid;
            this.eventType = eventType;
            this.eventDatetime = eventDatetime;
            this.numAttempts = numAttempts;
            this.latestAttemptDatetime = latestAttemptDatetime;
            this.latestAttemptResponse = latestAttemptResponse;
            this.status = status;
            this.lastModifiedDatetime = lastModifiedDatetime;
            this.fileSize = fileSize;
        }

        public static QueueItem unreadable(QueueDefinition definition, File file, String message) {
            return new QueueItem(
                    definition,
                    file.getName(),
                    "",
                    "",
                    null,
                    null,
                    null,
                    message,
                    STATUS_UNREADABLE,
                    new Date(file.lastModified()),
                    file.length());
        }

        public QueueDefinition getDefinition() {
            return definition;
        }

        public String getFileName() {
            return fileName;
        }

        public String getEntityUuid() {
            return entityUuid;
        }

        public String getEventType() {
            return eventType;
        }

        public Date getEventDatetime() {
            return eventDatetime;
        }

        public String getEventDatetimeDisplay() {
            return formatDate(eventDatetime);
        }

        public Integer getNumAttempts() {
            return numAttempts;
        }

        public int getAttemptCount() {
            return numAttempts == null ? 0 : numAttempts;
        }

        public Date getLatestAttemptDatetime() {
            return latestAttemptDatetime;
        }

        public String getLatestAttemptDatetimeDisplay() {
            return formatDate(latestAttemptDatetime);
        }

        public String getLatestAttemptResponse() {
            return latestAttemptResponse;
        }

        public String getLatestAttemptResponsePreview() {
            return preview(latestAttemptResponse);
        }

        public String getStatus() {
            return status;
        }

        public Date getLastModifiedDatetime() {
            return lastModifiedDatetime;
        }

        public String getLastModifiedDatetimeDisplay() {
            return formatDate(lastModifiedDatetime);
        }

        public long getFileSize() {
            return fileSize;
        }

        public long getSortTime() {
            if (eventDatetime != null) {
                return eventDatetime.getTime();
            }
            return lastModifiedDatetime == null ? 0L : lastModifiedDatetime.getTime();
        }
    }
}
