package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A normalized WHO ICD-11 postcoordination axis.
 */
@Getter
@Setter
public class Icd11PostcoordinationScale {

	private String axisName;

	private boolean required;

	private String allowMultipleValues;

	private List<String> scaleEntities;
}
