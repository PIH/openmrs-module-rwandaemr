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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.model.RhipIntegrationLog;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Supports connections to and operations with the patient-reception endpoint for MMI flows.
 */
@Component("insurancePatientReceptionProvider")
public class InsurancePatientReceptionProvider {

	protected Log log = LogFactory.getLog(getClass());

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final InsuranceIntegrationConfig config;

	public InsurancePatientReceptionProvider(@Autowired InsuranceIntegrationConfig config) {
		this.config = config;
	}

	public IntegrationResponse createPatientReception(String insuranceType, String patientIdentifier, String facilityFosaId,
	                                                  String patientType, String otpCode, Boolean prescriptionRequired) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(config.isPatientReceptionEnabled());
		String url = config.getPatientReceptionUrl();
		Map<String, Object> parameters = buildPatientReceptionPayload(insuranceType, patientIdentifier, facilityFosaId,
				patientType, otpCode, prescriptionRequired);
		String requestPayload = toJson(parameters);
		if (!ret.isEnabled()) {
			ret.setErrorMessage("Patient reception integration is not enabled. Configure " +
					InsuranceIntegrationConfig.PATIENT_RECEPTION_URL + " global property.");
		}
		if (ret.isEnabled()) {
			try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
				HttpPost httpPost = new HttpPost(url);
				log.debug("POSTING " + url);
				log.info("MMI patient reception request payload: " + requestPayload);
				httpPost.setHeader("Content-Type", "application/json");
				String apiKey = config.getPatientReceptionApiKey();
				if (StringUtils.isNotBlank(apiKey)) {
					httpPost.setHeader("x-api-key", apiKey);
				}
				String apiOrigin = config.getPatientReceptionApiOrigin();
				if (StringUtils.isNotBlank(apiOrigin)) {
					httpPost.setHeader("Origin", apiOrigin);
				}
				httpPost.setEntity(new StringEntity(requestPayload == null ? "" : requestPayload));
				ret.setEndpointAccessible(false);
				try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
					ret.setEndpointAccessible(true);
					ret.setResponseCode(response.getStatusLine().getStatusCode());
					HttpEntity entity = response.getEntity();
					String data = "";
					try {
						data = EntityUtils.toString(entity);
					}
					catch (Exception ignored) {
					}
					if (StringUtils.isNotBlank(data)) {
						try {
							ret.setResponseEntity(OBJECT_MAPPER.readValue(data, InsurancePatientReceptionResponse.class));
						}
						catch (Exception e) {
							ret.setErrorMessage(e.getMessage());
						}
					}
				}
			}
			catch (Exception e) {
				ret.setErrorMessage(e.getMessage());
			}
			finally {
				persistRhipIntegrationLog(url, "MMI_PATIENT_RECEPTION", requestPayload, ret);
			}
		} else {
			persistRhipIntegrationLog(url, "MMI_PATIENT_RECEPTION", requestPayload, ret);
		}
		return ret;
	}

	private Map<String, Object> buildPatientReceptionPayload(String insuranceType, String patientIdentifier,
	                                                         String facilityFosaId, String patientType, String otpCode,
	                                                         Boolean prescriptionRequired) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", StringUtils.isBlank(insuranceType) ? "MMI" : insuranceType.trim());
		parameters.put("patientIdentifier", patientIdentifier);
		parameters.put("facilityFosaId", facilityFosaId);
		parameters.put("patientType", patientType);
		parameters.put("prescriptionRequired", prescriptionRequired == null ? Boolean.TRUE : prescriptionRequired);
		if (StringUtils.isNotBlank(otpCode)) {
			parameters.put("otpCode", otpCode.trim());
		}
		return parameters;
	}

	private String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			log.warn("Unable to serialize MMI patient reception payload", e);
			return null;
		}
	}

	private void persistRhipIntegrationLog(String url, String operationType, String requestPayload,
	                                       IntegrationResponse response) {
		try {
			BillingService billingService = Context.getService(BillingService.class);
			if (billingService == null) {
				return;
			}
			User currentUser = Context.getAuthenticatedUser();
			RhipIntegrationLog logEntry = new RhipIntegrationLog();
			logEntry.setDateCreated(new Date());
			logEntry.setCreator(currentUser);
			logEntry.setSenderUsername(currentUser == null ? null : currentUser.getUsername());
			logEntry.setOperationType(operationType);
			logEntry.setEndpointUrl(url);
			logEntry.setRequestPayload(requestPayload);
			logEntry.setResponseCode(response == null ? null : response.getResponseCode());
			logEntry.setResponseStatus(resolveResponseStatus(response));
			logEntry.setResponseBody(toJson(response == null ? null : response.getResponseEntity()));
			logEntry.setErrorMessage(response == null ? "No response" : response.getErrorMessage());
			logEntry.setUuid(UUID.randomUUID().toString());
			billingService.saveRhipIntegrationLog(logEntry);
		}
		catch (Exception e) {
			log.warn("Unable to persist MMI patient reception in RHIP integration logs", e);
		}
	}

	private String resolveResponseStatus(IntegrationResponse response) {
		if (response == null) {
			return "NO_RESPONSE";
		}
		if (StringUtils.isNotBlank(response.getErrorMessage())) {
			return "ERROR";
		}
		Integer code = response.getResponseCode();
		if (code == null) {
			return "UNKNOWN";
		}
		return code >= 200 && code < 300 ? "SUCCESS" : "HTTP_" + code;
	}
}
