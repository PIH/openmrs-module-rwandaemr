package org.openmrs.module.rwandaemr.integration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Encounter.EncounterStatus;
import org.hl7.fhir.r4.model.Provenance.ProvenanceAgentComponent;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.EncounterService;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@Component
public class ShrEncounterTranslator {
    protected Log log = LogFactory.getLog(getClass());

    private final EncounterService encounterService;
    private final RwandaEmrConfig rwandaEmrConfig;

    public ShrEncounterTranslator(
        @Autowired EncounterService encounterService,
        @Autowired RwandaEmrConfig rwandaEmrConfig
        ){
        this.encounterService = encounterService;
        this.rwandaEmrConfig = rwandaEmrConfig;
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

    /**
     * Update SHR encounter with data from OpenMRS encounter information
     * @param shrEncounter
     * @param encounter
     */
    public void updateShrEncounter(@NotNull ShrEncounter shrEncounter, @Nonnull Encounter encounter){
        //Adding Subject reference for HIE To filter UPID
        Patient patient = encounter.getPatient();
        if(patient != null){
            PatientIdentifier patientIdentifier = patient.getPatientIdentifier(rwandaEmrConfig.getUPID());
            if(patientIdentifier != null){
                // log.info(patientIdentifier.getIdentifier() + " Now trying add subject as we have UPID");
                Coding idTypeCoding = new Coding().setCode(patientIdentifier.getIdentifierType().getName()).setDisplay(patientIdentifier.getIdentifierType().getDescription());
                CodeableConcept idType = new CodeableConcept().addCoding(idTypeCoding);
                Identifier identifier = new Identifier().setType(idType).setValue(patientIdentifier.getIdentifier());
                org.hl7.fhir.r4.model.Reference subject = new org.hl7.fhir.r4.model.Reference().setReference("Patient/" + patientIdentifier.getIdentifier()).setType("Patient").setIdentifier(identifier).setDisplay(patient.getFamilyName() + " " + patient.getGivenName());
                shrEncounter.getEncounter().setSubject(subject);
            }
        }

        //Adding the encouter Type on object
        EncounterType encounterType = encounter.getEncounterType();
        if(encounterType != null){
            // log.info(encounterType.getName() + " is used as encounter type");
            Coding typeCoding = new Coding().setSystem("http://fhir.openmrs.org/code-system/encounter-type").setCode(encounterType.getUuid()).setDisplay(encounterType.getName());
            CodeableConcept codeableConceptType = new CodeableConcept().addCoding(typeCoding);
            shrEncounter.getEncounter().addType(codeableConceptType);
        }

        //Adding Class Object
        shrEncounter.getEncounter().setClass_(new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode").setCode("AMB"));
        
        //Adding Participant to the Object
        Set<EncounterProvider> providers = encounter.getEncounterProviders();
        if(!providers.isEmpty()){
            
            // while (providers.iterator().hasNext()) {
                EncounterProvider provider = providers.iterator().next();
                // log.info(provider.getProvider().getUuid() + " is going to be used as encounter provider");
                org.hl7.fhir.r4.model.Reference providerReference = new org.hl7.fhir.r4.model.Reference().setReference("Practitioner/" + provider.getProvider().getUuid()).setType("Practitioner").setIdentifier(new Identifier().setValue(provider.getProvider().getIdentifier())).setDisplay(provider.getProvider().getName());
                shrEncounter.getEncounter().addParticipant().setIndividual(providerReference);
            // }
        }

        //Adding Location field to Object
        Location location = encounter.getLocation();
        if(location.getParentLocation() != null){
            //This will make sure the used location is Hospital level not sub locations
            location = location.getParentLocation();
        }
        if(location != null){
            // log.info(location.getName() + " is going to be used as location");
            org.hl7.fhir.r4.model.Reference locationReference = new org.hl7.fhir.r4.model.Reference().setReference("Location/" + location.getUuid()).setType("Location").setIdentifier(new Identifier().setValue(location.getDescription())).setDisplay(location.getName());
            shrEncounter.getEncounter().addLocation().setLocation(locationReference);
        }

        //Adding the service type
        CodeableConcept serviceType = new CodeableConcept()
                .addCoding(new Coding()
                        .setSystem("http://terminology.hl7.org/CodeSystem/service-type")
                        .setCode("Service/"+ encounter.getLocation().getLocationId())
                        .setDisplay(encounter.getLocation().getName())
                ).setText(encounter.getLocation().getName());
        shrEncounter.getEncounter().setServiceType(serviceType);
        
        //Adding Period field to object
        if(encounter.getEncounterDatetime() != null){
            // log.info(encounter.getEncounterDatetime() + " is being used as Period Start object");
            Period period = new Period().setStart(encounter.getEncounterDatetime());
            shrEncounter.getEncounter().setPeriod(period);
        }

        //Adding Contained field to Object
        User creator = encounter.getCreator();
        if(creator != null){
            // log.info(creator.getUuid() + " is going to be used for practitionaer");
            Provenance provenance = new Provenance();
            provenance.setId(creator.getUuid());
            provenance.setRecorded(encounter.getEncounterDatetime());

            Coding activityCoding = new Coding().setSystem("http://terminology.hl7.org/CodeSystemv3-DataOperation").setCode("CREATE").setDisplay("create");
            provenance.setActivity(new CodeableConcept().addCoding(activityCoding));

            ProvenanceAgentComponent agent = new ProvenanceAgentComponent();
            agent.setType(new CodeableConcept().addCoding(new Coding().setSystem("http://terminology.hl7.org/CodeSystemprovenance-participant-type").setCode("author").setDisplay("Author")));
            Set<Role> roles = creator.getAllRoles();
            // while (roles.iterator().hasNext()) {
                Role role = roles.iterator().next();
                agent.addRole(new CodeableConcept().addCoding(new Coding().setSystem("http://terminology.hl7.org/CodeSystemv3-ParticipationType").setCode(role.getRole()).setDisplay(role.getName())));
            // }
            org.hl7.fhir.r4.model.Reference who = new org.hl7.fhir.r4.model.Reference().setReference("Practitioner/" + creator.getUuid()).setType("Practitioner").setDisplay(creator.getFamilyName() + " " + creator.getGivenName());
            agent.setWho(who);

            provenance.addAgent(agent);

            shrEncounter.getEncounter().addContained(provenance);
        }
        // log.info("Meta is going to be added");
        //Adding Meta to the object
        shrEncounter.getEncounter().setMeta(new Meta().addTag(new Coding().setSystem("http://fhir.openmrs.org/ext/encounter-tag").setCode("encounter").setDisplay("Encounter")));
        // log.info("Status is going to be added too.");
        //Add Status field to the Object
        shrEncounter.getEncounter().setStatus(EncounterStatus.UNKNOWN);
        log.info("Translation Update process ended");
    }
}
