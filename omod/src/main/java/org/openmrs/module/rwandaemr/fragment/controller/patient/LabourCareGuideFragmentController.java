package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.page.controller.patient.LabourCareGuidePageController;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabourCareGuideFragmentController {

    public void controller(FragmentConfiguration config,
                           FragmentModel model,
                           @SpringBean("encounterService") EncounterService encounterService,
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

        List<Encounter> encounters = loadEncounters(patientWrapper.getPatient(), encounterService);
        String latestEpisodeId = null;
        for (Encounter encounter : encounters) {
            latestEpisodeId = LabourCareGuidePageController.getLabourEpisodeId(encounter, conceptService);
            if (latestEpisodeId != null) {
                break;
            }
        }

        List<Encounter> selectedEncounters = new ArrayList<Encounter>();
        for (Encounter encounter : encounters) {
            String episodeId = LabourCareGuidePageController.getLabourEpisodeId(encounter, conceptService);
            if (latestEpisodeId != null && latestEpisodeId.equals(episodeId)) {
                selectedEncounters.add(encounter);
            }
        }

        Map<String, String> latestValues = new LinkedHashMap<String, String>();
        Encounter latest = selectedEncounters.isEmpty() ? null : selectedEncounters.get(0);
        Encounter latestHourly = latestEncounterWithConcept(selectedEncounters, conceptService,
                LabourCareGuidePageController.CONCEPT_FHR);
        if (latestHourly != null) {
            addNumeric(latestValues, latestHourly, conceptService, "FHR",
                    LabourCareGuidePageController.CONCEPT_FHR, " bpm");
            addNumeric(latestValues, latestHourly, conceptService, "Pulse",
                    LabourCareGuidePageController.CONCEPT_PULSE, " bpm");
            addBloodPressure(latestValues, latestHourly, conceptService);
            addNumeric(latestValues, latestHourly, conceptService, "Cervix",
                    LabourCareGuidePageController.CONCEPT_CERVIX, " cm");
            addNumeric(latestValues, latestHourly, conceptService, "Contractions",
                    LabourCareGuidePageController.CONCEPT_CONTRACTIONS, "/10min");
        }

        model.addAttribute("patient", patientWrapper);
        model.addAttribute("episodeId", latestEpisodeId);
        model.addAttribute("encounterCount", selectedEncounters.size());
        model.addAttribute("latestEncounter", latest);
        model.addAttribute("latestValues", latestValues);
    }

    private List<Encounter> loadEncounters(Patient patient, EncounterService encounterService) {
        List<EncounterType> types = new ArrayList<EncounterType>();
        EncounterType findingsType = encounterService.getEncounterTypeByUuid(
                LabourCareGuidePageController.FINDINGS_ENCOUNTER_TYPE_UUID);
        EncounterType partogramType = encounterService.getEncounterTypeByUuid(
                LabourCareGuidePageController.PARTOGRAM_ENCOUNTER_TYPE_UUID);
        if (findingsType != null) {
            types.add(findingsType);
        }
        if (partogramType != null) {
            types.add(partogramType);
        }
        if (types.isEmpty()) {
            return Collections.emptyList();
        }
        List<Encounter> encounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                .setPatient(patient)
                .setEncounterTypes(types)
                .setIncludeVoided(false)
                .createEncounterSearchCriteria());
        encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime).reversed());
        return encounters;
    }

    private Encounter latestEncounterWithConcept(List<Encounter> encounters, ConceptService conceptService,
                                                 String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return null;
        }
        for (Encounter encounter : encounters) {
            for (Obs obs : encounter.getAllObs()) {
                if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                    return encounter;
                }
            }
        }
        return null;
    }

    private void addBloodPressure(Map<String, String> values, Encounter encounter, ConceptService conceptService) {
        Double sbp = getNumericObs(encounter, conceptService, LabourCareGuidePageController.CONCEPT_SBP);
        Double dbp = getNumericObs(encounter, conceptService, LabourCareGuidePageController.CONCEPT_DBP);
        if (sbp != null || dbp != null) {
            values.put("BP", format(sbp) + " / " + format(dbp));
        }
    }

    private void addNumeric(Map<String, String> values, Encounter encounter, ConceptService conceptService,
                            String label, String conceptUuid, String unit) {
        Double value = getNumericObs(encounter, conceptService, conceptUuid);
        if (value != null) {
            values.put(label, format(value) + unit);
        }
    }

    private Double getNumericObs(Encounter encounter, ConceptService conceptService, String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return null;
        }
        for (Obs obs : encounter.getAllObs()) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return obs.getValueNumeric();
            }
        }
        return null;
    }

    private String format(Double value) {
        if (value == null) {
            return "-";
        }
        return value % 1 == 0 ? String.valueOf(value.intValue()) : String.valueOf(value);
    }

    private Concept getConcept(ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByReference(conceptUuid);
        if (concept == null) {
            concept = conceptService.getConceptByUuid(conceptUuid);
        }
        return concept;
    }
}
