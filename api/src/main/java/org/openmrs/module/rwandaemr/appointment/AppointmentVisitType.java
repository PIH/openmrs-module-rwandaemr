package org.openmrs.module.rwandaemr.appointment;

public enum AppointmentVisitType {

    INITIAL("Initial"),
    FOLLOW_UP("Follow-Up");

    private final String displayName;

    AppointmentVisitType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
