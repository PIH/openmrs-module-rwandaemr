package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A normalized WHO ICD-11 term used for inclusions, exclusions, and index terms.
 */
@Getter
@Setter
public class Icd11Term {

	private String label;

	private String foundationReference;

	private String linearizationReference;
}
