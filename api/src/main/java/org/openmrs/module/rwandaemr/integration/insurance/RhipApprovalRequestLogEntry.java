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

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class RhipApprovalRequestLogEntry {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_APPROVED = "APPROVED";
	public static final String STATUS_REJECTED = "REJECTED";
	public static final String STATUS_PARTIALLY_APPROVED = "PARTIALLY_APPROVED";
	public static final String STATUS_FAILED = "FAILED";

	private Integer id;
	private Integer patientId;
	private Integer insurancePolicyId;
	private String insuranceType;
	private String insuranceName;
	private String insuranceCardNo;
	private String patientIdentifier;
	private String facilityFosaId;
	private String productCode;
	private String productName;
	private String productType;
	private BigDecimal requestedQuantity;
	private BigDecimal requestedUnitPrice;
	private String diagnosisIds;
	private String clinicalKnowledge;
	private String practitionerLicenseNumber;
	private String approvalCode;
	private String approvalStatus;
	private String message;
	private String requestPayload;
	private String responsePayload;
	private Integer responseCode;
	private Integer automaticStatusCheckCount;
	private Integer creator;
	private Date dateCreated;
	private Integer changedBy;
	private Date dateChanged;

	/**
	 * The MMI reception the approval belongs to. MMI keeps one approval per reception, shared by all its lines.
	 */
	private String visitReferenceNumber;

	/**
	 * MMI's decision for this line; can be lower than requested
	 */
	private BigDecimal approvedQuantity;
	private BigDecimal approvedUnitPrice;

	/**
	 * The approver's comment on this line
	 */
	private String decisionReason;

	/**
	 * Whether MMI has already put the approved line on a bill
	 */
	private Boolean billed;
	private String decidedBy;
	private String decidedAt;
	private Date lastStatusCheck;

	public boolean isMmi() {
		return "mmi".equalsIgnoreCase(insuranceType == null ? null : insuranceType.trim());
	}
}
