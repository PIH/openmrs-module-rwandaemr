package org.openmrs.module.rwandaemr.integration;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.r4.model.Provenance.ProvenanceAgentComponent;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShrObsTranslator {
    protected Log log = LogFactory.getLog(getClass());

    private final RwandaEmrConfig rwandaEmrConfig;

    public ShrObsTranslator(
        @Autowired RwandaEmrConfig rwandaEmrConfig
    ){
        this.rwandaEmrConfig = rwandaEmrConfig;
    }

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
                    //log.debug(uuid + " is not valid UUID.");
                }
            }
        }

        if(shrObservation.getValueText() != null){
            //log.debug(shrObservation.getValueText());
            obs.setValueText(shrObservation.getValueText());
        }else if(shrObservation.getCodeCode() != null){
            obs.setValueText(shrObservation.getValueCodeableConceptName());
        }

        if(shrObservation.getCodeCode() != null){
            obs.setComment(shrObservation.getCodeDisplay());
        }

        if(shrObservation.getValueQuantity() != null){
            obs.setValueText(shrObservation.getValueQuantity().value + " " + shrObservation.getValueQuantity().unit);
        }

        return obs;
    }

    public void  updateShrObservation(@NotNull ShrObservation shrObservation, @Nonnull Obs obs){
        Patient patient = obs.getEncounter().getPatient();

        if(patient != null){
            PatientIdentifier patientIdentifier = patient.getPatientIdentifier(rwandaEmrConfig.getUPID());

            Coding idTypeCoding = new Coding().setCode(patientIdentifier.getIdentifierType().getName()).setDisplay(patientIdentifier.getIdentifierType().getDescription());
            CodeableConcept idType = new CodeableConcept().addCoding(idTypeCoding);
            Identifier identifier = new Identifier().setType(idType).setValue(patientIdentifier.getIdentifier());
            Reference subject = new Reference().setReference("Patient/" + patientIdentifier.getIdentifier()).setType("Patient").setIdentifier(identifier).setDisplay(patient.getFamilyName() + " " + patient.getGivenName());

            shrObservation.getObservation().setSubject(subject);
        }

        //Make sure to add the status which is final
        shrObservation.getObservation().setStatus(ObservationStatus.FINAL);

        //
        User creator = obs.getCreator();
        if(creator != null){
            //Mak sure to add the agent who saved the observation
            Provenance provenance = new Provenance();
            provenance.setId(creator.getUuid());
            provenance.setRecorded(obs.getObsDatetime());

            Coding activityCoding = new Coding().setSystem("http://terminology.hl7.org/CodeSystemv3-DataOperation").setCode("CREATE").setDisplay("create");
            provenance.setActivity(new CodeableConcept().addCoding(activityCoding));

            ProvenanceAgentComponent agentComponent = new ProvenanceAgentComponent();
            agentComponent.setType(new CodeableConcept().addCoding(new Coding().setSystem("http://terminology.hl7.org/CodeSystemprovenance-participant-type").setCode("author").setDisplay("Author")));
            Set<Role> roles = creator.getAllRoles();
            Role role = roles.iterator().next();
            agentComponent.addRole(new CodeableConcept().addCoding(new Coding().setSystem("http://terminology.hl7.org/CodeSystemv3-ParticipationType").setCode(role.getRole()).setDisplay(role.getName())));
            Reference who = new Reference().setReference("Practitioner/" + creator.getUuid()).setType("Practitioner").setDisplay(creator.getFamilyName() + " " + creator.getGivenName());
            agentComponent.setWho(who);
            provenance.addAgent(agentComponent);

            shrObservation.getObservation().addContained(provenance);

            //Adding missing performer paremeters too.
            Reference practitioner = new Reference()
                .setReference("Practitioner/" + creator.getUuid())
                .setType("Practitioner")
                .setDisplay(creator.getFamilyName() + " " + creator.getGivenName())
                .setIdentifier(new Identifier().setValue(creator.getSystemId()))
            ;

            shrObservation.getObservation().addPerformer(practitioner);

        }

        //Make sure to category
        if(obs.getEncounter() != null){
            Reference encounterReference = new Reference().setReference("Encounter/" + obs.getEncounter().getUuid()).setType("Encounter");
            shrObservation.getObservation().setEncounter(encounterReference);

            //Here use the encounter location to add HealthcareService which was missing
            Reference healthCareServiceReference = new Reference()
            .setReference("HealthcareService/" + obs.getEncounter().getLocation().getUuid())
            .setType("HealthcareService")
            .setDisplay(obs.getEncounter().getLocation().getName())
            .setIdentifier(new Identifier().setValue(obs.getEncounter().getLocation().getName()))
            ;

            shrObservation.getObservation().addPerformer(healthCareServiceReference);

            //Add the missing Location parameters
            if(obs.getEncounter().getLocation().getCityVillage() != null){
                Reference locationReference = new Reference()
                .setReference("Location/" + obs.getEncounter().getLocation().getUuid())
                .setType("Location")
                .setDisplay(obs.getEncounter().getLocation().getCityVillage())
                .setIdentifier(new Identifier().setValue(obs.getEncounter().getLocation().getCityVillage()))
                ;
                shrObservation.getObservation().addPerformer(locationReference);
            }
        }

        if(obs.getDateCreated() != null){
            shrObservation.getObservation().setEffective(new DateTimeType(obs.getDateCreated()));
            shrObservation.getObservation().setIssued(obs.getDateCreated());
        }

        if(obs.getUuid() != null){
            shrObservation.getObservation().setId(obs.getUuid());
        }

        if(obs.getValueText() != null){
            shrObservation.getObservation().setValue(new StringType(obs.getValueText()));
        }

        if(obs.getEncounter().getEncounterType() != null){
            EncounterType encounterType = obs.getEncounter().getEncounterType();
            Coding categoryCoding = new Coding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode(encounterType.getName()).setDisplay(encounterType.getDescription());
            CodeableConcept categoryConcept = new CodeableConcept().addCoding(categoryCoding);

            shrObservation.getObservation().addCategory(categoryConcept);
        }

        if(obs.getConcept() != null){
            Concept obsConcept = obs.getConcept();

            Coding obsCode = new Coding().setCode(obsConcept.getUuid()).setDisplay(obsConcept.getName().getName());

            CodeableConcept obsCodeableConcept = new CodeableConcept().addCoding(obsCode);
            shrObservation.getObservation().setCode(obsCodeableConcept);
        }

        if(obs.hasGroupMembers()){
            Set <Obs> groupMembers = obs.getGroupMembers();
            if(groupMembers != null && !groupMembers.isEmpty()){
                for(Obs memberObs : groupMembers){
                    Reference referenceMember = new Reference().setReference("Observation/" + memberObs.getUuid()).setType("Observation");
                    shrObservation.getObservation().addHasMember(referenceMember);
                }
            }
        }

        if(obs.getValueCoded() != null){
            Concept valueCoded = obs.getValueCoded();
            Coding valueCodedCoding = new Coding().setCode(valueCoded.getUuid()).setDisplay(valueCoded.getDisplayString());
            CodeableConcept codeableConcept = new CodeableConcept().addCoding(valueCodedCoding);
            shrObservation.getObservation().setValue(codeableConcept);
        }
        
        if(obs.getValueNumeric() != null){
            log.debug("Value numeric is found!");
            Concept obs_concept = obs.getConcept();
            try{
                // Here are having everything
                ConceptNumeric conceptNumeric = Context.getConceptService().getConceptNumeric(obs_concept.getConceptId()); // (ConceptNumeric) obs_concept;
                if(conceptNumeric != null) {
                    Quantity quantity = new Quantity().setValue(obs.getValueNumeric()).setUnit(conceptNumeric.getUnits());
                    shrObservation.getObservation().setValue(quantity);

                    //here add the reference information
                    Coding typeCoding = new Coding().setSystem("http://terminology.hl7.org/CodeSystem/referencerange-meaning").setCode("normal");
                    CodeableConcept typeCodeableConcept = new CodeableConcept().addCoding(typeCoding);
                    //Make to get the real low and high values

                    Quantity quantity_low = new Quantity().setValue(obs.getValueNumeric()).setUnit(conceptNumeric.getUnits());
                    Quantity quantity_high = new Quantity().setValue(obs.getValueNumeric()).setUnit(conceptNumeric.getUnits());
                    ObservationReferenceRangeComponent rangeComponent = new ObservationReferenceRangeComponent().setLow(quantity_low).setHigh(quantity_high).setType(typeCodeableConcept);

                    shrObservation.getObservation().addReferenceRange(rangeComponent);
                }
            } catch(ClassCastException cce){
                log.error("Unable to get Numeric characteristics of the OBS: " + cce.getMessage());
            }
        }
    }
}