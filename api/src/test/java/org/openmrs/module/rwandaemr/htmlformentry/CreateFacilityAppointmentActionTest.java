package org.openmrs.module.rwandaemr.htmlformentry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;

public class CreateFacilityAppointmentActionTest {

    @Test
    public void shouldCreateAppointmentForSubmittedEncounterPatient() {
        FacilityAppointmentService appointmentService = mock(FacilityAppointmentService.class);
        FormEntrySession session = mock(FormEntrySession.class);
        Patient patient = new Patient(21);
        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        when(session.getEncounter()).thenReturn(encounter);
        CreateFacilityAppointmentAction action = new CreateFacilityAppointmentAction(31, "Booked from follow-up form") {
            @Override
            protected FacilityAppointmentService getAppointmentService() {
                return appointmentService;
            }
        };

        action.applyAction(session);

        verify(appointmentService).requestAppointment(patient, 31, "Booked from follow-up form");
    }
}
