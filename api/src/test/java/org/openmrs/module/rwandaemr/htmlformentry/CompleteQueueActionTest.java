package org.openmrs.module.rwandaemr.htmlformentry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

public class CompleteQueueActionTest {

    private QueueService queueService;

    private Date referenceDate;

    private CompleteQueueAction action;

    @BeforeEach
    public void setUp() {
        queueService = mock(QueueService.class);
        referenceDate = new Date(5_000L);
        action = new CompleteQueueAction() {
            @Override
            protected QueueService getQueueService() {
                return queueService;
            }

            @Override
            protected Date getReferenceDate() {
                return referenceDate;
            }
        };
    }

    @Test
    public void shouldImplementCustomFormSubmissionAction() {
        assertTrue(CustomFormSubmissionAction.class.isAssignableFrom(CompleteQueueAction.class));
    }

    @Test
    public void shouldCompleteActiveQueueEntryForDischargeVisit() {
        Patient patient = patient();
        Visit visit = new Visit();
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setVisit(visit);
        QueueEntry queueEntry = new QueueEntry();
        queueEntry.setUuid("queue-entry-uuid");
        when(queueService.getActiveQueueEntry(visit)).thenReturn(queueEntry);
        when(queueService.completeService(queueEntry)).thenReturn(queueEntry);

        action.completeQueue(encounter, patient);

        verify(queueService).completeService(queueEntry);
        verify(queueService, never()).getActiveQueueEntry(patient, referenceDate);
    }

    @Test
    public void shouldUsePatientAndDateWhenDischargeEncounterHasNoVisit() {
        Patient patient = patient();
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        QueueEntry queueEntry = new QueueEntry();
        queueEntry.setUuid("queue-entry-uuid");
        when(queueService.getActiveQueueEntry(patient, referenceDate)).thenReturn(queueEntry);
        when(queueService.completeService(queueEntry)).thenReturn(queueEntry);

        action.completeQueue(encounter, patient);

        verify(queueService).completeService(queueEntry);
    }

    @Test
    public void shouldOnlyRunForEnterAndEditModes() {
        assertTrue(action.isSupportedMode(FormEntryContext.Mode.ENTER));
        assertTrue(action.isSupportedMode(FormEntryContext.Mode.EDIT));
        assertFalse(action.isSupportedMode(FormEntryContext.Mode.VIEW));
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setUuid("patient-uuid");
        return patient;
    }
}
