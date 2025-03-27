package org.openmrs.module.rwandaemr.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Obs;
import org.springframework.stereotype.Component;

@Component
public class ShrObsTranslator {
    protected Log log = LogFactory.getLog(getClass());

    public Obs toObs(@Nonnull ShrObservation shrObservation){
        Obs obs = new Obs();

        if(shrObservation.getObservationUuid() != null){
            String[] uuid_parts = shrObservation.getObservationUuid().split("/");

            for(String uuid : uuid_parts){
                try {
                    UUID.fromString(uuid);
                    obs.setUuid(uuid);
                    break;
                } catch(IllegalArgumentException iae){
                    log.debug(uuid + " is not valid UUID.");
                }
            }
        }

        if(shrObservation.getValueText() != null){
            log.debug(shrObservation.getValueText());
            obs.setValueText(shrObservation.getValueText());
        }else if(shrObservation.getCodeCode() != null){
            obs.setValueText(shrObservation.getValueCodeableConceptName());
        }

        if(shrObservation.getCodeCode() != null){
            obs.setComment(shrObservation.getCodeDisplay());
        }

        return obs;
    }
}
