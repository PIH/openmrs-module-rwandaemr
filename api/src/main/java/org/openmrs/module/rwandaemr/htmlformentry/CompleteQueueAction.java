package org.openmrs.module.rwandaemr.htmlformentry;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

/**
 * Completes the active queue entry when a discharge form is submitted.
 */
public class CompleteQueueAction implements CustomFormSubmissionAction {

    protected final Log log = LogFactory.getLog(getClass());

    @Override
    public void applyAction(FormEntrySession session) {
        if (session == null || session.getContext() == null) {
            log.warn("Skipping queue completion because the FormEntrySession or FormEntryContext is null");
            return;
        }
        if (!isSupportedMode(session.getContext().getMode())) {
            return;
        }

        completeQueue(session.getEncounter(), session.getPatient());
    }

    protected void completeQueue(Encounter encounter, Patient sessionPatient) {
        Patient patient = encounter != null && encounter.getPatient() != null
                ? encounter.getPatient() : sessionPatient;
        if (patient == null) {
            log.warn("Skipping queue completion because the discharge submission has no patient");
            return;
        }

        QueueService queueService = getQueueService();
        Visit visit = encounter == null ? null : encounter.getVisit();
        QueueEntry queueEntry = visit == null
                ? queueService.getActiveQueueEntry(patient, getReferenceDate())
                : queueService.getActiveQueueEntry(visit);
        if (queueEntry == null) {
            log.info("No active queue entry found for discharged patient " + patient.getUuid());
            return;
        }

        QueueEntry completedEntry = queueService.completeService(queueEntry);
        log.info("Completed queue entry " + completedEntry.getUuid() + " for discharged patient "
                + patient.getUuid());
    }

    protected QueueService getQueueService() {
        return Context.getService(QueueService.class);
    }

    protected Date getReferenceDate() {
        return new Date();
    }

    protected boolean isSupportedMode(FormEntryContext.Mode mode) {
        return mode == FormEntryContext.Mode.ENTER || mode == FormEntryContext.Mode.EDIT;
    }
}
