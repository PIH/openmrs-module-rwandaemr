package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

@Data
public class MmiReceptionData {
	private String receptionNumber;
	private String facilityFosaId;
	private String bpCode;
	private String patientNumber;
	private Integer patientTypeNumber;
	private String receptionDate;
	private String status;
	private String practitionerLicenseNumber;
	private String visitType;
}
