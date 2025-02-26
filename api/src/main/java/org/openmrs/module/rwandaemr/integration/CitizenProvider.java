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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
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

	public CitizenProvider(
			@Autowired IntegrationConfig integrationConfig
	) {
		this.integrationConfig = integrationConfig;
	}

	/**
	 * This will attempt to retrieve the Citizen from the HIE population registry
	 * for the given identifier, identifierSystem and fosaId
	 */
	public Citizen getCitizen(String identifierSystem, String identifier, String fosaId) {
		if (!integrationConfig.isHieEnabled()) {
			log.debug("Incomplete credentials supplied to connect to getCitizen, skipping");
			return null;
		}
		if (StringUtils.isBlank(identifierSystem) || StringUtils.isBlank(identifier) || StringUtils.isBlank(fosaId)) {
			log.debug("All required arguments not supplied to connect to getCitizen, skipping");
			return null;
		}
		try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
			ObjectMapper mapper = new ObjectMapper();
			String endpoint = "/api/v1/citizens/getCitizen";
			HttpPost httpPost = new HttpPost(integrationConfig.getHieEndpointUrl(endpoint));
			Map<String, String> postBody = new HashMap<>();
			postBody.put("documentType", identifierSystem);
			postBody.put("documentNumber", identifier);
			postBody.put("fosaid", fosaId);
			log.debug("POSTING " + endpoint + ": " + postBody);
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
				log.debug("Data: " + data);
				if (statusCode != 200) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}
				CitizenResponse citizenResponse = mapper.readValue(data, CitizenResponse.class);
				if (citizenResponse.getData() == null || !"ok".equalsIgnoreCase(citizenResponse.getStatus())) {
					throw new IllegalStateException("No citizen retrieved.  Status: " + citizenResponse.getStatus());
				}
				return citizenResponse.getData();
			}
		} catch (Exception e) {
			log.debug("An error occurred trying to generate UPID, returning null", e);
		}
		return null;
	}
}
