package org.openmrs.module.rwandaemr.fragment.controller.patient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.ConfigUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Displays transfer shortcut link when registration encounter includes transfer-id observation.
 */
public class HieTransferSectionFragmentController {

    protected final Log log = LogFactory.getLog(HieTransferSectionFragmentController.class);

    private static final String TRANSFER_ID_CONCEPT_UUID_GP = "rwandaemr.hie.transfer_id";

    public void controller(
            FragmentConfiguration config,
            PageModel pageModel,
            FragmentModel model,
            UiUtils ui,
            @SpringBean("encounterService") EncounterService encounterService,
            @SpringBean RwandaEmrConfig rwandaEmrConfig,
            @SpringBean IntegrationConfig integrationConfig,
            @InjectBeans PatientDomainWrapper patientWrapper
    ) {
        config.require("patient");
        Object patient = config.get("patient");
        if (patient instanceof Patient) {
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        } else if (patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }

        model.addAttribute("showTransferLink", false);
        model.addAttribute("transferId", "");
        model.addAttribute("upid", "");
        model.addAttribute("error", "");

        try {
            if (!integrationConfig.isHieEnabled()) {
                model.addAttribute("error", "HIE is not enable on the server");
                return;
            }

            Patient currentPatient = patientWrapper.getPatient();
            if (currentPatient == null) {
                model.addAttribute("error", "No transfer was recorded");
                return;
            }

            PatientIdentifier upidPatientIdentifier = currentPatient.getPatientIdentifier(rwandaEmrConfig.getUPID());
            if (upidPatientIdentifier == null || upidPatientIdentifier.getIdentifier() == null
                    || upidPatientIdentifier.getIdentifier().trim().isEmpty()) {
                model.addAttribute("error", "No UPID found");
                return;
            }
            model.addAttribute("upid", upidPatientIdentifier.getIdentifier().trim());

            String transferIdConceptUuid = ConfigUtil.getProperty(TRANSFER_ID_CONCEPT_UUID_GP);
            if (StringUtils.isBlank(transferIdConceptUuid)) {
                model.addAttribute("error", "Transfer concept is not configured");
                return;
            }

            Concept transferIdConcept = Context.getConceptService().getConceptByUuid(transferIdConceptUuid.trim());
            if (transferIdConcept == null) {
                model.addAttribute("error", "Transfer concept is not configured");
                return;
            }

            List<Encounter> patientEncounters = encounterService.getEncountersByPatient(currentPatient);
            if (patientEncounters == null || patientEncounters.isEmpty()) {
                model.addAttribute("error", "No transfer was recorded");
                return;
            }

            int registrationEncounterTypeId = integrationConfig.getRegistrationEncounterType();
            if (registrationEncounterTypeId == 0) {
                model.addAttribute("error", "Registration encounter type is not configured");
                return;
            }

            List<Encounter> registrationEncounters = patientEncounters.stream()
                    .filter(e -> e != null && e.getEncounterType() != null
                            && e.getEncounterType().getEncounterTypeId() != null
                            && e.getEncounterType().getEncounterTypeId() == registrationEncounterTypeId)
                    .sorted(Comparator.comparing(Encounter::getEncounterDatetime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .collect(Collectors.toList());

            if (registrationEncounters.isEmpty()) {
                model.addAttribute("error", "No transfer was recorded");
                return;
            }

            Encounter latestRegistration = registrationEncounters.get(0);
            Set<Obs> obsSet = latestRegistration.getAllObs(false);
            if (obsSet == null || obsSet.isEmpty()) {
                model.addAttribute("error", "No transfer was recorded");
                return;
            }

            for (Obs obs : obsSet) {
                if (obs != null && obs.getConcept() != null && obs.getConcept().equals(transferIdConcept)) {
                    String transferIdValue = obs.getValueAsString(Context.getLocale());
                    if (transferIdValue != null && transferIdValue.trim().length() > 0) {
                        model.addAttribute("showTransferLink", true);
                        model.addAttribute("transferId", transferIdValue.trim());
                        return;
                    }
                }
            }
            model.addAttribute("error", "No transfer was recorded");
        } catch (Exception e) {
            log.warn("Unable to resolve transfer section state", e);
            model.addAttribute("error", "Unable to check transfer information");
        }
    }
}
