package org.openmrs.module.rwandaemr.integration;

import java.util.Date;
import javax.validation.constraints.NotNull;
import org.hl7.fhir.r4.model.Encounter;

import lombok.Data;

@Data
public class ShrEncounter {
    
    private final Encounter encounter;

    public ShrEncounter(@NotNull Encounter encounter){
        this.encounter = encounter;
    }

    public String getEncounterUuid(){
        if(encounter.hasId()){
            return encounter.getIdBase();
        }
        return null;
    }

    public Encounter.EncounterLocationComponent getEncounterLocation(){
        if(encounter.hasLocation()){
            return encounter.getLocationFirstRep();
        }
        return null;
    }

    public String getEncounterTypeString(){
        if(encounter.hasType()){
            return encounter.getTypeFirstRep().getCodingFirstRep().getCode();
        }
        return null;
    }

    public String getEncounterTypeDisplay(){
        if(encounter.hasType()){
            return encounter.getTypeFirstRep().getCodingFirstRep().getDisplay();
        }
        return null;
    }

    public Encounter.EncounterParticipantComponent getEncounterSubject(){
        if(encounter.hasSubject()){
            return encounter.getParticipantFirstRep();
        }
        return null;
    }

    public Date getEncounterDate(){
        if(encounter.hasPeriod()){
            return encounter.getPeriod().getStart();
        }
        return null;
    }
}
