package org.openmrs.module.rwandaemr.queue;

public enum QueuePriority {
    EMERGENCY(0),
    ELDERLY(10),
    PREGNANT(20),
    CHILD(30),
    DISABILITY(40),
    NORMAL(100);

    private final int sortWeight;

    QueuePriority(int sortWeight) {
        this.sortWeight = sortWeight;
    }

    public int getSortWeight() {
        return sortWeight;
    }
}
