package org.openmrs.module.rwandaemr.integration.insurance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MmiPatientType {
	private String typeId;
	private String typeName;
	private String description;
	@JsonProperty("isActive")
	private boolean active;
}
