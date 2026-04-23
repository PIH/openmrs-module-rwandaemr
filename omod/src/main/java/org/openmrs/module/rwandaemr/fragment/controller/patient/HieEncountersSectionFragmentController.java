package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.ShrEncounter;
import org.openmrs.module.rwandaemr.integration.ShrEncounterProvider;
import org.openmrs.module.rwandaemr.integration.ShrEncounterTranslator;
import org.openmrs.module.rwandaemr.integration.ShrVisit;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.fragment.action.ObjectResult;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class HieEncountersSectionFragmentController {

    protected final Log log = LogFactory.getLog(HieEncountersSectionFragmentController.class);
    private static final SimpleDateFormat ENCOUNTER_DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public void controller(FragmentConfiguration config,
    PageModel pageModel,
    FragmentModel model,
    UiUtils ui,
    UiSessionContext sessionContext,
    @InjectBeans PatientDomainWrapper patientWrapper,
    @SpringBean IntegrationConfig integrationConfig,
    @SpringBean RwandaEmrConfig rwandaEmrConfig
    ) {
        config.require("patient");
        Object patient = config.get("patient");

        if(patient instanceof Patient){
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        } else if(patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }
        model.addAttribute("error", "");
        model.addAttribute("upid", "");
        try {
            if (!integrationConfig.isHieEnabled()) {
                model.addAttribute("error", "Hie is not enabled on this server");
                return;
            }
            PatientIdentifier upidPatientIdentifier = patientWrapper.getPatient().getPatientIdentifier(rwandaEmrConfig.getUPID());
            if (upidPatientIdentifier == null || upidPatientIdentifier.getIdentifier() == null) {
                model.addAttribute("error", "No UPID found");
                return;
            }
            model.addAttribute("upid", upidPatientIdentifier.getIdentifier());
        } catch (Exception e) {
            model.addAttribute("error", "Error preparing HIE history: " + e.getClass().getSimpleName());
        }
    }

    public FragmentActionResult loadPastHistory(
            @RequestParam("upid") String upid,
            @SpringBean IntegrationConfig integrationConfig,
            @SpringBean ShrEncounterProvider shrEncounterProvider,
            @SpringBean ShrEncounterTranslator shrEncounterTranslator
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", "");
        result.put("visits", new ArrayList<>());
        try {
            if (!integrationConfig.isHieEnabled()) {
                result.put("message", "Hie is not enabled on this server");
                return new ObjectResult(result);
            }
            if (upid == null || upid.trim().isEmpty()) {
                result.put("message", "No UPID found");
                return new ObjectResult(result);
            }

            List<ShrEncounter> shrEncounters = shrEncounterProvider.fetchEncounterFromShr(upid.trim());
            List<ShrVisit> visits = new ArrayList<>();
            List<String> visitedLocation = new ArrayList<>();
            List<Map<String, Object>> visitPayload = new ArrayList<>();

            if (shrEncounters != null && !shrEncounters.isEmpty()) {
                for (ShrEncounter shrEncounter: shrEncounters) {
                    Encounter myEncounter = shrEncounterTranslator.toEncounter(shrEncounter);
                    String locationName = myEncounter.getLocation() != null ? myEncounter.getLocation().getName() : "Unspecified";
                    int locationIndex = visitedLocation.indexOf(locationName);
                    if (locationIndex == -1) {
                        ShrVisit shrVisit = new ShrVisit();
                        shrVisit.setLocation(locationName);
                        shrVisit.clearEncounters();
                        shrVisit.addEncounter(myEncounter);
                        visits.add(shrVisit);
                        visitedLocation.add(locationName);
                    } else {
                        visits.get(locationIndex).addEncounter(myEncounter);
                    }
                }
            }

            for (ShrVisit visit : visits) {
                Map<String, Object> visitMap = new LinkedHashMap<>();
                visitMap.put("location", visit.getLocation());
                List<Map<String, Object>> encounterPayload = new ArrayList<>();
                if (visit.getEncounters() != null) {
                    for (Encounter e : visit.getEncounters()) {
                        Map<String, Object> encounterMap = new LinkedHashMap<>();
                        encounterMap.put("uuid", e.getUuid());
                        encounterMap.put("encounterDatetime",
                                e.getEncounterDatetime() != null ? ENCOUNTER_DATETIME_FORMAT.format(e.getEncounterDatetime()) : "");
                        encounterMap.put("encounterType", e.getVoidReason());
                        encounterMap.put("location", e.getLocation() != null ? e.getLocation().getName() : "");
                        encounterPayload.add(encounterMap);
                    }
                }
                visitMap.put("encounters", encounterPayload);
                visitPayload.add(visitMap);
            }

            result.put("success", true);
            result.put("visits", visitPayload);
            return new ObjectResult(result);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                errorMsg = "Error retrieving HIE encounters: " + e.getClass().getSimpleName();
            }
            result.put("message", errorMsg);
            return new ObjectResult(result);
        }
    }
}
