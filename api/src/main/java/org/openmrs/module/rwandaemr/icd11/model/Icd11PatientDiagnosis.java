package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Obs;

/**
 * An ICD-11 diagnosis selected for a patient encounter.
 */
@Getter
@Setter
public class Icd11PatientDiagnosis extends BaseOpenmrsData {

	private Integer id;

	private Patient patient;

	private Encounter encounter;

	private String icd11Code;

	private String entityUri;

	private String foundationUri;

	private String title;

	private String linearization;

	private String diagnosisType;

	private String certainty;

	private Obs obsGroup;
}
