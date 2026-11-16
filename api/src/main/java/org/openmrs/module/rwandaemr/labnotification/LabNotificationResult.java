package org.openmrs.module.rwandaemr.labnotification;

import lombok.Data;

@Data
public class LabNotificationResult {

	private boolean success;
	private String status;
	private String message;
	private String providerMessageId;
	private String orderUuid;
	private String patientUuid;
	private String phoneNumber;
	private Integer responseCode;
	private String responseBody;
}
