package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A diagnosis selected in the HTML Form Entry ICD-11 widget.
 */
@Getter
@Setter
public class Icd11DiagnosisSelection {

	private String icd11Code;

	private String entityUri;

	private String foundationUri;

	private String title;

	private String linearization;

	private String diagnosisType;

	private String certainty;

	private String conceptUuid;
}
