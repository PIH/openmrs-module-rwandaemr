package org.openmrs.module.rwandaemr.integration;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.Encounter;

import lombok.Data;

/***
 * Represent the ShrVisit component from HIE aggregated represantation
 */
@Data
public class ShrVisit {
    private String location;
    private List<Encounter> encounters;

    public void setLocation(String location){
        this.location = location;
    }

    public String getLocation(){
        return this.location;
    }

    public void clearEncounters(){
        this.encounters = new ArrayList<>();
    }

    public void addEncounter(Encounter encounter){
        this.encounters.add(encounter);
    }

    public List<Encounter> getEncounters(){
        return this.encounters;
    }
}
