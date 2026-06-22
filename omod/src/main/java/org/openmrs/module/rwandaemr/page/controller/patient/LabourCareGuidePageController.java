package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Page controller for the WHO Labour Care Guide monitoring dashboard.
 * Loads all Labour Monitoring encounters and extracts time-series obs
 * for chart rendering and real-time status display.
 */
public class LabourCareGuidePageController {

    // Encounter type UUIDs
    static final String LCG_MONITORING_TYPE_UUID   = "9859a7fd-c9fb-42dd-a7a3-e6091f6de4ab";
    static final String LCG_BIRTH_SUMMARY_TYPE_UUID = "8cf89189-b0cf-43f1-9e77-4c8a4ac83058";
    static final String MATERNITY_ADMISSION_TYPE_UUID = "9facd372-0d04-4d32-86b6-dab46a7315d5";

    // Concept UUIDs for charted observations
    static final String CONCEPT_FHR               = "57c934dd-a086-4e73-a9b8-3b9c1abef1b1";
    static final String CONCEPT_PULSE             = "3ce93824-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_SBP               = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_DBP               = "3ce93694-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_TEMPERATURE       = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_RESPIRATIONS      = "3ceb11f8-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_CERVICAL_DILATION = "206dedbc-ea4a-4298-bb5a-a326526a082e";
    static final String CONCEPT_DESCENT           = "927948ed-8be0-4917-9199-9dc3fb776f86";
    static final String CONCEPT_CONTRACTIONS      = "5965b71c-4214-4780-a74e-2de09b06a552";
    static final String CONCEPT_CONTRACTION_DUR   = "0df7b54f-820d-4ad2-b8c3-ea4eac3e67d7";
    static final String CONCEPT_OXYTOCIN_UNITS    = "324e78bc-68cc-4dcc-ab81-54b9fd4b229f";
    static final String CONCEPT_OXYTOCIN_DROPS    = "11826120-205d-42db-be63-2d304d494bc8";

    // Concept UUIDs for admission header data
    static final String CONCEPT_LABOUR_ONSET      = "45c9dd63-5fea-4577-aa5a-83b82db76314";
    static final String CONCEPT_ACTIVE_LABOUR_DX  = "e45477c7-b3b1-4bb5-a29c-9b2cc714ccf5";
    static final String CONCEPT_GRAVIDA           = "3cee82de-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_PARITY            = "3cd6dda0-26fe-102b-80cb-0017a47871b2";
    static final String CONCEPT_RISK_FACTORS      = "f7e54349-17f1-46fd-9fce-0c787c6b8042";

    // Alert threshold constants
    static final double ALERT_FHR_LOW   = 110.0;
    static final double ALERT_FHR_HIGH  = 160.0;
    static final double ALERT_PULSE_LOW = 60.0;
    static final double ALERT_PULSE_HIGH= 120.0;
    static final double ALERT_SBP_LOW   = 80.0;
    static final double ALERT_SBP_HIGH  = 140.0;
    static final double ALERT_DBP_HIGH  = 90.0;
    static final double ALERT_TEMP_LOW  = 35.0;
    static final double ALERT_TEMP_HIGH = 37.5;
    static final double ALERT_RESP_LOW  = 12.0;
    static final double ALERT_RESP_HIGH = 20.0;
    static final double ALERT_CONT_LOW  = 2.0;
    static final double ALERT_CONT_HIGH = 5.0;

    public void get(PageModel model,
                    UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "patientId") Patient patient,
                    @SpringBean("encounterService") EncounterService encounterService,
                    @SpringBean("conceptService") ConceptService conceptService) {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);

        SimpleDateFormat dtFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        // ── 1. Load monitoring encounters (ascending, for chart order) ──────────
        EncounterType monitoringType = encounterService.getEncounterTypeByUuid(LCG_MONITORING_TYPE_UUID);
        List<Encounter> monitoringEncounters = new ArrayList<>();
        if (monitoringType != null) {
            monitoringEncounters = encounterService.getEncounters(
                new EncounterSearchCriteriaBuilder()
                    .setPatient(patient)
                    .setEncounterTypes(Collections.singletonList(monitoringType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()
            );
            monitoringEncounters.sort(Comparator.comparing(Encounter::getEncounterDatetime));
        }

        // ── 2. Build time-series data maps for charts ────────────────────────────
        List<String> labels = new ArrayList<>();
        List<Double> fhrData         = new ArrayList<>();
        List<Double> pulseData        = new ArrayList<>();
        List<Double> sbpData          = new ArrayList<>();
        List<Double> dbpData          = new ArrayList<>();
        List<Double> tempData         = new ArrayList<>();
        List<Double> respData         = new ArrayList<>();
        List<Double> cervixData       = new ArrayList<>();
        List<Double> descentData      = new ArrayList<>();
        List<Double> contractionsData = new ArrayList<>();

        List<Map<String, Object>> activeAlerts = new ArrayList<>();

        for (Encounter enc : monitoringEncounters) {
            String timeLabel = dtFmt.format(enc.getEncounterDatetime());
            labels.add(timeLabel);

            Double fhr    = getNumericObs(enc, conceptService, CONCEPT_FHR);
            Double pulse  = getNumericObs(enc, conceptService, CONCEPT_PULSE);
            Double sbp    = getNumericObs(enc, conceptService, CONCEPT_SBP);
            Double dbp    = getNumericObs(enc, conceptService, CONCEPT_DBP);
            Double temp   = getNumericObs(enc, conceptService, CONCEPT_TEMPERATURE);
            Double resp   = getNumericObs(enc, conceptService, CONCEPT_RESPIRATIONS);
            Double cervix = getNumericObs(enc, conceptService, CONCEPT_CERVICAL_DILATION);
            Double desc   = getNumericObs(enc, conceptService, CONCEPT_DESCENT);
            Double cont   = getNumericObs(enc, conceptService, CONCEPT_CONTRACTIONS);

            fhrData.add(fhr);
            pulseData.add(pulse);
            sbpData.add(sbp);
            dbpData.add(dbp);
            tempData.add(temp);
            respData.add(resp);
            cervixData.add(cervix);
            descentData.add(desc);
            contractionsData.add(cont);
        }

        // ── 3. Compute latest-value alerts ──────────────────────────────────────
        if (!monitoringEncounters.isEmpty()) {
            Encounter latest = monitoringEncounters.get(monitoringEncounters.size() - 1);
            Double fhr   = getNumericObs(latest, conceptService, CONCEPT_FHR);
            Double pulse = getNumericObs(latest, conceptService, CONCEPT_PULSE);
            Double sbp   = getNumericObs(latest, conceptService, CONCEPT_SBP);
            Double dbp   = getNumericObs(latest, conceptService, CONCEPT_DBP);
            Double temp  = getNumericObs(latest, conceptService, CONCEPT_TEMPERATURE);
            Double resp  = getNumericObs(latest, conceptService, CONCEPT_RESPIRATIONS);
            Double cont  = getNumericObs(latest, conceptService, CONCEPT_CONTRACTIONS);

            if (fhr != null && (fhr < ALERT_FHR_LOW || fhr >= ALERT_FHR_HIGH)) {
                activeAlerts.add(alert("FHR", fhr, "bpm", "< " + (int)ALERT_FHR_LOW + " or ≥ " + (int)ALERT_FHR_HIGH));
            }
            if (pulse != null && (pulse < ALERT_PULSE_LOW || pulse >= ALERT_PULSE_HIGH)) {
                activeAlerts.add(alert("Pulse", pulse, "bpm", "< " + (int)ALERT_PULSE_LOW + " or ≥ " + (int)ALERT_PULSE_HIGH));
            }
            if (sbp != null && (sbp < ALERT_SBP_LOW || sbp >= ALERT_SBP_HIGH)) {
                activeAlerts.add(alert("Systolic BP", sbp, "mmHg", "< " + (int)ALERT_SBP_LOW + " or ≥ " + (int)ALERT_SBP_HIGH));
            }
            if (dbp != null && dbp >= ALERT_DBP_HIGH) {
                activeAlerts.add(alert("Diastolic BP", dbp, "mmHg", "≥ " + (int)ALERT_DBP_HIGH));
            }
            if (temp != null && (temp < ALERT_TEMP_LOW || temp >= ALERT_TEMP_HIGH)) {
                activeAlerts.add(alert("Temperature", temp, "°C", "< " + ALERT_TEMP_LOW + " or ≥ " + ALERT_TEMP_HIGH));
            }
            if (resp != null && (resp < ALERT_RESP_LOW || resp >= ALERT_RESP_HIGH)) {
                activeAlerts.add(alert("Respirations", resp, "/min", "< " + (int)ALERT_RESP_LOW + " or ≥ " + (int)ALERT_RESP_HIGH));
            }
            if (cont != null && (cont <= ALERT_CONT_LOW || cont > ALERT_CONT_HIGH)) {
                activeAlerts.add(alert("Contractions/10min", cont, "", "≤ " + (int)ALERT_CONT_LOW + " or > " + (int)ALERT_CONT_HIGH));
            }
        }

        // ── 4. Load admission data (gravida, parity, onset, risk factors) ────────
        Map<String, String> admissionData = new LinkedHashMap<>();
        EncounterType admissionType = encounterService.getEncounterTypeByUuid(MATERNITY_ADMISSION_TYPE_UUID);
        if (admissionType != null) {
            List<Encounter> admEncounters = encounterService.getEncounters(
                new EncounterSearchCriteriaBuilder()
                    .setPatient(patient)
                    .setEncounterTypes(Collections.singletonList(admissionType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()
            );
            admEncounters.sort(Comparator.comparing(Encounter::getEncounterDatetime).reversed());
            if (!admEncounters.isEmpty()) {
                Encounter adm = admEncounters.get(0);
                Double gravida = getNumericObs(adm, conceptService, CONCEPT_GRAVIDA);
                Double parity  = getNumericObs(adm, conceptService, CONCEPT_PARITY);
                String onset   = getDatetimeObs(adm, conceptService, CONCEPT_LABOUR_ONSET, dtFmt);
                String activeDx = getDatetimeObs(adm, conceptService, CONCEPT_ACTIVE_LABOUR_DX, dtFmt);
                String risks   = getTextObs(adm, conceptService, CONCEPT_RISK_FACTORS);
                if (gravida != null) admissionData.put("Gravida", String.valueOf(gravida.intValue()));
                if (parity  != null) admissionData.put("Parity",  String.valueOf(parity.intValue()));
                if (onset   != null) admissionData.put("Labour Onset", onset);
                if (activeDx != null) admissionData.put("Active Labour Diagnosed", activeDx);
                if (risks   != null && !risks.isEmpty()) admissionData.put("Risk Factors", risks);
            }
        }

        // ── 5. Check if birth summary exists ────────────────────────────────────
        boolean birthSummaryExists = false;
        EncounterType birthSummaryType = encounterService.getEncounterTypeByUuid(LCG_BIRTH_SUMMARY_TYPE_UUID);
        if (birthSummaryType != null) {
            List<Encounter> birthEncs = encounterService.getEncounters(
                new EncounterSearchCriteriaBuilder()
                    .setPatient(patient)
                    .setEncounterTypes(Collections.singletonList(birthSummaryType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()
            );
            birthSummaryExists = !birthEncs.isEmpty();
        }

        // ── 6. Pass to model ─────────────────────────────────────────────────────
        model.addAttribute("monitoringEncounters", monitoringEncounters);
        model.addAttribute("labels",          toJsonArray(labels, true));
        model.addAttribute("fhrData",         toJsonArray(fhrData, false));
        model.addAttribute("pulseData",       toJsonArray(pulseData, false));
        model.addAttribute("sbpData",         toJsonArray(sbpData, false));
        model.addAttribute("dbpData",         toJsonArray(dbpData, false));
        model.addAttribute("tempData",        toJsonArray(tempData, false));
        model.addAttribute("respData",        toJsonArray(respData, false));
        model.addAttribute("cervixData",      toJsonArray(cervixData, false));
        model.addAttribute("descentData",     toJsonArray(descentData, false));
        model.addAttribute("contractionsData",toJsonArray(contractionsData, false));
        model.addAttribute("activeAlerts",    activeAlerts);
        model.addAttribute("admissionData",   admissionData);
        model.addAttribute("birthSummaryExists", birthSummaryExists);
        model.addAttribute("monitoringEncounterTypeUuid", LCG_MONITORING_TYPE_UUID);
        model.addAttribute("birthSummaryEncounterTypeUuid", LCG_BIRTH_SUMMARY_TYPE_UUID);
        model.addAttribute("postpartumEncounterTypeUuid",   "bb6c6c19-299d-4b70-80d0-cc541edbbbea");
    }

    // ── Helper: extract numeric obs from encounter by concept UUID ───────────────
    private Double getNumericObs(Encounter encounter, ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByUuid(conceptUuid);
        if (concept == null) return null;
        for (Obs obs : encounter.getAllObs()) {
            if (!obs.isVoided() && obs.getConcept().equals(concept)) {
                return obs.getValueNumeric();
            }
        }
        return null;
    }

    private String getTextObs(Encounter encounter, ConceptService conceptService, String conceptUuid) {
        Concept concept = conceptService.getConceptByUuid(conceptUuid);
        if (concept == null) return null;
        for (Obs obs : encounter.getAllObs()) {
            if (!obs.isVoided() && obs.getConcept().equals(concept)) {
                return obs.getValueText();
            }
        }
        return null;
    }

    private String getDatetimeObs(Encounter encounter, ConceptService conceptService, String conceptUuid, SimpleDateFormat fmt) {
        Concept concept = conceptService.getConceptByUuid(conceptUuid);
        if (concept == null) return null;
        for (Obs obs : encounter.getAllObs()) {
            if (!obs.isVoided() && obs.getConcept().equals(concept)) {
                if (obs.getValueDatetime() != null) {
                    return fmt.format(obs.getValueDatetime());
                }
            }
        }
        return null;
    }

    // ── Helper: build alert map ──────────────────────────────────────────────────
    private Map<String, Object> alert(String label, Double value, String unit, String threshold) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("threshold", threshold);
        return m;
    }

    // ── Helper: serialize a list to a JSON array string ──────────────────────────
    private String toJsonArray(List<?> items, boolean quoted) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            Object item = items.get(i);
            if (item == null) {
                sb.append("null");
            } else if (quoted) {
                sb.append("\"").append(item.toString().replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(item.toString());
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
