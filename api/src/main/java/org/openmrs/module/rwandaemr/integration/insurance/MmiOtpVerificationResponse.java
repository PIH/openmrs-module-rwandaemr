package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

@Data
public class MmiOtpVerificationResponse {
	private boolean success;
	private String message;
	private String insuranceType;
	private String patientIdentifier;
	private String facilityFosaId;
	private MmiOtpVerificationData data;
	private String timestamp;
}
