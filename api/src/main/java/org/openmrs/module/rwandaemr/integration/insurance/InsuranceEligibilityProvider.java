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
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supports connections to and operations with the insurance-eligibility endpoint in the HIE
 */
@Component("insuranceEligibilityProvider")
public class InsuranceEligibilityProvider {

	protected Log log = LogFactory.getLog(getClass());

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
		if (sendOtp) {
			Map<String, Object> parameters = new HashMap<>();
			parameters.put("insuranceType", type);
			parameters.put("identifier", identifier);
			parameters.put("fosaid", fosaid);
			parameters.put("sendOTP", true);
			return postRequest(config.getEligibilityCheckUrl(), parameters, Object.class, "MMI_ELIGIBILITY_OTP_REQUEST");
		}

		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(config.isEligibilityCheckEnabled());
		if (ret.isEnabled()) {
			try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
				ObjectMapper mapper = new ObjectMapper();
				String url = config.getEligibilityCheckUrl();
				url = url.replace("{identifier}", URLEncoder.encode(identifier == null ? "" : identifier, StandardCharsets.UTF_8.name()));
				url = url.replace("{type}", URLEncoder.encode(type == null ? "" : type, StandardCharsets.UTF_8.name()));
				log.debug("GETTING " + url);
				String apiKey = config.getEligibilityCheckApiKey();
				String apiOrigin = config.getEligibilityCheckApiOrigin();
				ret.setEndpointAccessible(false);

				HttpGet httpGet = new HttpGet(url);
				httpGet.setHeader("Content-Type", "application/json");
				if (StringUtils.isNotBlank(apiKey)) {
					httpGet.setHeader("x-api-key", apiKey);
				}
				if (StringUtils.isNotBlank(apiOrigin)) {
					httpGet.setHeader("Origin", apiOrigin);
				}

				try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
					ret.setEndpointAccessible(true);
					int getStatus = response.getStatusLine().getStatusCode();
					String getData = readEntityAsString(response.getEntity());
					ret.setResponseCode(getStatus);
					log.info("INS_ELIGIBILITY_MARKER GET_PRIMARY_RESULT status=" + getStatus + " type=" + type + " identifier=" + identifier);
					if (StringUtils.isNotBlank(getData)) {
						ret.setResponseEntity(normalizeResponse(type, identifier, getStatus, getData, mapper));
					}

					// Compatibility fallback: if GET fails, try POST with body semantics used by 3-x API.
					if (getStatus >= 500 || getStatus == 405 || getStatus == 404) {
						HttpPost httpPost = new HttpPost(url);
						httpPost.setHeader("Content-Type", "application/json");
						if (StringUtils.isNotBlank(apiKey)) {
							httpPost.setHeader("x-api-key", apiKey);
						}
						if (StringUtils.isNotBlank(apiOrigin)) {
							httpPost.setHeader("Origin", apiOrigin);
						}
						Map<String, Object> parameters = new HashMap<>();
						parameters.put("insuranceType", type);
						parameters.put("identifier", identifier);
						parameters.put("fosaid", fosaid);
						parameters.put("sendOTP", false);
						httpPost.setEntity(new StringEntity(mapper.writeValueAsString(parameters)));
						log.info("INS_ELIGIBILITY_MARKER GET_FAIL_RETRY_POST status=" + getStatus + " type=" + type + " identifier=" + identifier);
						try (CloseableHttpResponse postResponse = httpClient.execute(httpPost)) {
							int postStatus = postResponse.getStatusLine().getStatusCode();
							String postData = readEntityAsString(postResponse.getEntity());
							log.info("INS_ELIGIBILITY_MARKER POST_FALLBACK_RESULT status=" + postStatus + " type=" + type + " identifier=" + identifier);
							if (postStatus == 200 && StringUtils.isNotBlank(postData)) {
								ret.setResponseCode(postStatus);
								ret.setResponseEntity(normalizeResponse(type, identifier, postStatus, postData, mapper));
								ret.setErrorMessage(null);
								log.info("INS_ELIGIBILITY_MARKER POST_FALLBACK_SUCCESS type=" + type + " identifier=" + identifier);
							}
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

	public IntegrationResponse verifyOtp(String type, String identifier, String otpCode, String fosaid) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", type);
		parameters.put("identifier", identifier);
		parameters.put("otpCode", otpCode);
		parameters.put("fosaid", fosaid);
		return postRequest(config.getEligibilityOtpVerifyUrl(), parameters, MmiOtpVerificationResponse.class, "MMI_OTP_VERIFY");
	}

	public IntegrationResponse getPatientTypes(String insuranceType, String facilityFosaId) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("insuranceType", insuranceType);
		parameters.put("facility_fosa_id", facilityFosaId);
		return postRequest(config.getMmiPatientTypesUrl(), parameters, MmiPatientTypesResponse.class, "MMI_PATIENT_TYPES");
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
		return postRequest(config.getMmiReceptionUrl(), parameters, MmiReceptionResponse.class, "MMI_RECEPTION");
	}

	private IntegrationResponse postRequest(String url, Map<String, Object> parameters, Class<?> responseType,
	                                        String operationType) {
		IntegrationResponse ret = new IntegrationResponse();
		ret.setEnabled(StringUtils.isNotBlank(url));
		String requestPayload = toJson(parameters);
		if (!ret.isEnabled()) {
			ret.setErrorMessage("Endpoint URL is not configured");
			persistRhipIntegrationLog(url, operationType, requestPayload, ret);
			return ret;
		}

		try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
			HttpPost httpPost = new HttpPost(url);
			log.debug("POSTING " + url);
			if ("MMI_RECEPTION".equals(operationType)) {
				log.info("MMI reception request payload: " + requestPayload);
			}
			httpPost.setHeader("Content-Type", "application/json");
			String apiKey = config.getEligibilityCheckApiKey();
			if (StringUtils.isNotBlank(apiKey)) {
				httpPost.setHeader("x-api-key", apiKey);
			}
			String apiOrigin = config.getEligibilityCheckApiOrigin();
			if (StringUtils.isNotBlank(apiOrigin)) {
				httpPost.setHeader("Origin", apiOrigin);
			}
			httpPost.setEntity(new StringEntity(requestPayload == null ? "" : requestPayload));
			ret.setEndpointAccessible(false);
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				ret.setEndpointAccessible(true);
				ret.setResponseCode(response.getStatusLine().getStatusCode());
				String data = readEntityAsString(response.getEntity());
				if (StringUtils.isNotBlank(data)) {
					try {
						ret.setResponseEntity(OBJECT_MAPPER.readValue(data, responseType));
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
			persistRhipIntegrationLog(url, operationType, requestPayload, ret);
		}

		return ret;
	}

	private String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			log.warn("Unable to serialize MMI integration payload", e);
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
			log.warn("Unable to persist MMI request in RHIP integration logs", e);
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

	private String readEntityAsString(HttpEntity entity) {
		if (entity == null) {
			return "";
		}
		try {
			return EntityUtils.toString(entity);
		}
		catch (Exception ignored) {
			return "";
		}
	}

	/**
	 * Normalize both legacy responses (cbhi/rama payloads) and new 3-x unified payload
	 * into InsuranceEligibilityResponse expected by the 3-x UI.
	 */
	private InsuranceEligibilityResponse normalizeResponse(String type, String identifier, int responseCode, String data, ObjectMapper mapper) throws Exception {
		JsonNode root = mapper.readTree(data);
		InsuranceEligibilityResponse normalized = new InsuranceEligibilityResponse();
		normalized.setInsuranceType(type);
		normalized.setIdentifier(identifier);

		// New unified format already used by 3-x endpoint.
		if (root.has("success")) {
			InsuranceEligibilityResponse parsed = mapper.readValue(data, InsuranceEligibilityResponse.class);
			if (StringUtils.isBlank(parsed.getInsuranceType())) {
				parsed.setInsuranceType(type);
			}
			if (StringUtils.isBlank(parsed.getIdentifier())) {
				parsed.setIdentifier(identifier);
			}
			log.info("INS_ELIGIBILITY_MARKER NORMALIZED_UNIFIED type=" + type + " identifier=" + identifier + " success=" + parsed.isSuccess());
			return parsed;
		}

		// Legacy CBHI payload compatibility.
		if (root.has("members") && root.get("members").isArray()) {
			List<InsuranceMember> members = new ArrayList<InsuranceMember>();
			InsuranceOwner owner = null;
			for (JsonNode memberNode : root.get("members")) {
				InsuranceMember m = new InsuranceMember();
				m.setPatientId(text(memberNode, "memberId"));
				String first = text(memberNode, "firstName");
				String last = text(memberNode, "lastName");
				m.setFullName((first + " " + last).trim());
				m.setDateOfBirth(text(memberNode, "dateOfBirth"));
				m.setGender(text(memberNode, "gender"));
				m.setDocumentNumber(text(memberNode, "documentNumber"));
				m.setEligibilityStartDate(text(memberNode, "eligibilityStartDate"));
				m.setIsEligible(bool(memberNode, "isEligible"));
				m.setIsGovernmentSponsored(boolGovernmentSponsored(memberNode));
				m.setStatus(text(root, "status"));
				String relationType = text(memberNode, "type");
				if ("HEAD".equalsIgnoreCase(relationType)) {
					InsuranceOwner head = new InsuranceOwner();
					head.setPatientId(m.getPatientId());
					head.setFullName(m.getFullName());
					head.setIsEligible(m.getIsEligible());
					head.setDocumentNumber(m.getDocumentNumber());
					head.setTelephone(m.getTelephone());
					head.setGender(m.getGender());
					head.setDateOfBirth(m.getDateOfBirth());
					head.setNid(m.getNid());
					head.setEligibilityStartDate(m.getEligibilityStartDate());
					head.setIsGovernmentSponsored(m.getIsGovernmentSponsored());
					head.setStatus(m.getStatus());
					head.setDependants(new ArrayList<InsuranceMember>());
					owner = head;
				} else {
					members.add(m);
				}
			}
			if (owner == null) {
				owner = new InsuranceOwner();
				owner.setFullName(text(root, "headOfHouseholdId"));
				owner.setDocumentNumber(text(root, "headOfHouseholdId"));
				owner.setDependants(new ArrayList<InsuranceMember>());
				owner.setIsGovernmentSponsored(boolGovernmentSponsored(root));
				owner.setStatus(text(root, "status"));
			}
			owner.setDependants(members);
			normalized.setData(owner);
			normalized.setStatus(text(root, "status"));
			normalized.setSuccess(responseCode == 200);
			normalized.setMessage(responseCode == 200 ? null : "Insurance not found");
			log.info("INS_ELIGIBILITY_MARKER NORMALIZED_LEGACY_CBHI type=" + type + " identifier=" + identifier + " members=" + root.get("members").size());
			return normalized;
		}

		// Legacy RAMA payload compatibility.
		if (root.has("firstName") || root.has("lastName") || root.has("mainAffiliateId")) {
			InsuranceOwner owner = new InsuranceOwner();
			String first = text(root, "firstName");
			String last = text(root, "lastName");
			owner.setFullName((first + " " + last).trim());
			owner.setGender(text(root, "gender"));
			owner.setDateOfBirth(text(root, "dateOfBirth"));
			owner.setDocumentNumber(text(root, "mainAffiliateId"));
			owner.setPatientId(text(root, "cardId"));
			owner.setIsEligible(bool(root, "isEligible"));
			owner.setStatus(text(root, "status"));
			owner.setDependants(new ArrayList<InsuranceMember>());
			normalized.setData(owner);
			normalized.setStatus(owner.getStatus());
			normalized.setSuccess(responseCode == 200);
			normalized.setMessage(responseCode == 200 ? null : "Insurance not found");
			log.info("INS_ELIGIBILITY_MARKER NORMALIZED_LEGACY_RAMA type=" + type + " identifier=" + identifier);
			return normalized;
		}

		// Unknown schema fallback.
		normalized.setSuccess(responseCode == 200);
		normalized.setMessage(text(root, "message"));
		normalized.setError(text(root, "error"));
		log.info("INS_ELIGIBILITY_MARKER NORMALIZED_UNKNOWN type=" + type + " identifier=" + identifier + " responseCode=" + responseCode);
		return normalized;
	}

	private String text(JsonNode node, String field) {
		if (node == null || field == null || !node.has(field) || node.get(field).isNull()) {
			return "";
		}
		return node.get(field).asText();
	}

	private Boolean bool(JsonNode node, String field) {
		if (node == null || field == null || !node.has(field) || node.get(field).isNull()) {
			return null;
		}
		return node.get(field).asBoolean();
	}

	/** Reads government-sponsored flag; supports unified and legacy (misspelled) JSON keys. */
	private Boolean boolGovernmentSponsored(JsonNode node) {
		if (node == null) {
			return null;
		}
		if (node.has("isGovernmentSponsored") && !node.get("isGovernmentSponsored").isNull()) {
			return node.get("isGovernmentSponsored").asBoolean();
		}
		if (node.has("isGovermentSponsored") && !node.get("isGovermentSponsored").isNull()) {
			return node.get("isGovermentSponsored").asBoolean();
		}
		return null;
	}
}
