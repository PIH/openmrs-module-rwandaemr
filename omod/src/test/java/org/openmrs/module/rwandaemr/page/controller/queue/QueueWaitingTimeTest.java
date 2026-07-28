package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

public class QueueWaitingTimeTest {

    @Test
    public void shouldFormatElapsedTimeBeforePatientWasCalled() {
        QueueEntry entry = queueEntry(0L, QueueStatus.CALLED);
        entry.setCalledTime(minutesAfterEpoch(65));

        assertEquals("1 hr 5 min", QueueWaitingTime.format(entry, minutesAfterEpoch(90)));
    }

    @Test
    public void shouldUseReferenceTimeWhilePatientIsWaiting() {
        QueueEntry entry = queueEntry(0L, QueueStatus.WAITING);

        assertEquals("42 min", QueueWaitingTime.format(entry, minutesAfterEpoch(42)));
    }

    @Test
    public void shouldContinueCountingWhilePatientIsOnHold() {
        QueueEntry entry = queueEntry(0L, QueueStatus.ON_HOLD);

        assertEquals("2 hrs 10 min", QueueWaitingTime.format(entry, minutesAfterEpoch(130)));
    }

    @Test
    public void shouldReturnBlankWhenPatientWasNeverCalledAndIsNoLongerWaiting() {
        QueueEntry entry = queueEntry(0L, QueueStatus.CANCELLED);

        assertEquals("", QueueWaitingTime.format(entry, minutesAfterEpoch(42)));
    }

    private QueueEntry queueEntry(long arrivalTime, QueueStatus status) {
        QueueEntry entry = new QueueEntry();
        entry.setArrivalTime(new Date(arrivalTime));
        entry.setStatus(status);
        return entry;
    }

    private Date minutesAfterEpoch(long minutes) {
        return new Date(minutes * 60L * 1000L);
    }
}
