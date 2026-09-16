package org.openmrs.module.rwandaemr;

/**
 * The date a Lab ID sequence value was scoped to (yyyyMMdd), paired with the sequence value
 * itself. Returned as a single unit so the date and the sequence always agree on which day
 * they belong to - deriving the date separately from the sequence lookup would let the two
 * disagree if a caller's clock read straddled midnight relative to the service's.
 */
public class LabIdSequenceValue {

    private final String dateString;

    private final int sequenceValue;

    public LabIdSequenceValue(String dateString, int sequenceValue) {
        this.dateString = dateString;
        this.sequenceValue = sequenceValue;
    }

    public String getDateString() {
        return dateString;
    }

    public int getSequenceValue() {
        return sequenceValue;
    }
}
