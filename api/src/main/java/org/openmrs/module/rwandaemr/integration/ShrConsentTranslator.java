package org.openmrs.module.rwandaemr.integration;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShrConsentTranslator {
    protected Log log = LogFactory.getLog(getClass());

    private final RwandaEmrConfig rwandaEmrConfig;

    public ShrConsentTranslator (
        @Autowired RwandaEmrConfig rwandaEmrConfig
    ) {
        this.rwandaEmrConfig = rwandaEmrConfig;
    }

    public void updateShrConsent(@NotNull ShrConsent shrConsent, @Nonnull Encounter encounter) {
        //Check if consent is null (should not happen if created properly)
        if(shrConsent.getConsent() == null){
            log.error("Cannot update consent: ShrConsent was created with null consent object. Please create ShrConsent with a new Consent() object.");
            throw new IllegalStateException("Cannot update consent: ShrConsent was created with null consent object. Please create ShrConsent with a new Consent() object.");
        }
        
        Patient patient = encounter.getPatient();
        if(patient != null){
            PatientIdentifier patientIdentifier = patient.getPatientIdentifier(rwandaEmrConfig.getUPID());
            if(patientIdentifier != null){
                // log.info(patientIdentifier.getIdentifier() + " Now trying add subject as we have UPID");
                Coding idTypeCoding = new Coding().setCode(patientIdentifier.getIdentifierType().getName()).setDisplay(patientIdentifier.getIdentifierType().getDescription());
                CodeableConcept idType = new CodeableConcept().addCoding(idTypeCoding);
                Identifier identifier = new Identifier().setType(idType).setValue(patientIdentifier.getIdentifier());
                org.hl7.fhir.r4.model.Reference subject = new org.hl7.fhir.r4.model.Reference().setReference("Patient/" + patientIdentifier.getIdentifier()).setType("Patient").setIdentifier(identifier).setDisplay(patient.getFamilyName() + " " + patient.getGivenName());
                // shrEncounter.getEncounter().setSubject(subject);
                shrConsent.getConsent().setPatient(subject);
            }
        }

        //Adding the Scope to be Privacy Consent
        Coding scopeCoding = new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/consentscope")
                .setCode("patient-privacy")
                .setDisplay("Privacy Consent");

        CodeableConcept scope = new CodeableConcept()
                .addCoding(scopeCoding)
                .setText("Privacy Consent");
        shrConsent.getConsent().setScope(scope);

        // Adding Category
        CodeableConcept category = new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode")
                        .setCode("INFA")
                        .setDisplay("Information access")
                ).setText("Information Access");
        shrConsent.getConsent().addCategory(category);

        //Adding the Date Time
        shrConsent.getConsent().setDateTime(encounter.getDateCreated());
        
        //Adding Organization performer
        Reference organizatioReference = new Reference();
        organizatioReference.setReference("Organization/"+ encounter.getLocation().getUuid());
        organizatioReference.setType("Location");
        organizatioReference.setDisplay(encounter.getLocation().getName());

        // Adding Policy Rule
        shrConsent.getConsent().setPolicyRule(new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/consentpolicycodes")
                        .setCode("opt-in")
                        .setDisplay("Opt-in consent")
                ));
        shrConsent.getConsent().setStatus(Consent.ConsentState.ACTIVE);

        log.info("SHR Consent Translation Update process ended!!!!");
    }
}
