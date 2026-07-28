package org.openmrs.module.rwandaemr.appointment.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Patient;
import org.openmrs.Program;
import org.openmrs.module.rwandaemr.appointment.AppointmentStatus;
import org.openmrs.module.rwandaemr.appointment.AppointmentVisitType;

public class AppointmentBooking extends BaseOpenmrsData {

    private Integer id;
    private AppointmentSchedule schedule;
    private Patient patient;
    private Program program;
    private String visitTypeName;
    private String statusName = AppointmentStatus.CONFIRMED.name();
    private Date requestedAt;
    private String notes;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public AppointmentSchedule getSchedule() {
        return schedule;
    }

    public void setSchedule(AppointmentSchedule schedule) {
        this.schedule = schedule;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public AppointmentVisitType getVisitType() {
        return visitTypeName == null ? null : AppointmentVisitType.valueOf(visitTypeName);
    }

    public void setVisitType(AppointmentVisitType visitType) {
        this.visitTypeName = visitType == null ? null : visitType.name();
    }

    public String getVisitTypeName() {
        return visitTypeName;
    }

    public void setVisitTypeName(String visitTypeName) {
        this.visitTypeName = visitTypeName;
    }

    public AppointmentStatus getStatus() {
        return statusName == null ? null : AppointmentStatus.valueOf(statusName);
    }

    public void setStatus(AppointmentStatus status) {
        this.statusName = status == null ? null : status.name();
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Date getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Date requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
