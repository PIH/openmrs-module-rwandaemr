package org.openmrs.module.rwandaemr.page.controller.queue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Obs;

public class LatestVitalsSummary {

    static final String VITALS_ENCOUNTER_TYPE_UUID = "b9b9d19e-3774-4951-ab68-ddd6e817d6dd";

    private static final String HEIGHT_UUID = "3ce93cf2-26fe-102b-80cb-0017a47871b2";
    private static final String WEIGHT_UUID = "3ce93b62-26fe-102b-80cb-0017a47871b2";
    private static final String TEMPERATURE_UUID = "3ce939d2-26fe-102b-80cb-0017a47871b2";
    private static final String PULSE_UUID = "3ce93824-26fe-102b-80cb-0017a47871b2";
    private static final String RESPIRATORY_RATE_UUID = "3ceb11f8-26fe-102b-80cb-0017a47871b2";
    private static final String SYSTOLIC_UUID = "3ce934fa-26fe-102b-80cb-0017a47871b2";
    private static final String DIASTOLIC_UUID = "3ce93694-26fe-102b-80cb-0017a47871b2";
    private static final String OXYGEN_SATURATION_UUID = "3ce9401c-26fe-102b-80cb-0017a47871b2";

    private static final Set<String> STANDARD_VITAL_UUIDS = new HashSet<String>();

    static {
        Collections.addAll(STANDARD_VITAL_UUIDS, HEIGHT_UUID, WEIGHT_UUID, TEMPERATURE_UUID, PULSE_UUID,
                RESPIRATORY_RATE_UUID, SYSTOLIC_UUID, DIASTOLIC_UUID, OXYGEN_SATURATION_UUID);
    }

    private final Date recordedAt;
    private final List<VitalValue> values;

    private LatestVitalsSummary(Date recordedAt, List<VitalValue> values) {
        this.recordedAt = recordedAt;
        this.values = values;
    }

    public Date getRecordedAt() {
        return recordedAt;
    }

    public List<VitalValue> getValues() {
        return values;
    }

    public static LatestVitalsSummary from(List<Encounter> encounters, Locale locale) {
        Encounter latestEncounter = findLatestVitalsEncounter(encounters);
        if (latestEncounter == null) {
            return null;
        }

        List<Obs> observations = new ArrayList<Obs>(latestEncounter.getAllObs());
        List<VitalValue> values = new ArrayList<VitalValue>();
        Double height = numericValue(observations, HEIGHT_UUID);
        Double weight = numericValue(observations, WEIGHT_UUID);

        addNumericValue(values, "Height", height, "cm");
        addNumericValue(values, "Weight", weight, "kg");
        if (height != null && height > 0 && weight != null && weight > 0) {
            values.add(new VitalValue("BMI", formatNumber(weight / Math.pow(height / 100, 2), 1), "kg/m2"));
        }
        addNumericValue(values, "Temperature", numericValue(observations, TEMPERATURE_UUID), "\u00b0C");
        addNumericValue(values, "Pulse", numericValue(observations, PULSE_UUID), "bpm");
        addNumericValue(values, "Respiratory rate", numericValue(observations, RESPIRATORY_RATE_UUID), "/min");
        addBloodPressure(values, numericValue(observations, SYSTOLIC_UUID),
                numericValue(observations, DIASTOLIC_UUID));
        addNumericValue(values, "Oxygen saturation", numericValue(observations, OXYGEN_SATURATION_UUID), "%");
        addRemainingValues(values, observations, locale == null ? Locale.ENGLISH : locale);

        if (values.isEmpty()) {
            return null;
        }
        return new LatestVitalsSummary(latestEncounter.getEncounterDatetime(),
                Collections.unmodifiableList(values));
    }

    private static Encounter findLatestVitalsEncounter(List<Encounter> encounters) {
        Encounter latest = null;
        if (encounters == null) {
            return null;
        }
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.isVoided() || encounter.getEncounterType() == null
                    || !VITALS_ENCOUNTER_TYPE_UUID.equals(encounter.getEncounterType().getUuid())) {
                continue;
            }
            if (latest == null || compareEncounters(encounter, latest) > 0) {
                latest = encounter;
            }
        }
        return latest;
    }

    private static int compareEncounters(Encounter first, Encounter second) {
        int dateComparison = compareDates(first.getEncounterDatetime(), second.getEncounterDatetime());
        if (dateComparison != 0) {
            return dateComparison;
        }
        dateComparison = compareDates(first.getDateCreated(), second.getDateCreated());
        if (dateComparison != 0) {
            return dateComparison;
        }
        return compareIntegers(first.getEncounterId(), second.getEncounterId());
    }

    private static int compareDates(Date first, Date second) {
        if (first == null) {
            return second == null ? 0 : -1;
        }
        return second == null ? 1 : first.compareTo(second);
    }

    private static int compareIntegers(Integer first, Integer second) {
        if (first == null) {
            return second == null ? 0 : -1;
        }
        return second == null ? 1 : first.compareTo(second);
    }

    private static Double numericValue(List<Obs> observations, String conceptUuid) {
        Obs matchingObs = null;
        for (Obs obs : observations) {
            if (obs == null || obs.isVoided() || obs.getConcept() == null
                    || !conceptUuid.equals(obs.getConcept().getUuid()) || obs.getValueNumeric() == null) {
                continue;
            }
            if (matchingObs == null || compareDates(obs.getObsDatetime(), matchingObs.getObsDatetime()) > 0) {
                matchingObs = obs;
            }
        }
        return matchingObs == null ? null : matchingObs.getValueNumeric();
    }

    private static void addNumericValue(List<VitalValue> values, String label, Double value, String unit) {
        if (value != null) {
            values.add(new VitalValue(label, formatNumber(value), unit));
        }
    }

    private static void addBloodPressure(List<VitalValue> values, Double systolic, Double diastolic) {
        if (systolic == null && diastolic == null) {
            return;
        }
        String value = (systolic == null ? "-" : formatNumber(systolic)) + "/"
                + (diastolic == null ? "-" : formatNumber(diastolic));
        values.add(new VitalValue("Blood pressure", value, "mmHg"));
    }

    private static void addRemainingValues(List<VitalValue> values, List<Obs> observations, Locale locale) {
        List<VitalValue> remainingValues = new ArrayList<VitalValue>();
        for (Obs obs : observations) {
            if (obs == null || obs.isVoided() || obs.getConcept() == null
                    || STANDARD_VITAL_UUIDS.contains(obs.getConcept().getUuid())) {
                continue;
            }
            String value = obs.getValueAsString(locale);
            ConceptName conceptName = obs.getConcept().getName(locale);
            if (StringUtils.isNotBlank(value) && conceptName != null && StringUtils.isNotBlank(conceptName.getName())) {
                remainingValues.add(new VitalValue(conceptName.getName(), value, ""));
            }
        }
        Collections.sort(remainingValues, new Comparator<VitalValue>() {
            @Override
            public int compare(VitalValue first, VitalValue second) {
                return first.getLabel().compareToIgnoreCase(second.getLabel());
            }
        });
        values.addAll(remainingValues);
    }

    private static String formatNumber(Double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String formatNumber(Double value, int decimalPlaces) {
        return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static class VitalValue {

        private final String label;
        private final String value;
        private final String unit;

        private VitalValue(String label, String value, String unit) {
            this.label = label;
            this.value = value;
            this.unit = unit;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }
    }
}
