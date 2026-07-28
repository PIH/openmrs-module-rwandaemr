package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

final class QueueWaitingTime {

    private static final long MILLIS_PER_MINUTE = 60L * 1000L;

    private QueueWaitingTime() {
    }

    static Map<Integer, String> formatByEntryId(List<QueueEntry> entries, Date referenceTime) {
        Map<Integer, String> waitingTimes = new LinkedHashMap<Integer, String>();
        for (QueueEntry entry : entries) {
            if (entry.getId() != null) {
                waitingTimes.put(entry.getId(), format(entry, referenceTime));
            }
        }
        return waitingTimes;
    }

    static String format(QueueEntry entry, Date referenceTime) {
        if (entry == null || entry.getArrivalTime() == null) {
            return "";
        }

        Date waitingEnd = entry.getCalledTime();
        if (waitingEnd == null && isStillWaiting(entry.getStatus())) {
            waitingEnd = referenceTime;
        }
        if (waitingEnd == null) {
            return "";
        }

        long elapsedMillis = Math.max(0L, waitingEnd.getTime() - entry.getArrivalTime().getTime());
        long totalMinutes = elapsedMillis / MILLIS_PER_MINUTE;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours == 0L) {
            return totalMinutes + " min";
        }
        return hours + (hours == 1L ? " hr " : " hrs ") + minutes + " min";
    }

    private static boolean isStillWaiting(QueueStatus status) {
        return QueueStatus.WAITING.equals(status) || QueueStatus.ON_HOLD.equals(status);
    }
}
