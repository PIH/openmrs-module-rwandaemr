package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

@Data
public class MmiPatientType {
	private String typeId;
	private String typeName;
	private String description;
	private boolean isActive;
}
