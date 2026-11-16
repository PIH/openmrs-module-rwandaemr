package org.openmrs.module.rwandaemr.integration.insurance;

import lombok.Data;

import java.util.List;

@Data
public class MmiPatientTypesResponse {
	private boolean success;
	private String message;
	private String insuranceType;
	private String facilityFosaId;
	private List<MmiPatientType> patientTypes;
}
