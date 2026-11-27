package org.openmrs.module.rwandaemr.billing;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.mohbilling.model.Insurance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The insurances to choose from on the RHIP pages.  As in the bill report, every insurance of the MUTUELLE category
 * is one choice, MUTUELLE; every other insurance is a choice of its own.
 */
public class InsuranceFilterOptions {

	/** The category whose insurances are shown together */
	public static final String MUTUELLE = "MUTUELLE";

	/** The value of a choice that is a whole category, as the bill report names it */
	public static final String CATEGORY_PREFIX = "category:";

	/**
	 * One choice: an insurance, or every insurance of a category
	 */
	public static class Option {
		private final String value;
		private final String label;
		private final List<Integer> insuranceIds;

		Option(String value, String label, List<Integer> insuranceIds) {
			this.value = value;
			this.label = label;
			this.insuranceIds = insuranceIds;
		}

		/**
		 * @return the value of the choice: the insurance id, or category:MUTUELLE
		 */
		public String getValue() {
			return value;
		}

		public String getLabel() {
			return label;
		}

		/**
		 * @return the insurances of the choice
		 */
		public List<Integer> getInsuranceIds() {
			return insuranceIds;
		}

		/**
		 * @return true if the choice is every insurance of a category
		 */
		public boolean isCategory() {
			return value.startsWith(CATEGORY_PREFIX);
		}

		/**
		 * @return the single insurance of the choice, or null for a category
		 */
		public Integer getInsuranceId() {
			return isCategory() || insuranceIds.isEmpty() ? null : insuranceIds.get(0);
		}
	}

	/**
	 * @return true if the insurance is of the MUTUELLE category
	 */
	public static boolean isMutuelle(Insurance insurance) {
		return insurance != null && isMutuelle(insurance.getCategory());
	}

	/**
	 * @return true if the category is MUTUELLE
	 */
	public static boolean isMutuelle(String category) {
		return MUTUELLE.equalsIgnoreCase(StringUtils.trimToEmpty(category));
	}

	/**
	 * @param insurances the insurances the user can see
	 * @return MUTUELLE first if any of the insurances is of that category, then every other insurance by name
	 */
	public static List<Option> build(Collection<Insurance> insurances) {
		List<Integer> mutuelleIds = new ArrayList<Integer>();
		List<Insurance> others = new ArrayList<Insurance>();
		for (Insurance insurance : insurances) {
			if (insurance == null || insurance.getInsuranceId() == null) {
				continue;
			}
			if (isMutuelle(insurance)) {
				mutuelleIds.add(insurance.getInsuranceId());
			}
			else {
				others.add(insurance);
			}
		}
		Collections.sort(others, new Comparator<Insurance>() {
			@Override
			public int compare(Insurance left, Insurance right) {
				return String.valueOf(left.getName()).compareToIgnoreCase(String.valueOf(right.getName()));
			}
		});
		List<Option> ret = new ArrayList<Option>();
		if (!mutuelleIds.isEmpty()) {
			Collections.sort(mutuelleIds);
			ret.add(new Option(CATEGORY_PREFIX + MUTUELLE, MUTUELLE, Collections.unmodifiableList(mutuelleIds)));
		}
		for (Insurance insurance : others) {
			ret.add(new Option(insurance.getInsuranceId().toString(),
					StringUtils.defaultIfBlank(insurance.getName(), "Insurance #" + insurance.getInsuranceId()),
					Collections.singletonList(insurance.getInsuranceId())));
		}
		return ret;
	}

	/**
	 * @return the choice with the given value, or null.  An insurance of the MUTUELLE category chosen by its id is the
	 * MUTUELLE choice.
	 */
	public static Option find(List<Option> options, String value) {
		String wanted = StringUtils.trimToNull(value);
		if (wanted == null) {
			return null;
		}
		for (Option option : options) {
			if (option.getValue().equalsIgnoreCase(wanted)) {
				return option;
			}
		}
		for (Option option : options) {
			if (option.isCategory() && option.getInsuranceIds().contains(toInteger(wanted))) {
				return option;
			}
		}
		return null;
	}

	/**
	 * @return the value of the choice that holds the given insurance
	 */
	public static String valueOf(List<Option> options, Integer insuranceId) {
		for (Option option : options) {
			if (option.getInsuranceIds().contains(insuranceId)) {
				return option.getValue();
			}
		}
		return insuranceId == null ? "" : insuranceId.toString();
	}

	private static Integer toInteger(String value) {
		try {
			return Integer.valueOf(value.toUpperCase(Locale.ROOT).trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}
}
