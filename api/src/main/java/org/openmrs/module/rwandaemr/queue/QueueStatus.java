package org.openmrs.module.rwandaemr.queue;

public enum QueueStatus {
    WAITING,
    CALLED,
    IN_PROGRESS,
    ON_HOLD,
    TRANSFERRED,
    COMPLETED,
    CANCELLED
}
