package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MmiReceptionData {
	/** RHIP returns the MMI reception number as visitReferenceNumber */
	@JsonAlias("visitReferenceNumber")
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
