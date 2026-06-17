package org.openmrs.module.rwandaemr.htmlformentry;

import org.junit.jupiter.api.Test;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Icd11DiagnosesSubmissionActionTest {

	@Test
	public void shouldParseSelectedDiagnosesJson() {
		List<Icd11DiagnosisSelection> selections = Icd11DiagnosesSubmissionAction.parseSelections(
				"[{\"icd11Code\":\"1A00\",\"entityUri\":\"cholera\",\"title\":\"Cholera\","
						+ "\"diagnosisType\":\"primary\",\"certainty\":\"confirmed\"}]");

		assertEquals(1, selections.size());
		assertEquals("1A00", selections.get(0).getIcd11Code());
		assertEquals("confirmed", selections.get(0).getCertainty());
	}

	@Test
	public void shouldRejectInvalidJson() {
		assertThrows(IllegalArgumentException.class,
				() -> Icd11DiagnosesSubmissionAction.parseSelections("not-json"));
	}
}
