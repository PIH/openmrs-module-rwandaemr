package org.openmrs.module.rwandaemr.labnotification;

import lombok.Data;

@Data
public class IntouchSmsResponse {

	private boolean success;
	private Integer responseCode;
	private String responseBody;
	private String providerMessageId;
	private String errorMessage;
	private String requestPayload;
}
