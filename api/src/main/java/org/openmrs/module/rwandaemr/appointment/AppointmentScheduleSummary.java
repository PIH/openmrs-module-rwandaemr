package org.openmrs.module.rwandaemr.appointment;

import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public class AppointmentScheduleSummary {

    private final AppointmentSchedule schedule;
    private final int bookedPatients;

    public AppointmentScheduleSummary(AppointmentSchedule schedule, int bookedPatients) {
        this.schedule = schedule;
        this.bookedPatients = bookedPatients;
    }

    public AppointmentSchedule getSchedule() {
        return schedule;
    }

    public int getBookedPatients() {
        return bookedPatients;
    }

    public int getRemainingCapacity() {
        int maximum = schedule == null || schedule.getMaximumPatients() == null
                ? 0 : schedule.getMaximumPatients();
        return Math.max(0, maximum - bookedPatients);
    }

    public boolean isAvailable() {
        return schedule != null && Boolean.TRUE.equals(schedule.getActive()) && getRemainingCapacity() > 0;
    }
}
