/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsuranceOtpVerificationResponse {
	private boolean success;
	private String message;
	private String insuranceType;
	private String identifier;
	private String patientIdentifier;
	private String fosaid;
	private String facilityFosaId;
	private OtpVerificationData data;
	private String timestamp;
	private String status;
	private String error;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class OtpVerificationData {
		private Boolean verified;
		private String expiresAt;
		private String checkoutId;
		private String verificationType;
		private String patientNumber;
		private String verifiedAt;
		private String verifiedBy;
		private String verificationNotes;
		private String provider;
		private String referenceNumber;
	}
}
