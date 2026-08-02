package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.page.controller.patient.AnesthesiaRecordPageController;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
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

public class AnesthesiaRecordFragmentController {

    public void controller(FragmentConfiguration config,
                           FragmentModel model,
                           UiUtils ui,
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

        List<Encounter> encounters = Collections.emptyList();
        EncounterType anesthesiaType = encounterService.getEncounterTypeByUuid(
                AnesthesiaRecordPageController.ANESTHESIA_ENCOUNTER_TYPE_UUID);
        if (anesthesiaType != null) {
            encounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                    .setPatient(patientWrapper.getPatient())
                    .setEncounterTypes(Collections.singletonList(anesthesiaType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria());
            encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime).reversed());
        }

        String latestCaseId = null;
        for (Encounter encounter : encounters) {
            latestCaseId = AnesthesiaRecordPageController.getAnesthesiaCaseId(encounter, conceptService);
            if (latestCaseId != null) {
                break;
            }
        }
        List<Encounter> selectedEncounters = new ArrayList<Encounter>();
        for (Encounter encounter : encounters) {
            String encounterCaseId = AnesthesiaRecordPageController.getAnesthesiaCaseId(encounter, conceptService);
            if ((latestCaseId == null && encounterCaseId == null)
                    || (latestCaseId != null && latestCaseId.equals(encounterCaseId))) {
                selectedEncounters.add(encounter);
            }
        }
        encounters = selectedEncounters;

        Map<String, String> latestValues = new LinkedHashMap<String, String>();
        Encounter latest = encounters.isEmpty() ? null : encounters.get(0);
        addLatestValue(latestValues, encounters, conceptService, ui, "Anesthesia",
                AnesthesiaRecordPageController.CONCEPT_ANESTHESIA_TYPE);
        addLatestValue(latestValues, encounters, conceptService, ui, "Urgency",
                AnesthesiaRecordPageController.CONCEPT_URGENCY);
        Encounter latestVitals = findLatestVitalsEncounter(encounters, conceptService);
        if (latestVitals != null) {
            addNumeric(latestValues, latestVitals, conceptService, "Pulse",
                    AnesthesiaRecordPageController.CONCEPT_PULSE, " bpm");
            addBloodPressure(latestValues, latestVitals, conceptService);
            addNumeric(latestValues, latestVitals, conceptService, "Resp",
                    AnesthesiaRecordPageController.CONCEPT_RESPIRATORY_RATE, "/min");
            addNumeric(latestValues, latestVitals, conceptService, "SpO2",
                    AnesthesiaRecordPageController.CONCEPT_OXYGEN_SATURATION, "%");
        }
        addLatestNumeric(latestValues, encounters, conceptService, "Aldrete",
                AnesthesiaRecordPageController.CONCEPT_ALDRETE_SCORE, "");

        model.addAttribute("patient", patientWrapper);
        model.addAttribute("encounterCount", encounters.size());
        model.addAttribute("latestEncounter", latest);
        model.addAttribute("latestValues", latestValues);
    }

    private void addLatestValue(Map<String, String> values,
                                List<Encounter> encounters,
                                ConceptService conceptService,
                                UiUtils ui,
                                String label,
                                String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return;
        }
        for (Encounter encounter : encounters) {
            for (Obs obs : encounter.getObsAtTopLevel(false)) {
                if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                    String optionLabel = AnesthesiaRecordPageController.getAnesthesiaOptionLabel(obs.getValueCoded());
                    values.put(label, optionLabel != null
                            ? optionLabel
                            : ui.format(obs.getValueAsString(Context.getLocale())));
                    return;
                }
            }
        }
    }

    private void addLatestNumeric(Map<String, String> values,
                                  List<Encounter> encounters,
                                  ConceptService conceptService,
                                  String label,
                                  String conceptUuid,
                                  String unit) {
        for (Encounter encounter : encounters) {
            Double value = getNumericObs(encounter, conceptService, conceptUuid);
            if (value != null) {
                values.put(label, value.intValue() + unit);
                return;
            }
        }
    }

    private Encounter findLatestVitalsEncounter(List<Encounter> encounters, ConceptService conceptService) {
        for (Encounter encounter : encounters) {
            if (getNumericObs(encounter, conceptService, AnesthesiaRecordPageController.CONCEPT_PULSE) != null
                    || getNumericObs(encounter, conceptService, AnesthesiaRecordPageController.CONCEPT_SBP) != null
                    || getNumericObs(encounter, conceptService, AnesthesiaRecordPageController.CONCEPT_DBP) != null
                    || getNumericObs(encounter, conceptService,
                    AnesthesiaRecordPageController.CONCEPT_RESPIRATORY_RATE) != null
                    || getNumericObs(encounter, conceptService,
                    AnesthesiaRecordPageController.CONCEPT_OXYGEN_SATURATION) != null) {
                return encounter;
            }
        }
        return null;
    }

    private void addBloodPressure(Map<String, String> values, Encounter encounter, ConceptService conceptService) {
        Double sbp = getNumericObs(encounter, conceptService, AnesthesiaRecordPageController.CONCEPT_SBP);
        Double dbp = getNumericObs(encounter, conceptService, AnesthesiaRecordPageController.CONCEPT_DBP);
        if (sbp != null || dbp != null) {
            values.put("BP", (sbp == null ? "-" : sbp.intValue()) + " / " + (dbp == null ? "-" : dbp.intValue()));
        }
    }

    private void addNumeric(Map<String, String> values, Encounter encounter, ConceptService conceptService,
                            String label, String conceptUuid, String unit) {
        Double value = getNumericObs(encounter, conceptService, conceptUuid);
        if (value != null) {
            values.put(label, value.intValue() + unit);
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

    private Concept getConcept(ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByReference(conceptUuid);
        if (concept == null) {
            concept = conceptService.getConceptByUuid(conceptUuid);
        }
        return concept;
    }
}
