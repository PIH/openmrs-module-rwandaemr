package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MmiPatientTypesResponse {
	private boolean success;
	private String message;
	private String insuranceType;
	private String facilityFosaId;
	private List<MmiPatientType> patientTypes;
}
