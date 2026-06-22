package org.openmrs.module.rwandaemr.labnotification;

import lombok.Data;

import java.util.Date;

@Data
public class LabNotificationLogEntry {

	private Integer patientId;
	private Integer orderId;
	private String orderUuid;
	private String phoneNumber;
	private String messageContent;
	private String providerMessageId;
	private String requestPayload;
	private Integer responseCode;
	private String responseBody;
	private String status;
	private String errorMessage;
	private String dlrStatus;
	private String dlrLevel;
	private Integer creator;
	private Date dateCreated;
	private Date dateChanged;
}
