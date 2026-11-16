package org.openmrs.module.rwandaemr.htmlformentry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.icd11.Icd11Service;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;

import java.util.Collections;
import java.util.List;

/**
 * Persists ICD-11 selections after HTML Form Entry has saved the encounter.
 */
public class Icd11DiagnosesSubmissionAction implements CustomFormSubmissionAction {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final List<Icd11DiagnosisSelection> selections;

	public Icd11DiagnosesSubmissionAction(String diagnosesJson) {
		this.selections = parseSelections(diagnosesJson);
	}

	@Override
	public void applyAction(FormEntrySession session) {
		Encounter encounter = session.getEncounter();
		if (encounter == null) {
			throw new IllegalStateException("Unable to save ICD-11 diagnoses without an encounter");
		}
		Context.getService(Icd11Service.class).replaceDiagnoses(encounter.getPatient(), encounter, selections);
	}

	public static List<Icd11DiagnosisSelection> parseSelections(String diagnosesJson) {
		if (diagnosesJson == null || diagnosesJson.trim().isEmpty()) {
			return Collections.emptyList();
		}
		try {
			return OBJECT_MAPPER.readValue(diagnosesJson, new TypeReference<List<Icd11DiagnosisSelection>>() { });
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Invalid ICD-11 diagnosis selection JSON", e);
		}
	}
}
