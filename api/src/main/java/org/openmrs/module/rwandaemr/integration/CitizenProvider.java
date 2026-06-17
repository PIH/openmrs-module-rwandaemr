/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Supports connections to and operations with the citizen endpoints in the HIE
 */
@Component("citizenProvider")
public class CitizenProvider {

	protected Log log = LogFactory.getLog(getClass());

	private final IntegrationConfig integrationConfig;
	private static final String CITIZEN_FETCH_TIMEOUT_MS_GP = "rwandaemr.hie.citizenFetchTimeoutMs";
	private static final int DEFAULT_CITIZEN_FETCH_TIMEOUT_MS = 20000;

	public CitizenProvider(
			@Autowired IntegrationConfig integrationConfig
	) {
		this.integrationConfig = integrationConfig;
	}

	/**
	 * This will attempt to retrieve the Citizen from the HIE population registry
	 * for the given identifier, identifierSystem and fosaId
	 */
	public Citizen getCitizen(String identifierSystem, String identifier, String fosaId) throws Exception {
		if (!integrationConfig.isHieEnabled()) {
			log.warn("Incomplete credentials supplied to connect to getCitizen, skipping");
			return null;
		}
		if (StringUtils.isBlank(identifierSystem) || StringUtils.isBlank(identifier) || StringUtils.isBlank(fosaId)) {
			log.warn("All required arguments not supplied to connect to getCitizen, skipping");
			return null;
		}
		try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
			ObjectMapper mapper = new ObjectMapper();
			String endpoint = "/api/v1/citizens/getCitizen";
			HttpPost httpPost = new HttpPost(integrationConfig.getHieEndpointUrl(endpoint));
			int fetchTimeoutMs = resolveCitizenFetchTimeoutMs();
			httpPost.setConfig(RequestConfig.copy(RequestConfig.DEFAULT)
					.setConnectTimeout(HttpUtils.CONNECT_TIMEOUT)
					.setConnectionRequestTimeout(HttpUtils.CONNECTION_REQUEST_TIMEOUT)
					.setSocketTimeout(fetchTimeoutMs)
					.build());
			Map<String, String> postBody = new HashMap<>();
			postBody.put("documentType", identifierSystem);
			postBody.put("documentNumber", identifier);
			postBody.put("fosaid", fosaId);
			log.warn("POSTING " + endpoint + ": " + postBody);
			log.warn("Using citizen fetch socket timeout: " + fetchTimeoutMs + " ms");
			httpPost.setEntity(new StringEntity(mapper.writeValueAsString(postBody)));
			httpPost.setHeader("Content-Type", "application/json");
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				int statusCode = response.getStatusLine().getStatusCode();
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}
				log.warn("Data: " + data);
				if (statusCode != 200) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}
				CitizenResponse citizenResponse = mapper.readValue(data, CitizenResponse.class);
				if (citizenResponse.getData() == null || !"ok".equalsIgnoreCase(citizenResponse.getStatus())) {
					log.warn("No matching citizen found.  Http Status Code: " + statusCode + "; Response: " + data);
					return null;
				}
				return citizenResponse.getData();
			}
		}
	}

	private int resolveCitizenFetchTimeoutMs() {
		try {
			String configured = ConfigUtil.getProperty(CITIZEN_FETCH_TIMEOUT_MS_GP);
			if (StringUtils.isNotBlank(configured)) {
				int parsed = Integer.parseInt(configured.trim());
				if (parsed > 0) {
					return parsed;
				}
				log.warn("Ignoring non-positive " + CITIZEN_FETCH_TIMEOUT_MS_GP + " value: " + configured);
			}
		} catch (Exception e) {
			log.warn("Invalid " + CITIZEN_FETCH_TIMEOUT_MS_GP + " value, using default " + DEFAULT_CITIZEN_FETCH_TIMEOUT_MS + " ms");
		}
		return DEFAULT_CITIZEN_FETCH_TIMEOUT_MS;
	}
}
