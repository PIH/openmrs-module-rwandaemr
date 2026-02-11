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
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Supports connections to and operations with the insurance-eligibility endpoint in the HIE
 */
@Component("insuranceEligibilityProvider")
public class InsuranceEligibilityProvider {

	protected Log log = LogFactory.getLog(getClass());

	private final InsuranceIntegrationConfig config;

	public InsuranceEligibilityProvider(
			@Autowired InsuranceIntegrationConfig config
	) {
		this.config = config;
	}

	public IntegrationResponse checkEligibility(String type, String identifier, String fosaid) {
		return checkEligibility(type, identifier, fosaid, false);
	}

	public IntegrationResponse checkEligibility(String type, String identifier, String fosaid, boolean sendOtp) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", type);
		parameters.put("identifier", identifier);
		parameters.put("fosaid", fosaid);
		parameters.put("sendOTP", sendOtp);
		Class<?> responseType = sendOtp ? Object.class : InsuranceEligibilityResponse.class;
		return postRequest(config.getEligibilityCheckUrl(), parameters, responseType);
	}

	public IntegrationResponse verifyOtp(String type, String identifier, String otpCode, String fosaid) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", type);
		parameters.put("identifier", identifier);
		parameters.put("otpCode", otpCode);
		parameters.put("fosaid", fosaid);
		return postRequest(config.getEligibilityOtpVerifyUrl(), parameters, MmiOtpVerificationResponse.class);
	}

	public IntegrationResponse getPatientTypes(String insuranceType, String facilityFosaId) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", insuranceType);
		parameters.put("facility_fosa_id", facilityFosaId);
		return postRequest(config.getMmiPatientTypesUrl(), parameters, MmiPatientTypesResponse.class);
	}

	public IntegrationResponse createReception(String insuranceType, String patientIdentifier, String facilityFosaId,
											   String patientType, String otpCode, boolean prescriptionRequired) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", insuranceType);
		parameters.put("patientIdentifier", patientIdentifier);
		parameters.put("facilityFosaId", facilityFosaId);
		parameters.put("patientType", patientType);
		if (StringUtils.isNotBlank(otpCode)) {
			parameters.put("otpCode", otpCode);
		}
		parameters.put("prescriptionRequired", prescriptionRequired);
		return postRequest(config.getMmiReceptionUrl(), parameters, MmiReceptionResponse.class);
	}

	private IntegrationResponse postRequest(String url, Map<String, Object> parameters, Class<?> responseType) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(StringUtils.isNotBlank(url));
		if (!ret.isEnabled()) {
			return ret;
		}

		try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
			ObjectMapper mapper = new ObjectMapper();
			HttpPost httpPost = new HttpPost(url);
			log.debug("POSTING " + url);
			httpPost.setHeader("Content-Type", "application/json");
			String apiKey = config.getEligibilityCheckApiKey();
			if (StringUtils.isNotBlank(apiKey)) {
				httpPost.setHeader("x-api-key", apiKey);
			}
			String apiOrigin = config.getEligibilityCheckApiOrigin();
			if (StringUtils.isNotBlank(apiOrigin)) {
				httpPost.setHeader("Origin", apiOrigin);
			}
			httpPost.setEntity(new StringEntity(mapper.writeValueAsString(parameters)));
			ret.setEndpointAccessible(false);
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				ret.setEndpointAccessible(true);
				ret.setResponseCode(response.getStatusLine().getStatusCode());
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}

				// Process into an appropriate entity
				if (StringUtils.isNotBlank(data)) {
					try {
						ret.setResponseEntity(mapper.readValue(data, responseType));
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

		return ret;
	}
}
