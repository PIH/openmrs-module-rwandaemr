package org.openmrs.module.rwandaemr.htmlformentry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;

public class PathologyReportSubmissionActionTest {

	private Concept grossDescription;
	private Concept blocks;
	private Concept slides;
	private Concept diagnosis;
	private PathologyReportSubmissionAction action;

	@BeforeEach
	public void setUp() {
		grossDescription = new Concept(1);
		blocks = new Concept(2);
		slides = new Concept(3);
		diagnosis = new Concept(4);
		Map<PathologyStatus, Set<Concept>> resultsOfSteps = new EnumMap<PathologyStatus, Set<Concept>>(PathologyStatus.class);
		resultsOfSteps.put(PathologyStatus.GROSSED, Collections.singleton(grossDescription));
		resultsOfSteps.put(PathologyStatus.PROCESSED, new HashSet<Concept>(Arrays.asList(blocks, slides)));
		resultsOfSteps.put(PathologyStatus.SIGNED_OUT, Collections.singleton(diagnosis));
		action = new PathologyReportSubmissionAction(10, resultsOfSteps);
	}

	@Test
	public void shouldFindTheStepsTheReportHasResultsFor() {
		Obs gross = textObs(grossDescription, "Two cores of tan tissue");
		Obs blockCount = new Obs();
		blockCount.setConcept(blocks);
		blockCount.setValueNumeric(2.0);

		assertThat(action.getStepsWithResults(Arrays.asList(gross, blockCount)),
				containsInAnyOrder(PathologyStatus.GROSSED, PathologyStatus.PROCESSED));
	}

	@Test
	public void shouldIgnoreBlankVoidedAndOtherObservations() {
		Obs blank = textObs(grossDescription, "  ");
		Obs voided = textObs(diagnosis, "Adenocarcinoma");
		voided.setVoided(true);
		Obs other = textObs(new Concept(5), "Clinical history");

		assertThat(action.getStepsWithResults(Arrays.asList(blank, voided, other)), empty());
	}

	private Obs textObs(Concept concept, String value) {
		Obs obs = new Obs();
		obs.setConcept(concept);
		obs.setValueText(value);
		return obs;
	}
}
