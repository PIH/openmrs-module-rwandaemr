package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;

public class LatestVitalsSummaryTest {

    private static final String HEIGHT_UUID = "3ce93cf2-26fe-102b-80cb-0017a47871b2";
    private static final String WEIGHT_UUID = "3ce93b62-26fe-102b-80cb-0017a47871b2";
    private static final String TEMPERATURE_UUID = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    private static final String SYSTOLIC_UUID = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    private static final String DIASTOLIC_UUID = "3ce93694-26fe-102b-80cb-0017a47871b2";

    @Test
    public void shouldUseTheMostRecentVitalsEncounter() {
        Encounter olderVitals = encounter(1, 1000, LatestVitalsSummary.VITALS_ENCOUNTER_TYPE_UUID);
        olderVitals.addObs(numericObs(HEIGHT_UUID, 150.0, false));
        Encounter newerVitals = encounter(2, 3000, LatestVitalsSummary.VITALS_ENCOUNTER_TYPE_UUID);
        newerVitals.addObs(numericObs(HEIGHT_UUID, 175.0, false));
        newerVitals.addObs(numericObs(WEIGHT_UUID, 70.0, false));
        newerVitals.addObs(numericObs(TEMPERATURE_UUID, 36.5, false));
        newerVitals.addObs(numericObs(SYSTOLIC_UUID, 120.0, false));
        newerVitals.addObs(numericObs(DIASTOLIC_UUID, 80.0, false));
        newerVitals.addObs(textObs("clinical-impression", "Clinical impression", "Stable"));
        Encounter unrelated = encounter(3, 4000, "not-vitals");
        unrelated.addObs(numericObs(HEIGHT_UUID, 190.0, false));

        LatestVitalsSummary summary = LatestVitalsSummary.from(
                Arrays.asList(olderVitals, unrelated, newerVitals), Locale.ENGLISH);

        assertThat(summary.getRecordedAt(), is(new Date(3000)));
        assertThat(labelsAndValues(summary), contains(
                "Height=175 cm",
                "Weight=70 kg",
                "BMI=22.9 kg/m2",
                "Temperature=36.5 \u00b0C",
                "Blood pressure=120/80 mmHg",
                "Clinical impression=Stable"));
    }

    @Test
    public void shouldExcludeVoidedEncountersAndObservations() {
        Encounter voidedEncounter = encounter(1, 3000, LatestVitalsSummary.VITALS_ENCOUNTER_TYPE_UUID);
        voidedEncounter.setVoided(true);
        voidedEncounter.addObs(numericObs(HEIGHT_UUID, 190.0, false));
        Encounter activeEncounter = encounter(2, 2000, LatestVitalsSummary.VITALS_ENCOUNTER_TYPE_UUID);
        activeEncounter.addObs(numericObs(HEIGHT_UUID, 170.0, false));
        activeEncounter.addObs(numericObs(WEIGHT_UUID, 90.0, true));

        LatestVitalsSummary summary = LatestVitalsSummary.from(
                Arrays.asList(voidedEncounter, activeEncounter), Locale.ENGLISH);

        assertThat(labelsAndValues(summary), contains("Height=170 cm"));
    }

    @Test
    public void shouldReturnNullWithoutARecordedVitalsValue() {
        Encounter unrelated = encounter(1, 1000, "not-vitals");

        assertThat(LatestVitalsSummary.from(Arrays.asList(unrelated), Locale.ENGLISH), nullValue());
    }

    private Encounter encounter(int id, long timestamp, String encounterTypeUuid) {
        EncounterType encounterType = new EncounterType();
        encounterType.setUuid(encounterTypeUuid);
        Encounter encounter = new Encounter(id);
        encounter.setEncounterType(encounterType);
        encounter.setEncounterDatetime(new Date(timestamp));
        return encounter;
    }

    private Obs numericObs(String conceptUuid, double value, boolean voided) {
        Concept concept = new Concept();
        concept.setUuid(conceptUuid);
        Obs obs = new Obs();
        obs.setConcept(concept);
        obs.setValueNumeric(value);
        obs.setVoided(voided);
        return obs;
    }

    private Obs textObs(String conceptUuid, String conceptLabel, String value) {
        ConceptDatatype datatype = new ConceptDatatype();
        datatype.setHl7Abbreviation(ConceptDatatype.TEXT);
        Concept concept = new Concept();
        concept.setUuid(conceptUuid);
        concept.setDatatype(datatype);
        concept.addName(new ConceptName(conceptLabel, Locale.ENGLISH));
        Obs obs = new Obs();
        obs.setConcept(concept);
        obs.setValueText(value);
        return obs;
    }

    private List<String> labelsAndValues(LatestVitalsSummary summary) {
        return summary.getValues().stream()
                .map(value -> value.getLabel() + "=" + value.getValue()
                        + (value.getUnit().isEmpty() ? "" : " " + value.getUnit()))
                .collect(Collectors.toList());
    }
}
