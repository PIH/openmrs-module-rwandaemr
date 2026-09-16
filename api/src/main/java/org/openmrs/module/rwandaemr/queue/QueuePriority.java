package org.openmrs.module.rwandaemr.queue;

public enum QueuePriority {
    EMERGENCY(0, "Emergency"),
    ELDERLY(10, "Elderly"),
    PREGNANT(20, "Pregnant"),
    CHILD(30, "Child"),
    DISABILITY(40, "Disability"),
    NORMAL(100, "Not Emergency");

    private final int sortWeight;
    private final String displayName;

    QueuePriority(int sortWeight, String displayName) {
        this.sortWeight = sortWeight;
        this.displayName = displayName;
    }

    public int getSortWeight() {
        return sortWeight;
    }

    public String getDisplayName() {
        return displayName;
    }
}
