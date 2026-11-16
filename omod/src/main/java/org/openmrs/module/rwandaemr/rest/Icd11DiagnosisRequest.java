package org.openmrs.module.rwandaemr.rest;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON request body for recording an ICD-11 diagnosis.
 */
@Getter
@Setter
public class Icd11DiagnosisRequest {

	private String patient;

	private String encounter;

	private String icd11Code;

	private String entityUri;

	private String foundationUri;

	private String title;

	private String linearization;

	private String diagnosisType;

	private String certainty;
}
