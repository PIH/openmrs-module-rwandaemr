package org.openmrs.module.rwandaemr.queue.model;

import java.util.Date;

import org.openmrs.BaseOpenmrsObject;
import org.openmrs.User;
import org.openmrs.module.rwandaemr.queue.QueueStatus;

public class QueueStatusHistory extends BaseOpenmrsObject {

    private Integer id;
    private QueueEntry queueEntry;
    private String previousStatusName;
    private String newStatusName;
    private User changedBy;
    private Date dateChanged;
    private String reason;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public QueueEntry getQueueEntry() {
        return queueEntry;
    }

    public void setQueueEntry(QueueEntry queueEntry) {
        this.queueEntry = queueEntry;
    }

    public QueueStatus getPreviousStatus() {
        return previousStatusName == null ? null : QueueStatus.valueOf(previousStatusName);
    }

    public void setPreviousStatus(QueueStatus previousStatus) {
        this.previousStatusName = previousStatus == null ? null : previousStatus.name();
    }

    public String getPreviousStatusName() {
        return previousStatusName;
    }

    public void setPreviousStatusName(String previousStatusName) {
        this.previousStatusName = previousStatusName;
    }

    public QueueStatus getNewStatus() {
        return newStatusName == null ? null : QueueStatus.valueOf(newStatusName);
    }

    public void setNewStatus(QueueStatus newStatus) {
        this.newStatusName = newStatus == null ? null : newStatus.name();
    }

    public String getNewStatusName() {
        return newStatusName;
    }

    public void setNewStatusName(String newStatusName) {
        this.newStatusName = newStatusName;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public Date getDateChanged() {
        return dateChanged;
    }

    public void setDateChanged(Date dateChanged) {
        this.dateChanged = dateChanged;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
