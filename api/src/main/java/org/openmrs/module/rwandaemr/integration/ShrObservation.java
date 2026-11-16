package org.openmrs.module.rwandaemr.integration;

import javax.validation.constraints.NotNull;

import org.hl7.fhir.r4.model.Observation;
import org.openmrs.module.rwandaemr.object.Quantity;

import lombok.Data;

@Data
public class ShrObservation {
    
    private final Observation observation;

    public ShrObservation(@NotNull Observation observation){
        this.observation = observation;
    }

    public String getObservationUuid(){
        if(observation.hasId()){
            return observation.getIdBase();
        }
        return null;
    }

    public String getValueText(){
        if(observation.hasValueStringType()){
            return observation.getValueStringType().getValueAsString();
        }
        return null;
    }

    public String getCodeDisplay(){
        if(observation.hasCode()){
            return observation.getCode().getCodingFirstRep().getDisplay();
        }
        return null;
    }

    public String getCodeCode(){
        if(observation.hasCode()){
            return observation.getCode().getCodingFirstRep().getCode();
        }
        return null;
    }

    public String getValueCodeableConceptName(){
        if(observation.hasValueCodeableConcept()){
            return observation.getValueCodeableConcept().getCodingFirstRep().getDisplay();
        }
        return null;
    }

    public String getValueCodeableConceptCode(){
        if(observation.hasValueCodeableConcept()){
            return observation.getValueCodeableConcept().getCodingFirstRep().getCode();
        }
        return null;
    }

    public Quantity getValueQuantity(){
        if(observation.hasValueQuantity()){

            org.hl7.fhir.r4.model.Quantity qty = observation.getValueQuantity();

            Quantity quantity = new Quantity();
            if(qty.getValue() != null){
                quantity.value = qty.getValue().doubleValue();
            }
            if(qty.getUnit() != null){
                quantity.unit = qty.getUnit();
            } else {
                quantity.unit = "";
            }
            return quantity;
        }
        return null;
    }
}
