package org.openmrs.module.rwandaemr.pathology;

import org.junit.jupiter.api.Test;
import org.openmrs.api.APIException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PathologyAccessionNumberGeneratorTest {

	@Test
	public void shouldFormatTheYearAndAFourDigitSequence() {
		assertThat(PathologyAccessionNumberGenerator.format("2026", 1), equalTo("PAT2026-0001"));
		assertThat(PathologyAccessionNumberGenerator.format("2026", 123), equalTo("PAT2026-0123"));
		assertThat(PathologyAccessionNumberGenerator.format("2026", 12345), equalTo("PAT2026-12345"));
	}

	@Test
	public void shouldContinueTheSequenceWithinTheYear() {
		assertThat(PathologyAccessionNumberGenerator.getNextValue("2026:42", "2026"), equalTo(42));
	}

	@Test
	public void shouldStartTheSequenceAgainInANewYear() {
		assertThat(PathologyAccessionNumberGenerator.getNextValue("2026:42", "2027"), equalTo(1));
	}

	@Test
	public void shouldStartTheSequenceWhenNoneIsStored() {
		assertThat(PathologyAccessionNumberGenerator.getNextValue(null, "2026"), equalTo(1));
		assertThat(PathologyAccessionNumberGenerator.getNextValue("", "2026"), equalTo(1));
	}

	@Test
	public void shouldFailOnAnInvalidStoredValue() {
		assertThrows(APIException.class, () -> PathologyAccessionNumberGenerator.getNextValue("2026:abc", "2026"));
	}
}
