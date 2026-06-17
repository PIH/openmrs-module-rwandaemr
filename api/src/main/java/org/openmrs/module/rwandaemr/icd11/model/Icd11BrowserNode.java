package org.openmrs.module.rwandaemr.icd11.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A lazily loaded node in the locally installed ICD-11 code browser.
 */
@Getter
@Setter
public class Icd11BrowserNode {

	private String code;

	private String title;

	private boolean hasChildren;
}
