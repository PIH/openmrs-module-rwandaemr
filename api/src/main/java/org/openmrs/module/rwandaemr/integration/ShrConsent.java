package org.openmrs.module.rwandaemr.integration;

import java.util.Date;

import javax.validation.constraints.NotNull;

import org.hl7.fhir.r4.model.Consent;

import lombok.Data;

@Data
public class ShrConsent {
    
    private final Consent consent;
    
    public ShrConsent(@NotNull Consent consent){
        this.consent = consent;
    }

    public String getConsentUuid(){

        if(consent.hasId()){
            return consent.getIdBase();
        }
        return null;
    }

    public Date getConsentDate(){
        if(consent.hasDateTime()){
            return consent.getDateTime();
        }
        return null;
    }

    public String getStatus(){
        if(consent.hasStatus()){
            return consent.getStatus().getDisplay();
        }
        return null;
    }

    public String getScope(){
        if(consent.hasScope()){
            return consent.getScope().getCoding().getFirst().getDisplay();
        }
        return null;
    }

    public String getCategory(){
        if(consent.hasCategory()){
            return consent.getCategoryFirstRep().getCoding().get(0).getDisplay();
        }
        return null;
    }

    public String getPatient(){
        if(consent.hasPatient()){
            return consent.getPatient().getReference().replace("Patient/", "");
        }
        return null;
    }

    public String getPatientName(){
        try {
            if(consent.hasPatient()){
                return consent.getPatient().getDisplay();
            }
        } catch (Exception e){

        }
        return null;
    }
}
