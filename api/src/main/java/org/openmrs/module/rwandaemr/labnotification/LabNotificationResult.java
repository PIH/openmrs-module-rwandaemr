package org.openmrs.module.rwandaemr.labnotification;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class LabNotificationResult {

	private boolean success;
	private String status;
	private String message;
	private String providerMessageId;
	/** The first notified order; kept for callers that notify a single order */
	private String orderUuid;
	private String patientUuid;
	private String phoneNumber;
	private Integer responseCode;
	private String responseBody;
	/** Orders that were included in the SMS */
	private List<String> notifiedOrderUuids = new ArrayList<>();
	/** Orders that were left out of the SMS, with the reason */
	private Map<String, String> skippedOrders = new LinkedHashMap<>();
}
