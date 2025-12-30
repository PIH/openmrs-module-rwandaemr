package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.mohappointment.model.Appointment;
import org.openmrs.module.mohappointment.utils.AppointmentUtil;

import java.util.Date;

/**
 * Updates waiting appointments if needed due to services requested in registration encounters in active visits
 */
public class CreateAppointmentAction implements CustomFormSubmissionAction {

    protected Log log = LogFactory.getLog(getClass());

    @Override
    public void applyAction(FormEntrySession fes) {
        try {
            FormEntryContext.Mode mode = fes.getContext().getMode();
            log.debug("In " + getClass().getSimpleName() + " mode = " + mode);
            if (mode == FormEntryContext.Mode.ENTER || mode == FormEntryContext.Mode.EDIT) {
                Encounter encounter = fes.getEncounter();
                if (encounter == null) {
                    log.error("Encounter is null, skipping");
                    return;
                }
                EncounterType encounterType = encounter.getEncounterType();
                Visit visit = encounter.getVisit();
                log.debug("Encounter: " + encounter.getUuid() + "; type = " + encounterType + "; visit = " + visit);
                if (encounter.getEncounterType().equals(getRegistrationEncounterType())) {
                    if (visit.getStopDatetime() == null) {
                        log.debug("This is for an active visit in a registration encounter, proceeding");
                        Concept serviceRequestedConcept = getServiceRequestedConcept();
                        for (Obs o : encounter.getObsAtTopLevel(true)) {
                            if (o.getConcept().equals(serviceRequestedConcept)) {
                                if (BooleanUtils.isTrue(o.getVoided())) {
                                    log.info("Voiding appointment for obs: " + o);
                                    AppointmentUtil.voidAppointmentByObs(o);
                                } else {
                                    log.debug("Creating appointment for obs: " + o);
                                    Appointment a = new Appointment();
                                    a.setAppointmentDate(new Date());
                                    a.setCreatedDate(new Date());
                                    a.setCreator(Context.getAuthenticatedUser());
                                    a.setProvider(encounter.getEncounterProviders().iterator().next().getProvider().getPerson());
                                    a.setEncounter(encounter);
                                    a.setReason(o);
                                    a.setNote("This is a waiting patient to the " + o.getValueCoded().getName());
                                    a.setPatient(encounter.getPatient());
                                    a.setLocation(encounter.getLocation());
                                    a.setService(AppointmentUtil.getServiceByConcept(o.getValueCoded()));
                                    AppointmentUtil.saveWaitingAppointment(a);
                                    log.info("Appointment created: " + a);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("An error occurred creating appointment for patient", e);
        }
    }

    EncounterType getRegistrationEncounterType() {
        return Context.getRegisteredComponents(RwandaEmrConfig.class).get(0).getRegistrationEncounterType();
    }

    Concept getServiceRequestedConcept() {
        String gpVal = Context.getAdministrationService().getGlobalProperty("registration.serviceRequestedConcept");
        if (StringUtils.isNotBlank(gpVal)) {
            return HtmlFormEntryUtil.getConcept(gpVal);
        }
        return null;
    }
}
