package org.openmrs.module.rwandaemr.queue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Notifies an external system (the queue web application backend) whenever a {@link QueueEntry}
 * is created or changes state, so that system can broadcast live updates (e.g. over WebSocket)
 * without having to poll OpenMRS. Delivery is fire-and-forget: a webhook that is unreachable or
 * slow must never cause a queue operation to fail or block, so failures are only logged.
 */
@Component
public class QueueWebhookNotifier {

    public static final String WEBHOOK_URL_GP = "rwandaemr.queue.webhookUrl";

    public static final String WEBHOOK_SECRET_GP = "rwandaemr.queue.webhookSecret";

    public static final String WEBHOOK_SECRET_HEADER = "X-RwandaEMR-Queue-Secret";

    protected Log log = LogFactory.getLog(getClass());

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rwandaemr-queue-webhook");
            t.setDaemon(true);
            return t;
        }
    });

    /**
     * Queues delivery of a queue-change notification. If called within an active database
     * transaction, delivery is deferred until after that transaction commits, so the receiving
     * system never sees a notification for a change it can't yet read back via the REST API.
     */
    public void notifyChanged(QueueEntry queueEntry, String event) {
        if (queueEntry == null) {
            return;
        }
        final String url = getWebhookUrl();
        if (StringUtils.isBlank(url)) {
            return;
        }
        final String secret = getWebhookSecret();
        final String payload = buildPayload(queueEntry, event);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(url, secret, payload);
                }
            });
        } else {
            send(url, secret, payload);
        }
    }

    protected String getWebhookUrl() {
        try {
            return Context.getAdministrationService().getGlobalProperty(WEBHOOK_URL_GP);
        }
        catch (Exception e) {
            return null;
        }
    }

    protected String getWebhookSecret() {
        try {
            return Context.getAdministrationService().getGlobalProperty(WEBHOOK_SECRET_GP);
        }
        catch (Exception e) {
            return null;
        }
    }

    protected void send(final String url, final String secret, final String payload) {
        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                try (CloseableHttpClient client = HttpClients.custom()
                        .setDefaultRequestConfig(RequestConfig.custom()
                                .setConnectTimeout(HttpUtils.CONNECT_TIMEOUT)
                                .setSocketTimeout(HttpUtils.SOCKET_TIMEOUT)
                                .setConnectionRequestTimeout(HttpUtils.CONNECTION_REQUEST_TIMEOUT)
                                .build())
                        .build()) {
                    HttpPost post = new HttpPost(url);
                    post.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
                    if (StringUtils.isNotBlank(secret)) {
                        post.setHeader(WEBHOOK_SECRET_HEADER, secret);
                    }
                    try (CloseableHttpResponse response = client.execute(post)) {
                        int status = response.getStatusLine().getStatusCode();
                        if (status >= 300) {
                            log.warn("Queue webhook POST to " + url + " returned HTTP " + status);
                        }
                    }
                }
                catch (Exception e) {
                    log.warn("Failed to deliver queue webhook to " + url + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Hand-builds a minimal JSON payload rather than pulling in a JSON library, since none is
     * currently on the api module's classpath. Only flat, known-safe fields are included.
     */
    protected String buildPayload(QueueEntry queueEntry, String event) {
        StringBuilder json = new StringBuilder("{");
        appendField(json, "event", event, true);
        appendField(json, "entryUuid", queueEntry.getUuid(), false);
        appendField(json, "status", queueEntry.getStatusName(), false);
        appendField(json, "priority", queueEntry.getPriorityName(), false);
        appendField(json, "queueNumber", queueEntry.getQueueNumber(), false);
        appendField(json, "patientUuid", queueEntry.getPatient() == null ? null : queueEntry.getPatient().getUuid(), false);
        appendField(json, "visitUuid", queueEntry.getVisit() == null ? null : queueEntry.getVisit().getUuid(), false);
        appendField(json, "encounterUuid", queueEntry.getEncounter() == null ? null : queueEntry.getEncounter().getUuid(), false);
        appendField(json, "servicePointUuid", queueEntry.getServicePoint() == null ? null : queueEntry.getServicePoint().getUuid(), false);
        appendField(json, "servicePointName", queueEntry.getServicePoint() == null ? null : queueEntry.getServicePoint().getName(), false);
        appendField(json, "previousServicePointUuid", queueEntry.getPreviousServicePoint() == null ? null : queueEntry.getPreviousServicePoint().getUuid(), false);
        appendField(json, "arrivalTime", formatDate(queueEntry.getArrivalTime()), false);
        appendField(json, "calledTime", formatDate(queueEntry.getCalledTime()), false);
        appendField(json, "serviceStartTime", formatDate(queueEntry.getServiceStartTime()), false);
        appendField(json, "serviceEndTime", formatDate(queueEntry.getServiceEndTime()), false);
        appendField(json, "completedTime", formatDate(queueEntry.getCompletedTime()), false);
        appendField(json, "dateChanged", formatDate(queueEntry.getDateChanged()), false);
        json.append("}");
        return json.toString();
    }

    private void appendField(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(",");
        }
        json.append("\"").append(name).append("\":");
        if (value == null) {
            json.append("null");
        }
        else {
            json.append("\"").append(jsonEscape(value)).append("\"");
        }
    }

    private String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String formatDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return sdf.format(date);
    }
}
