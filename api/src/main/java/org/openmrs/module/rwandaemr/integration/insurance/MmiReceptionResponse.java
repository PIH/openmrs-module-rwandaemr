package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

@Data
public class MmiReceptionResponse {
	private boolean success;
	private String message;
	private String insuranceType;
	private String facilityFosaId;
	private String patientIdentifier;
	private MmiReceptionData data;
	private String timestamp;
}
