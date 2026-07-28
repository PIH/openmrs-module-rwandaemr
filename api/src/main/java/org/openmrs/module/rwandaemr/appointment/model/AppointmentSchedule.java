package org.openmrs.module.rwandaemr.appointment.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;

public class AppointmentSchedule extends BaseOpenmrsData {

    private Integer id;
    private Location servicePoint;
    private Date scheduleDate;
    private Integer maximumPatients;
    private Boolean active = true;
    private String notes;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Location getServicePoint() {
        return servicePoint;
    }

    public void setServicePoint(Location servicePoint) {
        this.servicePoint = servicePoint;
    }

    public Date getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(Date scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public Integer getMaximumPatients() {
        return maximumPatients;
    }

    public void setMaximumPatients(Integer maximumPatients) {
        this.maximumPatients = maximumPatients;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
