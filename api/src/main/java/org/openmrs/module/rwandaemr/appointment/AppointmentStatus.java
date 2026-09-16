package org.openmrs.module.rwandaemr.appointment;

public enum AppointmentStatus {
    REQUESTED(true),
    CONFIRMED(true),
    PRESENT(true),
    COMPLETED(true),
    CANCELLED(false),
    NO_SHOW(false);

    private final boolean usesCapacity;

    AppointmentStatus(boolean usesCapacity) {
        this.usesCapacity = usesCapacity;
    }

    public boolean usesCapacity() {
        return usesCapacity;
    }
}
