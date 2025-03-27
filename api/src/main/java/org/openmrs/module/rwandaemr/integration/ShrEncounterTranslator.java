package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

import javax.annotation.Nonnull;

@Component
public class ShrEncounterTranslator {
    protected Log log = LogFactory.getLog(getClass());

    private final EncounterService encounterService;

    public ShrEncounterTranslator(
        @Autowired EncounterService encounterService
        ){
        this.encounterService = encounterService;
    }

    public Encounter toEncounter(@Nonnull ShrEncounter shrEncounter){
        Encounter encounter = new Encounter();

        if(shrEncounter.getEncounterUuid() != null){
            String[] uuid_parts = shrEncounter.getEncounterUuid().split("/");

            for(String uuid : uuid_parts){
                try {
                    UUID.fromString(uuid);
                    encounter.setUuid(uuid);
                    break;
                } catch(IllegalArgumentException iae){
                    //log.debug(uuid + " is not valid UUID.");
                }
            }
        }

        if(shrEncounter.getEncounterSubject().hasType() && shrEncounter.getEncounterTypeString().equalsIgnoreCase("patient")){
            Patient p = new Patient();
            
            p.setIdentifiers(null);
            encounter.setPatient(p);
        }

        if(shrEncounter.getEncounterLocation() != null){
            // log.debug("Encounter Location: " + shrEncounter.getEncounterLocation().getLocation().getDisplay());
            Location location = new Location();
            location.setName( shrEncounter.getEncounterLocation().getLocation().getDisplay() );
            
            encounter.setLocation(location);
        }

        if(shrEncounter.getEncounterDate() != null){
            encounter.setEncounterDatetime(shrEncounter.getEncounterDate());
        }

        if(shrEncounter.getEncounterTypeString() != null){
            // encounter.setEn
            EncounterType encounterType = encounterService.getEncounterTypeByUuid(shrEncounter.getEncounterTypeString());
            if(encounterType != null){
                encounter.setEncounterType(encounterType);
            }
        }

        if(shrEncounter.getEncounterTypeDisplay() != null){
            encounter.setVoidReason(shrEncounter.getEncounterTypeDisplay());
        }
        return encounter;
    }
}
