package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.VisitService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shows the most recent medical plan recorded during the patient's active visit.
 * The newest obs across all plan concepts wins; nothing is shown once the visit ends.
 */
public class MedicalPlanFragmentController {

    public static final String[] MEDICAL_PLAN_CONCEPT_UUIDS = {
            "9c0a921e-ee2d-45ee-a448-a438d147392e",
            "4044b850-32da-4689-bb59-107fdacfb470"
    };

    public void controller(FragmentConfiguration config,
                           FragmentModel model,
                           @SpringBean("visitService") VisitService visitService,
                           @SpringBean("encounterService") EncounterService encounterService,
                           @SpringBean("obsService") ObsService obsService,
                           @SpringBean("conceptService") ConceptService conceptService,
                           @InjectBeans PatientDomainWrapper patientWrapper) {

        config.require("patient");
        Object patient = config.get("patient");
        if (patient instanceof Patient) {
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        }
        else if (patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }

        Visit activeVisit = getMostRecentActiveVisit(patientWrapper.getPatient(), visitService);

        model.addAttribute("patient", patientWrapper);
        model.addAttribute("activeVisit", activeVisit);
        model.addAttribute("latestPlan", getLatestPlan(patientWrapper.getPatient(), activeVisit,
                encounterService, obsService, conceptService));
    }

    private Visit getMostRecentActiveVisit(Patient patient, VisitService visitService) {
        List<Visit> activeVisits = visitService.getActiveVisitsByPatient(patient);
        if (activeVisits.isEmpty()) {
            return null;
        }
        return Collections.max(activeVisits, Comparator.comparing(Visit::getStartDatetime));
    }

    private Obs getLatestPlan(Patient patient, Visit visit, EncounterService encounterService,
                              ObsService obsService, ConceptService conceptService) {
        if (visit == null) {
            return null;
        }
        List<Concept> concepts = new ArrayList<Concept>();
        for (String uuid : MEDICAL_PLAN_CONCEPT_UUIDS) {
            Concept concept = conceptService.getConceptByUuid(uuid);
            if (concept != null) {
                concepts.add(concept);
            }
        }
        if (concepts.isEmpty()) {
            return null;
        }
        List<Encounter> encounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                .setPatient(patient)
                .setVisits(Collections.singletonList(visit))
                .setIncludeVoided(false)
                .createEncounterSearchCriteria());
        // An empty encounter list is ignored by getObservations, which would return plans from any visit
        if (encounters.isEmpty()) {
            return null;
        }
        List<Obs> obs = obsService.getObservations(Collections.<Person>singletonList(patient), encounters, concepts,
                null, null, null, Collections.singletonList("obsDatetime"), 1, null, null, null, false);
        return obs.isEmpty() ? null : obs.get(0);
    }
}
