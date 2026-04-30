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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsurancePatientReceptionResponse {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private boolean success;
	private String message;
	private List<ResponseError> errors;
	private String insuranceType;
	private String facilityFosaId;
	private String patientIdentifier;
	private ReceptionData data;
	private String timestamp;
	private String status;
	private String error;

	public String getFirstErrorMessage() {
		if (errors == null) {
			return null;
		}
		for (ResponseError responseError : errors) {
			if (responseError != null && StringUtils.isNotBlank(responseError.getMessage())) {
				return normalizeErrorMessage(responseError.getMessage());
			}
		}
		return null;
	}

	private String normalizeErrorMessage(String message) {
		String ret = StringUtils.trimToNull(message);
		if (ret == null) {
			return null;
		}
		if (ret.startsWith("[")) {
			try {
				List<String> messages = MAPPER.readValue(ret, new TypeReference<List<String>>() {});
				if (messages != null && !messages.isEmpty() && StringUtils.isNotBlank(messages.get(0))) {
					return messages.get(0).trim();
				}
			}
			catch (Exception ignored) {}
		}
		if (ret.startsWith("[")) {
			ret = ret.substring(1).trim();
		}
		if (ret.endsWith("]")) {
			ret = ret.substring(0, ret.length() - 1).trim();
		}
		ret = ret.replace("\\\"", "\"");
		if (ret.startsWith("\"")) {
			ret = ret.substring(1).trim();
		}
		if (ret.endsWith("\"")) {
			ret = ret.substring(0, ret.length() - 1).trim();
		}
		return StringUtils.trimToNull(ret);
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ResponseError {
		private String productId;
		private String message;
		private Boolean isValid;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ReceptionData {
		private String receptionNumber;
		private String facilityFosaId;
		private String bpCode;
		private String patientNumber;
		private Integer patientTypeNumber;
		private String receptionDate;
		private String status;
	}
}
