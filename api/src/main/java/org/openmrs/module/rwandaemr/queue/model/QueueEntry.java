package org.openmrs.module.rwandaemr.queue.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueueStatus;

public class QueueEntry extends BaseOpenmrsData {

    private Integer id;
    private Patient patient;
    private Visit visit;
    private Encounter encounter;
    private Location encounterLocation;
    private Location sessionLocation;
    private Location servicePoint;
    private Location previousServicePoint;
    private Concept serviceRequestedConcept;
    private Provider assignedProvider;
    private String priorityName = QueuePriority.NORMAL.name();
    private String statusName = QueueStatus.WAITING.name();
    private String queueNumber;
    private Date arrivalTime;
    private Date calledTime;
    private Date serviceStartTime;
    private Date serviceEndTime;
    private Date completedTime;
    private String comments;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Visit getVisit() {
        return visit;
    }

    public void setVisit(Visit visit) {
        this.visit = visit;
    }

    public Encounter getEncounter() {
        return encounter;
    }

    public void setEncounter(Encounter encounter) {
        this.encounter = encounter;
    }

    public Location getEncounterLocation() {
        return encounterLocation;
    }

    public void setEncounterLocation(Location encounterLocation) {
        this.encounterLocation = encounterLocation;
    }

    public Location getSessionLocation() {
        return sessionLocation;
    }

    public void setSessionLocation(Location sessionLocation) {
        this.sessionLocation = sessionLocation;
    }

    public Location getServicePoint() {
        return servicePoint;
    }

    public void setServicePoint(Location servicePoint) {
        this.servicePoint = servicePoint;
    }

    public Location getPreviousServicePoint() {
        return previousServicePoint;
    }

    public void setPreviousServicePoint(Location previousServicePoint) {
        this.previousServicePoint = previousServicePoint;
    }

    public Concept getServiceRequestedConcept() {
        return serviceRequestedConcept;
    }

    public void setServiceRequestedConcept(Concept serviceRequestedConcept) {
        this.serviceRequestedConcept = serviceRequestedConcept;
    }

    public Provider getAssignedProvider() {
        return assignedProvider;
    }

    public void setAssignedProvider(Provider assignedProvider) {
        this.assignedProvider = assignedProvider;
    }

    public QueuePriority getPriority() {
        return priorityName == null ? null : QueuePriority.valueOf(priorityName);
    }

    public void setPriority(QueuePriority priority) {
        this.priorityName = priority == null ? null : priority.name();
    }

    public String getPriorityName() {
        return priorityName;
    }

    public void setPriorityName(String priorityName) {
        this.priorityName = priorityName;
    }

    public QueueStatus getStatus() {
        return statusName == null ? null : QueueStatus.valueOf(statusName);
    }

    public void setStatus(QueueStatus status) {
        this.statusName = status == null ? null : status.name();
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public String getQueueNumber() {
        return queueNumber;
    }

    public void setQueueNumber(String queueNumber) {
        this.queueNumber = queueNumber;
    }

    public Date getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Date arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Date getCalledTime() {
        return calledTime;
    }

    public void setCalledTime(Date calledTime) {
        this.calledTime = calledTime;
    }

    public Date getServiceStartTime() {
        return serviceStartTime;
    }

    public void setServiceStartTime(Date serviceStartTime) {
        this.serviceStartTime = serviceStartTime;
    }

    public Date getServiceEndTime() {
        return serviceEndTime;
    }

    public void setServiceEndTime(Date serviceEndTime) {
        this.serviceEndTime = serviceEndTime;
    }

    public Date getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(Date completedTime) {
        this.completedTime = completedTime;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
