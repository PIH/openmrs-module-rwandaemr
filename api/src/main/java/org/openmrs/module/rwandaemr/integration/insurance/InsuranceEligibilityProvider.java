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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

	public IntegrationResponse checkEligibility(String type, String identifier) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(config.isEligibilityCheckEnabled());
		if (ret.isEnabled()) {
			try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
				ObjectMapper mapper = new ObjectMapper();
				String url = config.getEligibilityCheckUrl();
				url = url.replace("{identifier}", identifier);
				url = url.replace("{type}", type);
				HttpGet httpGet = new HttpGet(url);
				log.debug("GETTING " + config.getEligibilityCheckUrl());
				httpGet.setHeader("Content-Type", "application/json");
				String apiKey = config.getEligibilityCheckApiKey();
				if (StringUtils.isNotBlank(apiKey)) {
					httpGet.setHeader("x-api-key", apiKey);
				}
				String apiOrigin = config.getEligibilityCheckApiOrigin();
				if (StringUtils.isNotBlank(apiOrigin)) {
					httpGet.setHeader("Origin", apiOrigin);
				}
				ret.setEndpointAccessible(false);
				try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
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
								if ("cbhi".equalsIgnoreCase(type)) {
									ret.setResponseEntity(mapper.readValue(data, CbhiDetails.class));

								} else if ("rama".equalsIgnoreCase(type)) {
									ret.setResponseEntity(mapper.readValue(data, RamaDetails.class));
								}
								else if ("cbhi-special-case".equalsIgnoreCase(type)) {
									ret.setResponseEntity(mapper.readValue(data, CbhiSpecialCaseDetails.class));
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
