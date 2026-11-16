package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;

/**
 * An optional mapping from an ICD-11 entity to an existing OpenMRS concept.
 */
@Getter
@Setter
public class Icd11ConceptMap extends BaseOpenmrsData {

	private Integer id;

	private String icd11Code;

	private String entityUri;

	private String linearization;

	private Concept concept;

	private String mapType;
}
