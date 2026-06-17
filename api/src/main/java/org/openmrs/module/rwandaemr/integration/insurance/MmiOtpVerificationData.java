package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

@Data
public class MmiOtpVerificationData {
	private String patientNumber;
	private String verifiedAt;
	private String verifiedBy;
	private String verificationNotes;
	private String provider;
	private String checkoutId;
	private boolean verified;
	private String verificationType;
	private String referenceNumber;
}
