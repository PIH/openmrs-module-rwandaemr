package org.openmrs.module.rwandaemr.billing.verification;

import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The insurances whose bills are verified, from the global property rwandaemr.billVerification.insurances: a list of
 * insurance ids and insurance categories, such as "category:MUTUELLE, 12, 15", separated by commas, semicolons or
 * spaces.  The bills of the other insurances are not verified.
 */
public class VerificationScope {

	public static final String CATEGORY_PREFIX = "category:";

	/** Used when the global property is blank */
	public static final String DEFAULT = CATEGORY_PREFIX + "MUTUELLE";

	private final Set<Integer> insuranceIds = new HashSet<Integer>();
	private final Set<String> categories = new HashSet<String>();

	public VerificationScope(String setting) {
		String value = StringUtils.isBlank(setting) ? DEFAULT : setting;
		for (String token : value.split("[,;\\s]+")) {
			String entry = token.trim();
			if (entry.isEmpty()) {
				continue;
			}
			if (entry.toLowerCase(Locale.ROOT).startsWith(CATEGORY_PREFIX)) {
				String category = normalize(entry.substring(CATEGORY_PREFIX.length()));
				if (!category.isEmpty()) {
					categories.add(category);
				}
			}
			else {
				try {
					insuranceIds.add(Integer.valueOf(entry));
				}
				catch (NumberFormatException e) {
					// Not an insurance id: ignored
				}
			}
		}
	}

	/**
	 * @return true if the bills of the insurance with the given id and category are verified
	 */
	public boolean includes(Integer insuranceId, String category) {
		return (insuranceId != null && insuranceIds.contains(insuranceId)) || categories.contains(normalize(category));
	}

	public Set<Integer> getInsuranceIds() {
		return Collections.unmodifiableSet(insuranceIds);
	}

	public Set<String> getCategories() {
		return Collections.unmodifiableSet(categories);
	}

	private static String normalize(String category) {
		return StringUtils.trimToEmpty(category).toUpperCase(Locale.ROOT);
	}
}
