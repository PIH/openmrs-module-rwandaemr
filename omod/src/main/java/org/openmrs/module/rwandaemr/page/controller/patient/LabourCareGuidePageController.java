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
import org.openmrs.module.rwandaemr.htmlformentry.LabourEpisodeIdObsTagHandler;
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

public class LabourCareGuidePageController {

    public static final String FINDINGS_ENCOUNTER_TYPE_UUID = "76162246-15d8-43b0-9666-5884ad1e2be4";
    public static final String PARTOGRAM_ENCOUNTER_TYPE_UUID = "d0d3777d-a40e-4e5d-a116-0163aab5fd6c";

    public static final String FORM_HOURLY = "parto-hourly-form.xml";
    public static final String FORM_LABOUR_SUMMARY = "parto-labour-summary.xml";
    public static final String FORM_NEWBORN = "parto-newborn.xml";
    public static final String FORM_PPH = "parto-pph-diagnosis.xml";
    public static final String FORM_POSTPARTUM_WOMAN = "parto-postpartum-woman.xml";
    public static final String FORM_POSTPARTUM_NEWBORN = "parto-postpartum-newborn.xml";
    public static final String FORM_DISCHARGE = "parto-discharge.xml";

    public static final String CONCEPT_EPISODE_ID = LabourEpisodeIdObsTagHandler.EPISODE_ID_CONCEPT;
    public static final String CONCEPT_COMPANION = "9fd6786b-6b83-4fa7-b6b4-ba5846a84937";
    public static final String CONCEPT_PAIN_RELIEF = "9bd95df5-684b-4a9c-be19-1f30f104908a";
    public static final String CONCEPT_ORAL_FLUID = "59dfcb57-b663-4ecb-8fa6-ca0b7a23674b";
    public static final String CONCEPT_POSTURE = "cb758416-d950-4e21-a4b6-4cd37843b74c";
    public static final String CONCEPT_FHR = "36a6d4d5-b797-4316-adde-3ca3e7109b52";
    public static final String CONCEPT_FHR_DECELERATION = "30331c69-3ecf-4ae5-bd54-71ea90f033bf";
    public static final String CONCEPT_AMNIOTIC_FLUID = "2576c214-d10e-4330-ab3c-3df4d6bf466d";
    public static final String CONCEPT_FETAL_POSITION = "370efbe9-f916-425c-9d3e-8dd1c747be3e";
    public static final String CONCEPT_CAPUT = "eaa3f2f6-22bb-4a58-82e4-5e3950f74cc8";
    public static final String CONCEPT_MOULDING = "74a28037-2fc1-49a5-8572-8cf2710b8c12";
    public static final String CONCEPT_PULSE = "3ce93824-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_RESPIRATIONS = "3ceb11f8-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_SBP = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_DBP = "3ce93694-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_TEMPERATURE = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    public static final String CONCEPT_URINE = "8eeea59e-d31c-4294-b02d-078249818e1b";
    public static final String CONCEPT_URINE_VOLUME = "bd418b33-2082-43de-bbe4-3ed8fd813088";
    public static final String CONCEPT_CONTRACTIONS = "5965b71c-4214-4780-a74e-2de09b06a552";
    public static final String CONCEPT_CONTRACTION_DURATION = "33dddcf3-791b-4c20-a971-4d5c1ed4110c";
    public static final String CONCEPT_CERVIX = "1d28c215-f090-48d4-8813-b214d0e4054b";
    public static final String CONCEPT_DESCENT = "ef129b33-e1ed-43f2-9e47-b169a95f7c59";
    public static final String CONCEPT_OXYTOCIN = "76f30070-c91f-4211-880c-024565b69d42";
    public static final String CONCEPT_MEDICINE = "89bf85f5-72f1-4037-ac86-6e3ac730ef45";
    public static final String CONCEPT_IV_FLUIDS = "def3f08b-e55a-464d-8d1c-b4a389a37649";
    public static final String CONCEPT_ASSESSMENT = "34ab9dab-f58c-409d-8649-6cc5c254470d";
    public static final String CONCEPT_PLAN = "ca5f9645-f958-47ca-b488-079d4089cc3e";
    public static final String CONCEPT_INITIALS = "d5180c5c-96a4-4640-9509-5e3926c5efe4";

    public static final String CONCEPT_MODE_OF_CHILDBIRTH = "3104238e-94f7-4d63-a59f-d2886bd7b15a";
    public static final String CONCEPT_BLOOD_LOSS = "93e9d507-b603-4f83-9d6b-d514fd87ab1a";
    public static final String CONCEPT_NEWBORN_OUTCOME = "cf3137cf-8a15-429b-800b-2e35d43011d7";
    public static final String CONCEPT_PPH_DIAGNOSIS_DATE = "7fbd32ed-3d7c-4e81-b89b-87d7043a1b5a";
    public static final String CONCEPT_POSTPARTUM_WOMAN_DATE = "f6acad2a-8553-436a-af8d-15fb934be24c";
    public static final String CONCEPT_POSTPARTUM_NEWBORN_DATE = "0bc46a19-fe56-4c3a-9d2a-13f1786163a9";
    public static final String CONCEPT_DISCHARGE_DANGER_SIGNS = "14e038f6-779f-4826-af20-0e12a973a4fe";

    public void get(PageModel model,
                    UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "patientId") Patient patient,
                    @RequestParam(value = "episodeId", required = false) String requestedEpisodeId,
                    @SpringBean("encounterService") EncounterService encounterService,
                    @SpringBean("conceptService") ConceptService conceptService) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);

        List<Encounter> allEncounters = loadLabourEncounters(patient, encounterService);
        Map<String, List<Encounter>> encountersByEpisode = new LinkedHashMap<String, List<Encounter>>();
        for (Encounter encounter : allEncounters) {
            String episodeId = getLabourEpisodeId(encounter, conceptService);
            if (episodeId != null) {
                encountersByEpisode.computeIfAbsent(episodeId, key -> new ArrayList<Encounter>()).add(encounter);
            }
        }

        for (List<Encounter> encounters : encountersByEpisode.values()) {
            encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime));
        }

        List<Map<String, Object>> episodeSummaries = buildEpisodeSummaries(encountersByEpisode, conceptService, ui);
        String selectedEpisodeId = selectEpisodeId(requestedEpisodeId, encountersByEpisode, episodeSummaries);
        List<Encounter> selectedEncounters = selectedEpisodeId == null
                ? Collections.<Encounter>emptyList()
                : encountersByEpisode.get(selectedEpisodeId);

        List<Encounter> hourlyEncounters = findEncountersWithConcept(selectedEncounters, conceptService, CONCEPT_FHR);
        Map<String, Object> sectionStatus = buildSectionStatus(selectedEncounters, conceptService);
        List<Map<String, Object>> hourlyRows = buildHourlyRows(hourlyEncounters, conceptService, ui);
        List<Map<String, Object>> activeAlerts = hourlyRows.isEmpty()
                ? Collections.<Map<String, Object>>emptyList()
                : castAlerts(hourlyRows.get(hourlyRows.size() - 1).get("alerts"));

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        model.addAttribute("episodeSummaries", episodeSummaries);
        model.addAttribute("selectedEpisodeId", selectedEpisodeId);
        model.addAttribute("selectedEpisodeSummary", findEpisodeSummary(episodeSummaries, selectedEpisodeId));
        model.addAttribute("newEpisodeId", UUID.randomUUID().toString());
        model.addAttribute("sectionStatus", sectionStatus);
        model.addAttribute("hourlyRows", hourlyRows);
        model.addAttribute("activeAlerts", activeAlerts);
        model.addAttribute("labels", toJsonArray(values(hourlyRows, "label"), true));
        model.addAttribute("fhrData", toJsonArray(values(hourlyRows, "fhr"), false));
        model.addAttribute("pulseData", toJsonArray(values(hourlyRows, "pulse"), false));
        model.addAttribute("respData", toJsonArray(values(hourlyRows, "respirations"), false));
        model.addAttribute("sbpData", toJsonArray(values(hourlyRows, "sbp"), false));
        model.addAttribute("dbpData", toJsonArray(values(hourlyRows, "dbp"), false));
        model.addAttribute("tempData", toJsonArray(values(hourlyRows, "temperature"), false));
        model.addAttribute("cervixData", toJsonArray(values(hourlyRows, "cervix"), false));
        model.addAttribute("descentData", toJsonArray(values(hourlyRows, "descent"), false));
        model.addAttribute("contractionsData", toJsonArray(values(hourlyRows, "contractions"), false));
        model.addAttribute("generatedAt", dateFormat.format(new Date()));
    }

    private List<Encounter> loadLabourEncounters(Patient patient, EncounterService encounterService) {
        List<EncounterType> types = new ArrayList<EncounterType>();
        EncounterType findingsType = encounterService.getEncounterTypeByUuid(FINDINGS_ENCOUNTER_TYPE_UUID);
        EncounterType partogramType = encounterService.getEncounterTypeByUuid(PARTOGRAM_ENCOUNTER_TYPE_UUID);
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
        encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime));
        return encounters;
    }

    private List<Map<String, Object>> buildHourlyRows(List<Encounter> encounters, ConceptService conceptService,
                                                       UiUtils ui) {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (Encounter encounter : encounters) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("encounter", encounter);
            row.put("label", dateFormat.format(encounter.getEncounterDatetime()));
            addValue(row, "companion", encounter, conceptService, ui, CONCEPT_COMPANION);
            addValue(row, "painRelief", encounter, conceptService, ui, CONCEPT_PAIN_RELIEF);
            addValue(row, "oralFluid", encounter, conceptService, ui, CONCEPT_ORAL_FLUID);
            addValue(row, "posture", encounter, conceptService, ui, CONCEPT_POSTURE);
            addNumeric(row, "fhr", encounter, conceptService, CONCEPT_FHR);
            addValue(row, "fhrDeceleration", encounter, conceptService, ui, CONCEPT_FHR_DECELERATION);
            addValue(row, "amnioticFluid", encounter, conceptService, ui, CONCEPT_AMNIOTIC_FLUID);
            addValue(row, "fetalPosition", encounter, conceptService, ui, CONCEPT_FETAL_POSITION);
            addValue(row, "caput", encounter, conceptService, ui, CONCEPT_CAPUT);
            addValue(row, "moulding", encounter, conceptService, ui, CONCEPT_MOULDING);
            addNumeric(row, "pulse", encounter, conceptService, CONCEPT_PULSE);
            addNumeric(row, "respirations", encounter, conceptService, CONCEPT_RESPIRATIONS);
            addNumeric(row, "sbp", encounter, conceptService, CONCEPT_SBP);
            addNumeric(row, "dbp", encounter, conceptService, CONCEPT_DBP);
            addNumeric(row, "temperature", encounter, conceptService, CONCEPT_TEMPERATURE);
            addValue(row, "urine", encounter, conceptService, ui, CONCEPT_URINE);
            addNumeric(row, "urineVolume", encounter, conceptService, CONCEPT_URINE_VOLUME);
            addNumeric(row, "contractions", encounter, conceptService, CONCEPT_CONTRACTIONS);
            addNumeric(row, "contractionDuration", encounter, conceptService, CONCEPT_CONTRACTION_DURATION);
            addNumeric(row, "cervix", encounter, conceptService, CONCEPT_CERVIX);
            addNumeric(row, "descent", encounter, conceptService, CONCEPT_DESCENT);
            addValue(row, "oxytocin", encounter, conceptService, ui, CONCEPT_OXYTOCIN);
            addValue(row, "medicine", encounter, conceptService, ui, CONCEPT_MEDICINE);
            addValue(row, "ivFluids", encounter, conceptService, ui, CONCEPT_IV_FLUIDS);
            addValue(row, "assessment", encounter, conceptService, ui, CONCEPT_ASSESSMENT);
            addValue(row, "plan", encounter, conceptService, ui, CONCEPT_PLAN);
            addValue(row, "initials", encounter, conceptService, ui, CONCEPT_INITIALS);
            row.put("alerts", evaluateAlerts(row));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> evaluateAlerts(Map<String, Object> row) {
        List<Map<String, Object>> alerts = new ArrayList<Map<String, Object>>();
        addRangeAlert(alerts, row, "FHR", "fhr", 110.0, 160.0, "bpm", true, true);
        addRangeAlert(alerts, row, "Pulse", "pulse", 60.0, 120.0, "bpm", true, true);
        addRangeAlert(alerts, row, "Respirations", "respirations", 12.0, 20.0, "/min", true, true);
        addRangeAlert(alerts, row, "Systolic BP", "sbp", 80.0, 140.0, "mmHg", true, true);
        addHighAlert(alerts, row, "Diastolic BP", "dbp", 90.0, "mmHg");
        addRangeAlert(alerts, row, "Temperature", "temperature", 35.0, 37.5, "C", true, true);
        addLowAlert(alerts, row, "Urine volume", "urineVolume", 30.0, "mL/hr");
        addRangeAlert(alerts, row, "Contractions", "contractions", 2.0, 5.0, "/10min", false, false);
        addRangeAlert(alerts, row, "Contraction duration", "contractionDuration", 20.0, 60.0, "sec", true, false);
        addTextAlert(alerts, row, "Caput", "caput", "+++");
        addTextAlert(alerts, row, "Moulding", "moulding", "+++");
        addTextAlert(alerts, row, "Urine", "urine", "P++");
        addTextAlert(alerts, row, "Urine", "urine", "A++");
        return alerts;
    }

    private Map<String, Object> buildSectionStatus(List<Encounter> encounters, ConceptService conceptService) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("hourly", countEncountersWithConcept(encounters, conceptService, CONCEPT_FHR));
        status.put("labourSummary", countEncountersWithConcept(encounters, conceptService, CONCEPT_MODE_OF_CHILDBIRTH));
        status.put("newborn", countEncountersWithConcept(encounters, conceptService, CONCEPT_NEWBORN_OUTCOME));
        status.put("pph", countEncountersWithConcept(encounters, conceptService, CONCEPT_PPH_DIAGNOSIS_DATE));
        status.put("postpartumWoman", countEncountersWithConcept(encounters, conceptService, CONCEPT_POSTPARTUM_WOMAN_DATE));
        status.put("postpartumNewborn", countEncountersWithConcept(encounters, conceptService, CONCEPT_POSTPARTUM_NEWBORN_DATE));
        status.put("discharge", countEncountersWithConcept(encounters, conceptService, CONCEPT_DISCHARGE_DANGER_SIGNS));
        return status;
    }

    private List<Map<String, Object>> buildEpisodeSummaries(Map<String, List<Encounter>> encountersByEpisode,
                                                            ConceptService conceptService,
                                                            UiUtils ui) {
        List<Map<String, Object>> summaries = new ArrayList<Map<String, Object>>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:mm");
        for (Map.Entry<String, List<Encounter>> entry : encountersByEpisode.entrySet()) {
            List<Encounter> encounters = entry.getValue();
            Map<String, Object> summary = new LinkedHashMap<String, Object>();
            Date started = encounters.get(0).getEncounterDatetime();
            Date lastUpdated = encounters.get(encounters.size() - 1).getEncounterDatetime();
            String outcome = latestValue(encounters, conceptService, ui, CONCEPT_NEWBORN_OUTCOME);
            summary.put("id", entry.getKey());
            summary.put("started", started);
            summary.put("lastUpdated", lastUpdated);
            summary.put("encounterCount", encounters.size());
            summary.put("outcome", outcome);
            summary.put("label", dateFormat.format(started) + " - "
                    + (outcome == null ? "Labour Care Guide" : outcome));
            summaries.add(summary);
        }
        summaries.sort((left, right) -> ((Date) right.get("lastUpdated")).compareTo((Date) left.get("lastUpdated")));
        return summaries;
    }

    private String selectEpisodeId(String requestedEpisodeId, Map<String, List<Encounter>> encountersByEpisode,
                                   List<Map<String, Object>> episodeSummaries) {
        String normalizedRequested = normalizeEpisodeId(requestedEpisodeId);
        if (normalizedRequested != null && encountersByEpisode.containsKey(normalizedRequested)) {
            return normalizedRequested;
        }
        return episodeSummaries.isEmpty() ? null : (String) episodeSummaries.get(0).get("id");
    }

    private Map<String, Object> findEpisodeSummary(List<Map<String, Object>> summaries, String episodeId) {
        if (episodeId == null) {
            return null;
        }
        for (Map<String, Object> summary : summaries) {
            if (episodeId.equals(summary.get("id"))) {
                return summary;
            }
        }
        return null;
    }

    public static String getLabourEpisodeId(Encounter encounter, ConceptService conceptService) {
        Concept concept = getConcept(conceptService, CONCEPT_EPISODE_ID);
        if (concept == null) {
            return null;
        }
        for (Obs obs : encounter.getObsAtTopLevel(false)) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return normalizeEpisodeId(obs.getValueText());
            }
        }
        return null;
    }

    static String normalizeEpisodeId(String value) {
        String normalized = LabourEpisodeIdObsTagHandler.normalizeEpisodeId(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private void addValue(Map<String, Object> row, String key, Encounter encounter, ConceptService conceptService,
                          UiUtils ui, String conceptUuid) {
        row.put(key, getValue(encounter.getAllObs(), conceptService, ui, conceptUuid));
    }

    private void addNumeric(Map<String, Object> row, String key, Encounter encounter, ConceptService conceptService,
                            String conceptUuid) {
        row.put(key, getNumericObs(encounter.getAllObs(), conceptService, conceptUuid));
    }

    private String latestValue(List<Encounter> encounters, ConceptService conceptService, UiUtils ui,
                               String conceptUuid) {
        for (int i = encounters.size() - 1; i >= 0; i--) {
            String value = getValue(encounters.get(i).getAllObs(), conceptService, ui, conceptUuid);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String getValue(Collection<Obs> observations, ConceptService conceptService, UiUtils ui,
                            String conceptUuid) {
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return null;
        }
        for (Obs obs : observations) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return ui.format(obs.getValueAsString(Context.getLocale()));
            }
        }
        return null;
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

    private List<Encounter> findEncountersWithConcept(List<Encounter> encounters, ConceptService conceptService,
                                                       String conceptUuid) {
        List<Encounter> matching = new ArrayList<Encounter>();
        Concept concept = getConcept(conceptService, conceptUuid);
        if (concept == null) {
            return matching;
        }
        for (Encounter encounter : encounters) {
            if (hasConcept(encounter, concept)) {
                matching.add(encounter);
            }
        }
        return matching;
    }

    private int countEncountersWithConcept(List<Encounter> encounters, ConceptService conceptService,
                                           String conceptUuid) {
        return findEncountersWithConcept(encounters, conceptService, conceptUuid).size();
    }

    private boolean hasConcept(Encounter encounter, Concept concept) {
        for (Obs obs : encounter.getAllObs()) {
            if (!obs.isVoided() && concept.equals(obs.getConcept())) {
                return true;
            }
        }
        return false;
    }

    private static Concept getConcept(ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByReference(conceptUuid);
        if (concept == null) {
            concept = conceptService.getConceptByUuid(conceptUuid);
        }
        return concept;
    }

    private void addRangeAlert(List<Map<String, Object>> alerts, Map<String, Object> row, String label, String key,
                               Double low, Double high, String unit, boolean lowIsStrict, boolean highIsInclusive) {
        Double value = (Double) row.get(key);
        if (value == null) {
            return;
        }
        boolean tooLow = lowIsStrict ? value < low : value <= low;
        boolean tooHigh = highIsInclusive ? value >= high : value > high;
        if (tooLow || tooHigh) {
            alerts.add(alert(label, value, unit, (lowIsStrict ? "< " : "<= ") + low + " or "
                    + (highIsInclusive ? ">= " : "> ") + high));
        }
    }

    private void addHighAlert(List<Map<String, Object>> alerts, Map<String, Object> row, String label, String key,
                              Double high, String unit) {
        Double value = (Double) row.get(key);
        if (value != null && value >= high) {
            alerts.add(alert(label, value, unit, ">= " + high));
        }
    }

    private void addLowAlert(List<Map<String, Object>> alerts, Map<String, Object> row, String label, String key,
                             Double low, String unit) {
        Double value = (Double) row.get(key);
        if (value != null && value < low) {
            alerts.add(alert(label, value, unit, "< " + low));
        }
    }

    private void addTextAlert(List<Map<String, Object>> alerts, Map<String, Object> row, String label, String key,
                              String marker) {
        Object value = row.get(key);
        if (value != null && value.toString().contains(marker)) {
            alerts.add(alert(label, value, "", marker));
        }
    }

    private Map<String, Object> alert(String label, Object value, String unit, String threshold) {
        Map<String, Object> alert = new LinkedHashMap<String, Object>();
        alert.put("label", label);
        alert.put("value", value);
        alert.put("unit", unit);
        alert.put("threshold", threshold);
        return alert;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castAlerts(Object alerts) {
        return alerts == null ? Collections.<Map<String, Object>>emptyList() : (List<Map<String, Object>>) alerts;
    }

    private List<Object> values(List<Map<String, Object>> rows, String key) {
        List<Object> values = new ArrayList<Object>();
        for (Map<String, Object> row : rows) {
            values.add(row.get(key));
        }
        return values;
    }

    private String toJsonArray(List<?> items, boolean quoted) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Object item = items.get(i);
            if (item == null) {
                sb.append("null");
            }
            else if (quoted) {
                sb.append("\"").append(item.toString().replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            }
            else {
                sb.append(item.toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
