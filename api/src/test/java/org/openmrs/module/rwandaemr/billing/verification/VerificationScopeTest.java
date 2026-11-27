package org.openmrs.module.rwandaemr.billing.verification;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

public class VerificationScopeTest {

	@Test
	public void shouldVerifyOnlyMutuelleWhenBlank() {
		VerificationScope scope = new VerificationScope("  ");
		assertThat(scope.includes(5, "MUTUELLE"), is(true));
		assertThat(scope.includes(5, " mutuelle "), is(true));
		assertThat(scope.includes(6, "RSSB"), is(false));
		assertThat(scope.includes(7, null), is(false));
	}

	@Test
	public void shouldReadInsuranceIdsAndCategories() {
		VerificationScope scope = new VerificationScope("category:mutuelle, 12; 15 Category:RSSB nonsense");
		assertThat(scope.getInsuranceIds(), containsInAnyOrder(12, 15));
		assertThat(scope.getCategories(), containsInAnyOrder("MUTUELLE", "RSSB"));
		assertThat(scope.includes(12, "PRIVATE"), is(true));
		assertThat(scope.includes(13, "PRIVATE"), is(false));
		assertThat(scope.includes(13, "RSSB"), is(true));
	}

	@Test
	public void shouldVerifyOnlyTheListedIds() {
		VerificationScope scope = new VerificationScope("3");
		assertThat(scope.includes(3, "PRIVATE"), is(true));
		assertThat(scope.includes(4, "MUTUELLE"), is(false));
	}
}
