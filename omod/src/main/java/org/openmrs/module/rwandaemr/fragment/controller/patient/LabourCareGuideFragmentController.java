package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.contextmodel.PatientContextModel;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard widget fragment controller for the WHO Labour Care Guide.
 * Shows a compact summary: last monitoring time, observation count, active alerts.
 */
public class LabourCareGuideFragmentController {

    protected final Log log = LogFactory.getLog(LabourCareGuideFragmentController.class);

    private static final String LCG_MONITORING_TYPE_UUID   = "9859a7fd-c9fb-42dd-a7a3-e6091f6de4ab";
    private static final String LCG_BIRTH_SUMMARY_TYPE_UUID = "8cf89189-b0cf-43f1-9e77-4c8a4ac83058";

    private static final String CONCEPT_FHR   = "57c934dd-a086-4e73-a9b8-3b9c1abef1b1";
    private static final String CONCEPT_PULSE = "3ce93824-26fe-102b-80cb-0017a47871b2";
    private static final String CONCEPT_SBP   = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    private static final String CONCEPT_DBP   = "3ce93694-26fe-102b-80cb-0017a47871b2";
    private static final String CONCEPT_TEMP  = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    private static final String CONCEPT_CONT  = "5965b71c-4214-4780-a74e-2de09b06a552";
    private static final String CONCEPT_CERVIX = "206dedbc-ea4a-4298-bb5a-a326526a082e";

    public void controller(FragmentConfiguration config,
                           FragmentModel model,
                           UiSessionContext sessionContext,
                           @SpringBean("encounterService") EncounterService encounterService,
                           @SpringBean("conceptService") ConceptService conceptService,
                           @InjectBeans PatientDomainWrapper patientWrapper) {

        config.require("patient");
        Object patient = config.get("patient");

        if (patient instanceof Patient) {
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        } else if (patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }

        AppContextModel contextModel = sessionContext.generateAppContextModel();
        contextModel.put("patient", new PatientContextModel(patientWrapper.getPatient()));

        // Load monitoring encounters
        EncounterType monitoringType = encounterService.getEncounterTypeByUuid(LCG_MONITORING_TYPE_UUID);
        List<Encounter> encounters = Collections.emptyList();
        if (monitoringType != null) {
            encounters = encounterService.getEncounters(
                new EncounterSearchCriteriaBuilder()
                    .setPatient(patientWrapper.getPatient())
                    .setEncounterTypes(Collections.singletonList(monitoringType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()
            );
            encounters.sort(Comparator.comparing(Encounter::getEncounterDatetime).reversed());
        }

        // Gather latest values and check alerts
        Map<String, String> latestValues = new LinkedHashMap<>();
        int alertCount = 0;
        Encounter lastEnc = null;

        if (!encounters.isEmpty()) {
            lastEnc = encounters.get(0);
            Double fhr   = getNumericObs(lastEnc, conceptService, CONCEPT_FHR);
            Double pulse = getNumericObs(lastEnc, conceptService, CONCEPT_PULSE);
            Double sbp   = getNumericObs(lastEnc, conceptService, CONCEPT_SBP);
            Double dbp   = getNumericObs(lastEnc, conceptService, CONCEPT_DBP);
            Double temp  = getNumericObs(lastEnc, conceptService, CONCEPT_TEMP);
            Double cont  = getNumericObs(lastEnc, conceptService, CONCEPT_CONT);
            Double cervix = getNumericObs(lastEnc, conceptService, CONCEPT_CERVIX);

            if (fhr   != null) { latestValues.put("FHR", fhr.intValue() + " bpm");    if (fhr < 110 || fhr >= 160) alertCount++; }
            if (pulse != null) { latestValues.put("Pulse", pulse.intValue() + " bpm"); if (pulse < 60 || pulse >= 120) alertCount++; }
            if (sbp   != null) { latestValues.put("SBP", sbp.intValue() + " mmHg");   if (sbp < 80 || sbp >= 140) alertCount++; }
            if (dbp   != null) { latestValues.put("DBP", dbp.intValue() + " mmHg");   if (dbp >= 90) alertCount++; }
            if (temp  != null) { latestValues.put("Temp", Math.round(temp * 10.0) / 10.0 + "°C"); if (temp < 35 || temp >= 37.5) alertCount++; }
            if (cont  != null) { latestValues.put("Contractions", cont.intValue() + "/10min");      if (cont <= 2 || cont > 5) alertCount++; }
            if (cervix != null) latestValues.put("Cervix", cervix + " cm");
        }

        // Check if birth summary exists
        boolean birthSummaryExists = false;
        EncounterType birthType = encounterService.getEncounterTypeByUuid(LCG_BIRTH_SUMMARY_TYPE_UUID);
        if (birthType != null) {
            List<Encounter> birthEncs = encounterService.getEncounters(
                new EncounterSearchCriteriaBuilder()
                    .setPatient(patientWrapper.getPatient())
                    .setEncounterTypes(Collections.singletonList(birthType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()
            );
            birthSummaryExists = !birthEncs.isEmpty();
        }

        model.addAttribute("patient", patientWrapper);
        model.addAttribute("encounterCount", encounters.size());
        model.addAttribute("lastEncounter", lastEnc);
        model.addAttribute("latestValues", latestValues);
        model.addAttribute("alertCount", alertCount);
        model.addAttribute("birthSummaryExists", birthSummaryExists);
    }

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
}
