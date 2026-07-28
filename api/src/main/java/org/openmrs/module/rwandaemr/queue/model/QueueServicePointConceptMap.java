package org.openmrs.module.rwandaemr.queue.model;

import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Location;

public class QueueServicePointConceptMap extends BaseOpenmrsData {

    private Integer id;
    private Concept serviceRequestedConcept;
    private Location servicePoint;
    private Boolean active = true;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Concept getServiceRequestedConcept() {
        return serviceRequestedConcept;
    }

    public void setServiceRequestedConcept(Concept serviceRequestedConcept) {
        this.serviceRequestedConcept = serviceRequestedConcept;
    }

    public Location getServicePoint() {
        return servicePoint;
    }

    public void setServicePoint(Location servicePoint) {
        this.servicePoint = servicePoint;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
