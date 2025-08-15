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

	public IntegrationResponse checkEligibility(String eligibilityCheckType, String identifier) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(config.isEligibilityCheckEnabled());
		if (ret.isEnabled()) {
			try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
				ObjectMapper mapper = new ObjectMapper();
				HttpPost httpPost = new HttpPost(config.getEligibilityCheckUrl());
				Map<String, String> postBody = new HashMap<>();
				postBody.put("eligibility_check_type", eligibilityCheckType);
				postBody.put("documentNumber", identifier);
				postBody.put("identifier", identifier);
				log.debug("POSTING " + config.getEligibilityCheckUrl() + ": " + postBody);
				httpPost.setEntity(new StringEntity(mapper.writeValueAsString(postBody)));
				httpPost.setHeader("Content-Type", "application/json");
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
							if (ret.getResponseCode() == 200) {
								if ("cbhi".equalsIgnoreCase(eligibilityCheckType)) {
									CbhiEligibilityResponse cbhiResponse = mapper.readValue(data, CbhiEligibilityResponse.class);
									ret.setResponseEntity(cbhiResponse);
								} else if ("rama".equalsIgnoreCase(eligibilityCheckType)) {
									RamaEligibilityResponse ramaResponse = mapper.readValue(data, RamaEligibilityResponse.class);
									ret.setResponseEntity(ramaResponse);
								}
							}
							else {
								ret.setResponseEntity(mapper.readValue(data, InsuranceNotFoundResponse.class));
							}
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
