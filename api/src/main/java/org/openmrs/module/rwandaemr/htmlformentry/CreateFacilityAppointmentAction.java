package org.openmrs.module.rwandaemr.htmlformentry;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;

/** Creates a facility appointment after HTML Form Entry successfully saves the encounter. */
public class CreateFacilityAppointmentAction implements CustomFormSubmissionAction {

    private final Integer scheduleId;
    private final String notes;

    public CreateFacilityAppointmentAction(Integer scheduleId, String notes) {
        this.scheduleId = scheduleId;
        this.notes = notes;
    }

    @Override
    public void applyAction(FormEntrySession session) {
        Encounter encounter = session.getEncounter();
        Patient patient = encounter == null ? null : encounter.getPatient();
        if (patient == null) {
            throw new IllegalStateException("Unable to create an appointment without a patient encounter");
        }
        getAppointmentService().requestAppointment(patient, scheduleId, notes);
    }

    protected FacilityAppointmentService getAppointmentService() {
        return Context.getService(FacilityAppointmentService.class);
    }
}
