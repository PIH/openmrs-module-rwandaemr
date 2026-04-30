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
 * Supports connections to and operations with the otp-verification endpoint for MMI flows.
 */
@Component("insuranceOtpVerificationProvider")
public class InsuranceOtpVerificationProvider {

	protected Log log = LogFactory.getLog(getClass());

	private final InsuranceIntegrationConfig config;

	public InsuranceOtpVerificationProvider(@Autowired InsuranceIntegrationConfig config) {
		this.config = config;
	}

	public IntegrationResponse verifyOtp(String insuranceType, String identifier, String otpCode, String fosaid) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(config.isOtpVerificationEnabled());
		if (ret.isEnabled()) {
			try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
				ObjectMapper mapper = new ObjectMapper();
				String url = config.getOtpVerifyUrl();
				HttpPost httpPost = new HttpPost(url);
				log.debug("POSTING " + url);
				httpPost.setHeader("Content-Type", "application/json");
				String apiKey = config.getOtpVerifyApiKey();
				if (StringUtils.isNotBlank(apiKey)) {
					httpPost.setHeader("x-api-key", apiKey);
				}
				String apiOrigin = config.getOtpVerifyApiOrigin();
				if (StringUtils.isNotBlank(apiOrigin)) {
					httpPost.setHeader("Origin", apiOrigin);
				}
				Map<String, Object> parameters = new HashMap<>();
				parameters.put("insuranceType", StringUtils.isBlank(insuranceType) ? "MMI" : insuranceType.trim());
				parameters.put("identifier", identifier);
				parameters.put("otpCode", otpCode);
				if (StringUtils.isNotBlank(fosaid)) {
					parameters.put("fosaid", fosaid.trim());
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
					}
					catch (Exception ignored) {
					}
					if (StringUtils.isNotBlank(data)) {
						try {
							ret.setResponseEntity(mapper.readValue(data, InsuranceOtpVerificationResponse.class));
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
		}

		return ret;
	}
}
