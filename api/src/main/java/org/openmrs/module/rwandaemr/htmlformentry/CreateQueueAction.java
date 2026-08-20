package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

public class CreateQueueAction implements CustomFormSubmissionAction {

    private final Log log = LogFactory.getLog(getClass());

    private final CreateAppointmentAction appointmentAction = new CreateAppointmentAction();

    public CreateQueueAction() {
        log.warn("CreateQueueAction constructed");
    }

    @Override
    public void applyAction(FormEntrySession fes) {
        log.warn("CreateQueueAction reached applyAction: " + describeFormEntrySession(fes));
        runQueueStep(fes);
        runAppointmentStep(fes);
        log.warn("CreateQueueAction finished applyAction: " + describeFormEntrySession(fes));
    }

    protected void createAppointment(FormEntrySession fes) {
        appointmentAction.applyAction(fes);
    }

    protected EncounterType getRegistrationEncounterType() {
        return appointmentAction.getRegistrationEncounterType();
    }

    protected void createQueueEntry(FormEntrySession fes) {
        if (fes == null) {
            log.warn("Skipping queue creation because FormEntrySession is null");
            return;
        }
        if (fes.getContext() == null) {
            log.warn("Skipping queue creation because FormEntryContext is null");
            return;
        }
        FormEntryContext.Mode mode = fes.getContext().getMode();
        log.warn("CreateQueueAction queue step mode: " + mode);
        if (mode != FormEntryContext.Mode.ENTER && mode != FormEntryContext.Mode.EDIT) {
            log.warn("Skipping queue creation because mode is not ENTER or EDIT: " + mode);
            return;
        }
        Encounter encounter = fes.getEncounter();
        if (encounter == null) {
            log.warn("Skipping queue creation because encounter is null");
            return;
        }
        log.warn("CreateQueueAction queue step encounter: " + describeEncounter(encounter));
        EncounterType registrationEncounterType = getRegistrationEncounterType();
        if (registrationEncounterType == null) {
            log.warn("Skipping queue creation because registration encounter type is not configured");
            return;
        }
        if (encounter.getEncounterType() == null || !encounter.getEncounterType().equals(registrationEncounterType)) {
            log.warn("Skipping queue creation because encounter type is not registration. Encounter type: "
                    + describeEncounterType(encounter.getEncounterType()) + ", registration type: "
                    + describeEncounterType(registrationEncounterType));
            return;
        }
        log.warn("CreateQueueAction calling QueueService.addPatientToQueueFromRegistration");
        QueueEntry queueEntry = Context.getService(QueueService.class).addPatientToQueueFromRegistration(encounter);
        log.warn("CreateQueueAction queue step completed. Queue entry: " + describeQueueEntry(queueEntry));
    }

    private void runQueueStep(FormEntrySession fes) {
        log.warn("CreateQueueAction starting queue step");
        try {
            createQueueEntry(fes);
        }
        catch (Throwable t) {
            log.error("CreateQueueAction queue step failed. Registration submission will continue to appointment step.", t);
        }
    }

    private void runAppointmentStep(FormEntrySession fes) {
        log.warn("CreateQueueAction starting appointment step");
        try {
            createAppointment(fes);
            log.warn("CreateQueueAction appointment step completed");
        }
        catch (Throwable t) {
            log.error("CreateQueueAction appointment step failed. The error was swallowed to avoid blocking registration submission.", t);
        }
    }

    private String describeFormEntrySession(FormEntrySession fes) {
        if (fes == null) {
            return "FormEntrySession=null";
        }
        try {
            FormEntryContext.Mode mode = fes.getContext() == null ? null : fes.getContext().getMode();
            return "mode=" + mode + ", encounter={" + describeEncounter(fes.getEncounter()) + "}";
        }
        catch (Throwable t) {
            return "unable to describe FormEntrySession: " + t.getClass().getName() + ": " + t.getMessage();
        }
    }

    private String describeEncounter(Encounter encounter) {
        if (encounter == null) {
            return "null";
        }
        String patientUuid = encounter.getPatient() == null ? null : encounter.getPatient().getUuid();
        String visitUuid = encounter.getVisit() == null ? null : encounter.getVisit().getUuid();
        String locationName = encounter.getLocation() == null ? null : encounter.getLocation().getName();
        return "uuid=" + encounter.getUuid()
                + ", type=" + describeEncounterType(encounter.getEncounterType())
                + ", patient=" + patientUuid
                + ", visit=" + visitUuid
                + ", location=" + locationName;
    }

    private String describeEncounterType(EncounterType encounterType) {
        if (encounterType == null) {
            return "null";
        }
        return encounterType.getName() + "(" + encounterType.getUuid() + ")";
    }

    private String describeQueueEntry(QueueEntry queueEntry) {
        if (queueEntry == null) {
            return "null";
        }
        return "uuid=" + queueEntry.getUuid()
                + ", queueNumber=" + queueEntry.getQueueNumber()
                + ", status=" + queueEntry.getStatus()
                + ", serviceRequested=" + (queueEntry.getServiceRequestedConcept() == null
                        ? null : queueEntry.getServiceRequestedConcept().getUuid());
    }
}
