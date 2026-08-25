package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnesthesiaRecordPageController {

    public static final String ANESTHESIA_ENCOUNTER_TYPE_UUID = "03c36ceb-d4c5-441f-aca5-d88fdd9b6964";

    public static final String CONCEPT_PULSE = "3ce93824-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_SBP = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_DBP = "3ce93694-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_RESPIRATORY_RATE = "3ceb11f8-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_TEMPERATURE = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_OXYGEN_SATURATION = "3ce9401c-26fe-102b-80cb-0017a47871b2";

    public static final String CONCEPT_ANESTHESIA_TYPE = "a6e87d58-bf18-4d6b-a7f6-8a17db7452a7";
    public static final String CONCEPT_URGENCY = "dedb6827-bd3f-4de1-9bc7-1cc63c8974e6";
    public static final String CONCEPT_SERVICE = "21e73863-867c-4faa-8c4b-dc5b1c0319ac";
    public static final String CONCEPT_PROCEDURE = "c3ecac79-8555-4588-a228-61c6d7f59892";
    public static final String CONCEPT_SURGEON = "f1fe45a0-70aa-4819-9543-e98002c80401";
    public static final String CONCEPT_ANESTHETIST = "54c9c4db-428b-4579-9023-84d8db65223a";
    public static final String CONCEPT_PREMEDICATION = "6ac01def-4790-4e05-98a3-4cc3da667005";
    public static final String CONCEPT_VENTILATION_MODE = "7a81095b-c2d2-48bb-9b2f-81fe4050d791";
    public static final String CONCEPT_MEDICATIONS = "e2a92af0-e3a8-48c7-b73f-1bf5fb1b63ef";
    public static final String CONCEPT_INHALED_AGENT = "2b42694e-707c-4888-80a8-1f6325072678";
    public static final String CONCEPT_INHALED_AGENT_PERCENT = "b6e47933-62b2-4929-b6f6-eaee3d5e9c8f";
    public static final String CONCEPT_FLUIDS = "074c4f8b-2f73-41c3-9b9b-9ee8826d4016";
    public static final String CONCEPT_TRANSFUSION = "4e56e745-a912-4734-bca7-62d78070b64d";
    public static final String CONCEPT_AIRWAY_TECHNIQUE = "0375a9d5-32d2-46aa-afd7-518bb7f66f28";
    public static final String CONCEPT_POSITION = "4080da65-d8dd-48a6-a90a-094866308d12";
    public static final String CONCEPT_POSTOP_DIAGNOSIS = "1c811683-d7f1-45f8-ac51-e7bf01344390";
    public static final String CONCEPT_REMARKS = "8d3ea639-e9a4-43c7-b9bd-03556160b01d";
    public static final String CONCEPT_ALDRETE_SCORE = "c20d3924-d2ff-420b-b0e2-bb0b6d9d18b1";
    public static final String CONCEPT_OPERATION_ID = "c8641c43-5d65-4b6b-b6d3-2dbebf020ffc";
    public static final String CONCEPT_OPERATION_COMPLETED = "20150cc7-a7ef-460e-bb23-c03ada212c89";
    public static final String CONCEPT_GAS_OBSERVATION_SET = "a04ca71f-cb71-4c9c-b05b-9397235699b7";
    public static final String CONCEPT_MEDICATION_ADMINISTRATION_SET = "975ecb87-fe23-40f1-97ac-ad5e85356e60";
    public static final String LEGACY_CASE_ID = "legacy";

    public void get(PageModel model,
                    UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "patientId") Patient patient,
                    @RequestParam(value = "caseId", required = false) String requestedCaseId,
                    @SpringBean("encounterService") EncounterService encounterService,
                    @SpringBean("conceptService") ConceptService conceptService) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);

        EncounterType anesthesiaType = encounterService.getEncounterTypeByUuid(ANESTHESIA_ENCOUNTER_TYPE_UUID);
        List<Encounter> allEncounters = new ArrayList<Encounter>();
        if (anesthesiaType != null) {
            allEncounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                    .setPatient(patient)
                    .setEncounterTypes(Collections.singletonList(anesthesiaType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria());
            allEncounters.sort(Comparator.comparing(Encounter::getEncounterDatetime));
        }

        Map<String, List<Encounter>> encountersByCase = new LinkedHashMap<String, List<Encounter>>();
        List<Encounter> legacyEncounters = new ArrayList<Encounter>();
        for (Encounter encounter : allEncounters) {
            String caseId = getAnesthesiaCaseId(encounter, conceptService);
            if (caseId == null) {
                legacyEncounters.add(encounter);
            }
            else {
                encountersByCase.computeIfAbsent(caseId, key -> new ArrayList<Encounter>()).add(encounter);
            }
        }

        List<Map<String, Object>> operationSummaries = buildOperationSummaries(
                encountersByCase, legacyEncounters, conceptService, ui);
        String selectedCaseId = selectCaseId(requestedCaseId, encountersByCase, legacyEncounters, operationSummaries);
        List<Encounter> encounters = selectedCaseId == null
                ? Collections.<Encounter>emptyList()
                : LEGACY_CASE_ID.equals(selectedCaseId)
                ? legacyEncounters
                : encountersByCase.get(selectedCaseId);
        Map<String, Object> selectedCaseSummary = findOperationSummary(operationSummaries, selectedCaseId);
        boolean selectedCaseWritable = selectedCaseId != null && !LEGACY_CASE_ID.equals(selectedCaseId);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        List<String> vitalLabels = new ArrayList<String>();
        List<Double> pulseData = new ArrayList<Double>();
        List<Double> sbpData = new ArrayList<Double>();
        List<Double> dbpData = new ArrayList<Double>();
        List<Double> respData = new ArrayList<Double>();
        List<Double> spo2Data = new ArrayList<Double>();
        List<String> gasLabels = new ArrayList<String>();
        List<Double> gasPercentData = new ArrayList<Double>();
        List<Map<String, Object>> vitalRows = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> gasRows = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> medicationRows = new ArrayList<Map<String, Object>>();
        Map<String, Object> caseDetails = emptyCaseDetails();
        Map<String, Object> recoveryDetails = emptyRecoveryDetails();
        Encounter caseDetailsEncounter = null;
        Encounter recoveryEncounter = null;
        Concept gasObservationSet = getConcept(conceptService, CONCEPT_GAS_OBSERVATION_SET);
        Concept medicationAdministrationSet = getConcept(conceptService, CONCEPT_MEDICATION_ADMINISTRATION_SET);

        for (Encounter encounter : encounters) {
            boolean caseDetailsUpdated = false;
            caseDetailsUpdated |= updateLatestValue(caseDetails, "service", encounter, conceptService, CONCEPT_SERVICE, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "procedure", encounter, conceptService, CONCEPT_PROCEDURE, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "surgeon", encounter, conceptService, CONCEPT_SURGEON, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "anesthetist", encounter, conceptService, CONCEPT_ANESTHETIST, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "urgency", encounter, conceptService, CONCEPT_URGENCY, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "anesthesiaType", encounter, conceptService, CONCEPT_ANESTHESIA_TYPE, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "premedication", encounter, conceptService, CONCEPT_PREMEDICATION, ui);
            caseDetailsUpdated |= updateLatestValue(caseDetails, "ventilation", encounter, conceptService, CONCEPT_VENTILATION_MODE, ui);
            if (caseDetailsUpdated) {
                caseDetailsEncounter = encounter;
            }

            boolean recoveryUpdated = false;
            recoveryUpdated |= updateLatestValue(recoveryDetails, "airway", encounter, conceptService, CONCEPT_AIRWAY_TECHNIQUE, ui);
            recoveryUpdated |= updateLatestValue(recoveryDetails, "position", encounter, conceptService, CONCEPT_POSITION, ui);
            recoveryUpdated |= updateLatestValue(recoveryDetails, "postopDiagnosis", encounter, conceptService, CONCEPT_POSTOP_DIAGNOSIS, ui);
            recoveryUpdated |= updateLatestValue(recoveryDetails, "remarks", encounter, conceptService, CONCEPT_REMARKS, ui);
            recoveryUpdated |= updateLatestNumeric(recoveryDetails, "aldrete", encounter, conceptService, CONCEPT_ALDRETE_SCORE);
            recoveryUpdated |= isTrueObs(encounter, conceptService, CONCEPT_OPERATION_COMPLETED);
            if (recoveryUpdated) {
                recoveryEncounter = encounter;
            }

            Double pulse = getNumericObs(encounter, conceptService, CONCEPT_PULSE);
            Double sbp = getNumericObs(encounter, conceptService, CONCEPT_SBP);
            Double dbp = getNumericObs(encounter, conceptService, CONCEPT_DBP);
            Double resp = getNumericObs(encounter, conceptService, CONCEPT_RESPIRATORY_RATE);
            Double temperature = getNumericObs(encounter, conceptService, CONCEPT_TEMPERATURE);
            Double spo2 = getNumericObs(encounter, conceptService, CONCEPT_OXYGEN_SATURATION);
            if (pulse != null || sbp != null || dbp != null || resp != null || temperature != null || spo2 != null) {
                vitalLabels.add(dateFormat.format(encounter.getEncounterDatetime()));
                pulseData.add(pulse);
                sbpData.add(sbp);
                dbpData.add(dbp);
                respData.add(resp);
                spo2Data.add(spo2);

                Map<String, Object> vitalRow = new LinkedHashMap<String, Object>();
                vitalRow.put("encounter", encounter);
                vitalRow.put("pulse", pulse);
                vitalRow.put("sbp", sbp);
                vitalRow.put("dbp", dbp);
                vitalRow.put("resp", resp);
                vitalRow.put("temperature", temperature);
                vitalRow.put("spo2", spo2);
                vitalRows.add(0, vitalRow);
            }

            for (Obs gasGroup : getObsGroups(encounter, gasObservationSet)) {
                addGasObservation(encounter,
                        getValue(gasGroup.getGroupMembers(false), conceptService, CONCEPT_INHALED_AGENT, ui),
                        getNumericObs(gasGroup.getGroupMembers(false), conceptService, CONCEPT_INHALED_AGENT_PERCENT),
                        dateFormat, gasLabels, gasPercentData, gasRows);
            }
            addGasObservation(encounter,
                    getValue(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_INHALED_AGENT, ui),
                    getNumericObs(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_INHALED_AGENT_PERCENT),
                    dateFormat, gasLabels, gasPercentData, gasRows);

            for (Obs medicationGroup : getObsGroups(encounter, medicationAdministrationSet)) {
                addMedicationAdministration(encounter,
                        getValue(medicationGroup.getGroupMembers(false), conceptService, CONCEPT_MEDICATIONS, ui),
                        getValue(medicationGroup.getGroupMembers(false), conceptService, CONCEPT_FLUIDS, ui),
                        getValue(medicationGroup.getGroupMembers(false), conceptService, CONCEPT_TRANSFUSION, ui),
                        medicationRows);
            }
            addMedicationAdministration(encounter,
                    getValue(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_MEDICATIONS, ui),
                    getValue(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_FLUIDS, ui),
                    getValue(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_TRANSFUSION, ui),
                    medicationRows);
        }

        model.addAttribute("encounters", encounters);
        model.addAttribute("operationSummaries", operationSummaries);
        model.addAttribute("selectedCaseId", selectedCaseId);
        model.addAttribute("selectedCaseSummary", selectedCaseSummary);
        model.addAttribute("selectedCaseWritable", selectedCaseWritable);
        model.addAttribute("legacyCaseSelected", LEGACY_CASE_ID.equals(selectedCaseId));
        model.addAttribute("newCaseId", UUID.randomUUID().toString());
        model.addAttribute("vitalRows", vitalRows);
        model.addAttribute("gasRows", gasRows);
        model.addAttribute("medicationRows", medicationRows);
        model.addAttribute("caseDetails", caseDetails);
        model.addAttribute("caseDetailsRecorded", caseDetailsEncounter != null);
        model.addAttribute("caseDetailsEncounter", caseDetailsEncounter);
        model.addAttribute("recoveryDetails", recoveryDetails);
        model.addAttribute("recoveryRecorded", recoveryEncounter != null);
        model.addAttribute("recoveryEncounter", recoveryEncounter);
        model.addAttribute("vitalLabels", toJsonArray(vitalLabels, true));
        model.addAttribute("pulseData", toJsonArray(pulseData, false));
        model.addAttribute("sbpData", toJsonArray(sbpData, false));
        model.addAttribute("dbpData", toJsonArray(dbpData, false));
        model.addAttribute("respData", toJsonArray(respData, false));
        model.addAttribute("spo2Data", toJsonArray(spo2Data, false));
        model.addAttribute("gasLabels", toJsonArray(gasLabels, true));
        model.addAttribute("gasPercentData", toJsonArray(gasPercentData, false));
    }

    private List<Map<String, Object>> buildOperationSummaries(Map<String, List<Encounter>> encountersByCase,
                                                               List<Encounter> legacyEncounters,
                                                               ConceptService conceptService,
                                                               UiUtils ui) {
        List<Map<String, Object>> summaries = new ArrayList<Map<String, Object>>();
        SimpleDateFormat labelDateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
        for (Map.Entry<String, List<Encounter>> entry : encountersByCase.entrySet()) {
            summaries.add(buildOperationSummary(entry.getKey(), entry.getValue(), false,
                    labelDateFormat, conceptService, ui));
        }
        summaries.sort((left, right) -> ((Date) right.get("lastUpdated")).compareTo((Date) left.get("lastUpdated")));
        if (!legacyEncounters.isEmpty()) {
            summaries.add(buildOperationSummary(LEGACY_CASE_ID, legacyEncounters, true,
                    labelDateFormat, conceptService, ui));
        }
        return summaries;
    }

    private Map<String, Object> buildOperationSummary(String caseId,
                                                       List<Encounter> encounters,
                                                       boolean legacy,
                                                       SimpleDateFormat labelDateFormat,
                                                       ConceptService conceptService,
                                                       UiUtils ui) {
        Date started = encounters.get(0).getEncounterDatetime();
        Date lastUpdated = encounters.get(encounters.size() - 1).getEncounterDatetime();
        String procedure = null;
        boolean complete = false;
        for (Encounter encounter : encounters) {
            String currentProcedure = getValue(encounter.getObsAtTopLevel(false), conceptService, CONCEPT_PROCEDURE, ui);
            if (hasText(currentProcedure)) {
                procedure = currentProcedure;
            }
            complete |= isTrueObs(encounter, conceptService, CONCEPT_OPERATION_COMPLETED);
        }

        Map<String, Object> summary = new LinkedHashMap<String, Object>();
        summary.put("id", caseId);
        summary.put("started", started);
        summary.put("lastUpdated", lastUpdated);
        summary.put("procedure", procedure);
        summary.put("complete", complete);
        summary.put("legacy", legacy);
        summary.put("encounterCount", encounters.size());
        summary.put("label", legacy
                ? "Legacy anesthesia record"
                : labelDateFormat.format(started) + " - " + (hasText(procedure) ? procedure : "Operation"));
        return summary;
    }

    private String selectCaseId(String requestedCaseId,
                                Map<String, List<Encounter>> encountersByCase,
                                List<Encounter> legacyEncounters,
                                List<Map<String, Object>> operationSummaries) {
        String normalizedRequestedCaseId = normalizeCaseId(requestedCaseId);
        if (normalizedRequestedCaseId != null && encountersByCase.containsKey(normalizedRequestedCaseId)) {
            return normalizedRequestedCaseId;
        }
        if (LEGACY_CASE_ID.equals(requestedCaseId) && !legacyEncounters.isEmpty()) {
            return LEGACY_CASE_ID;
        }
        for (Map<String, Object> summary : operationSummaries) {
            if (!Boolean.TRUE.equals(summary.get("legacy"))) {
                return (String) summary.get("id");
            }
        }
        return legacyEncounters.isEmpty() ? null : LEGACY_CASE_ID;
    }

    private Map<String, Object> findOperationSummary(List<Map<String, Object>> summaries, String caseId) {
        for (Map<String, Object> summary : summaries) {
            if (summary.get("id").equals(caseId)) {
                return summary;
            }
        }
        return null;
    }

    public static String getAnesthesiaCaseId(Encounter encounter, ConceptService conceptService) {
        Concept concept = getConcept(conceptService, CONCEPT_OPERATION_ID);
        if (concept == null) {
            return null;
        }
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return normalizeCaseId(obs.getValueText());
            }
        }
        return null;
    }

    private static String normalizeCaseId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> emptyCaseDetails() {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("service", null);
        details.put("procedure", null);
        details.put("surgeon", null);
        details.put("anesthetist", null);
        details.put("urgency", null);
        details.put("anesthesiaType", null);
        details.put("premedication", null);
        details.put("ventilation", null);
        return details;
    }

    private Map<String, Object> emptyRecoveryDetails() {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("airway", null);
        details.put("position", null);
        details.put("postopDiagnosis", null);
        details.put("remarks", null);
        details.put("aldrete", null);
        return details;
    }

    private boolean updateLatestValue(Map<String, Object> details,
                                      String key,
                                      Encounter encounter,
                                      ConceptService conceptService,
                                      String conceptUuid,
                                      UiUtils ui) {
        String value = getValue(encounter.getObsAtTopLevel(false), conceptService, conceptUuid, ui);
        if (!hasText(value)) {
            return false;
        }
        details.put(key, value);
        return true;
    }

    private boolean updateLatestNumeric(Map<String, Object> details,
                                        String key,
                                        Encounter encounter,
                                        ConceptService conceptService,
                                        String conceptUuid) {
        Double value = getNumericObs(encounter.getObsAtTopLevel(false), conceptService, conceptUuid);
        if (value == null) {
            return false;
        }
        details.put(key, value);
        return true;
    }

    private Double getNumericObs(Encounter encounter, ConceptService conceptService, String conceptUuid) {
        return getNumericObs(encounter.getAllObs(), conceptService, conceptUuid);
    }

    private Double getNumericObs(Collection<Obs> observations, ConceptService conceptService, String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return null;
        }
        for (Obs obs : observations) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return obs.getValueNumeric();
            }
        }
        return null;
    }

    private boolean isTrueObs(Encounter encounter, ConceptService conceptService, String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return false;
        }
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return Boolean.TRUE.equals(obs.getValueBoolean());
            }
        }
        return false;
    }

    private String getValue(Collection<Obs> observations, ConceptService conceptService, String conceptUuid, UiUtils ui) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return null;
        }
        for (Obs obs : observations) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                String optionLabel = getAnesthesiaOptionLabel(obs.getValueCoded());
                if (optionLabel != null) {
                    return optionLabel;
                }
                return ui.format(obs.getValueAsString(Context.getLocale()));
            }
        }
        return null;
    }

    public static String getAnesthesiaOptionLabel(Concept answer) {
        if (answer == null || answer.getUuid() == null) {
            return null;
        }
        switch (answer.getUuid()) {
            case "5bf03345-65a8-4350-8c9d-a26cb93e38b5":
                return "General";
            case "e7deab8a-0223-4724-a5ab-322a698f7884":
                return "Regional";
            case "c7d7e318-67ee-44d7-9b89-1a487d24c609":
                return "Emergency";
            case "22e5350e-3b6b-4797-b3d4-184d354a5192":
                return "Elective";
            case "82ed58d5-5797-4baa-bda3-f5e0a3ee7fba":
                return "Pre-operative";
            case "9e620141-eb87-4185-ad54-a4310b33944b":
                return "On table";
            case "0365d7cf-d727-4c66-8593-f7555b009fe4":
                return "Spontaneous";
            case "78397c49-8b70-4598-8ab2-bde2b0ff3dfa":
                return "Assisted";
            case "c88563b9-d30a-43ea-a600-19b139da38a1":
                return "Controlled";
            case "830186cd-e72a-4537-80b2-c2e9be1e3b9e":
                return "Face mask";
            case "0f956919-d30f-4e42-88a0-7274cead8a09":
                return "Oral endotracheal tube";
            case "f03d14e8-211d-4e91-ade5-9c24d02a9747":
                return "Nasal endotracheal tube";
            case "2978d97a-8d63-4e6f-9ab3-9a775e497332":
                return "Supine";
            case "3b4807f8-ccd5-49ce-be05-d1d16621db9b":
                return "Prone";
            case "127c90b1-586f-4e1e-af4d-5c87aa6341dd":
                return "Lateral";
            default:
                return null;
        }
    }

    private static Concept getConcept(ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByReference(conceptUuid);
        if (concept == null) {
            concept = conceptService.getConceptByUuid(conceptUuid);
        }
        return concept;
    }

    private List<Obs> getObsGroups(Encounter encounter, Concept groupingConcept) {
        List<Obs> groups = new ArrayList<Obs>();
        if (groupingConcept == null) {
            return groups;
        }
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (!obs.isVoided() && groupingConcept.equals(obs.getConcept())) {
                groups.add(obs);
            }
        }
        groups.sort(Comparator.comparing(Obs::getId));
        return groups;
    }

    private void addGasObservation(Encounter encounter,
                                   String inhaledAgent,
                                   Double inhaledAgentPercent,
                                   SimpleDateFormat dateFormat,
                                   List<String> gasLabels,
                                   List<Double> gasPercentData,
                                   List<Map<String, Object>> gasRows) {
        if (!hasText(inhaledAgent) && inhaledAgentPercent == null) {
            return;
        }
        gasLabels.add(dateFormat.format(encounter.getEncounterDatetime()));
        gasPercentData.add(inhaledAgentPercent);

        Map<String, Object> gasRow = new LinkedHashMap<String, Object>();
        gasRow.put("encounter", encounter);
        gasRow.put("inhaledAgent", inhaledAgent);
        gasRow.put("inhaledAgentPercent", inhaledAgentPercent);
        gasRows.add(0, gasRow);
    }

    private void addMedicationAdministration(Encounter encounter,
                                             String medications,
                                             String fluids,
                                             String transfusion,
                                             List<Map<String, Object>> medicationRows) {
        if (!hasText(medications) && !hasText(fluids) && !hasText(transfusion)) {
            return;
        }
        Map<String, Object> medicationRow = new LinkedHashMap<String, Object>();
        medicationRow.put("encounter", encounter);
        medicationRow.put("medications", medications);
        medicationRow.put("fluids", fluids);
        medicationRow.put("transfusion", transfusion);
        medicationRows.add(0, medicationRow);
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String toJsonArray(List<?> values, boolean quote) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            Object value = values.get(i);
            if (value == null) {
                json.append("null");
            }
            else if (quote) {
                json.append("\"").append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            else {
                json.append(value);
            }
        }
        json.append("]");
        return json.toString();
    }
}
