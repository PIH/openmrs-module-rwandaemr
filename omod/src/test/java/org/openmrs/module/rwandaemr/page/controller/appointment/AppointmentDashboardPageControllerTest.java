package org.openmrs.module.rwandaemr.page.controller.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openmrs.Encounter;
import org.openmrs.Patient;

public class AppointmentDashboardPageControllerTest {

    @Test
    public void shouldSelectLatestRegistrationEncounterForEachBookedPatient() {
        Patient bookedPatient = new Patient(12);
        Patient otherPatient = new Patient(13);
        Encounter earlier = encounter(101, bookedPatient, new Date(1_000L));
        Encounter latest = encounter(102, bookedPatient, new Date(2_000L));
        Encounter other = encounter(103, otherPatient, new Date(3_000L));

        Map<Integer, Integer> result = AppointmentDashboardPageController.latestEncounterIdsByPatient(
                Arrays.asList(latest, other, earlier), Collections.singleton(bookedPatient.getId()));

        assertEquals(1, result.size());
        assertEquals(latest.getId(), result.get(bookedPatient.getId()));
    }

    private Encounter encounter(Integer id, Patient patient, Date encounterDatetime) {
        Encounter encounter = new Encounter(id);
        encounter.setPatient(patient);
        encounter.setEncounterDatetime(encounterDatetime);
        return encounter;
    }
}
