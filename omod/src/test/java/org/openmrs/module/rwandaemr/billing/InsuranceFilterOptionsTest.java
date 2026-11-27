package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.Test;
import org.openmrs.module.mohbilling.model.Insurance;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class InsuranceFilterOptionsTest {

	private static Insurance insurance(int id, String name, String category) {
		Insurance ret = new Insurance();
		ret.setInsuranceId(id);
		ret.setName(name);
		ret.setCategory(category);
		return ret;
	}

	private final List<InsuranceFilterOptions.Option> options = InsuranceFilterOptions.build(Arrays.asList(
			insurance(1, "RSSB", "RSSB"),
			insurance(2, "Mutuelle Butaro", "MUTUELLE"),
			insurance(3, "MMI", "MMI"),
			insurance(4, "Mutuelle Burera", "mutuelle"),
			insurance(5, "AAR", "PRIVATE")));

	@Test
	public void shouldShowEveryMutuelleInsuranceAsOneChoiceFirst() {
		assertThat(options, hasSize(4));
		InsuranceFilterOptions.Option mutuelle = options.get(0);
		assertThat(mutuelle.getValue(), equalTo("category:MUTUELLE"));
		assertThat(mutuelle.getLabel(), equalTo("MUTUELLE"));
		assertThat(mutuelle.getInsuranceIds(), contains(2, 4));
		assertThat(mutuelle.isCategory(), is(true));
		assertThat(mutuelle.getInsuranceId(), nullValue());
	}

	@Test
	public void shouldKeepOtherCategoriesAsOneChoicePerInsurance() {
		assertThat(options.get(1).getLabel(), equalTo("AAR"));
		assertThat(options.get(2).getLabel(), equalTo("MMI"));
		assertThat(options.get(3).getLabel(), equalTo("RSSB"));
		assertThat(options.get(3).getInsuranceId(), equalTo(1));
	}

	@Test
	public void shouldFindAChoiceByItsValueOrAMutuelleInsuranceId() {
		assertThat(InsuranceFilterOptions.find(options, "3").getLabel(), equalTo("MMI"));
		assertThat(InsuranceFilterOptions.find(options, "category:mutuelle").getLabel(), equalTo("MUTUELLE"));
		assertThat(InsuranceFilterOptions.find(options, "4").getLabel(), equalTo("MUTUELLE"));
		assertThat(InsuranceFilterOptions.find(options, "99"), nullValue());
		assertThat(InsuranceFilterOptions.find(options, ""), nullValue());
	}

	@Test
	public void shouldNotOfferMutuelleWhenNoInsuranceIsOfThatCategory() {
		List<InsuranceFilterOptions.Option> withoutMutuelle = InsuranceFilterOptions.build(Arrays.asList(insurance(1, "RSSB", "RSSB")));
		assertThat(withoutMutuelle, hasSize(1));
		assertThat(withoutMutuelle.get(0).isCategory(), is(false));
	}
}
