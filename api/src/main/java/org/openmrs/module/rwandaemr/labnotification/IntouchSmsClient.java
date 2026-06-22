package org.openmrs.module.rwandaemr.labnotification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class IntouchSmsClient {

	protected final Log log = LogFactory.getLog(getClass());

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private final LabNotificationConfig config;

	public IntouchSmsClient(@Autowired LabNotificationConfig config) {
		this.config = config;
	}

	public IntouchSmsResponse send(String phoneNumber, String content) {
		IntouchSmsResponse result = new IntouchSmsResponse();
		Map<String, String> requestPayload = buildRequestPayload(phoneNumber, content, false);
		result.setRequestPayload(toJson(requestPayload));
		try (CloseableHttpClient httpClient = HttpUtils.getHttpClient(null, null, false)) {
			HttpPost httpPost = new HttpPost(config.getIntouchUrl());
			httpPost.setEntity(new UrlEncodedFormEntity(toFormParameters(buildRequestPayload(phoneNumber, content, true)),
					StandardCharsets.UTF_8));
			log.info("Sending lab result notification SMS to " + phoneNumber);
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				result.setResponseCode(response.getStatusLine().getStatusCode());
				HttpEntity entity = response.getEntity();
				String responseBody = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
				result.setResponseBody(responseBody);
				normalizeGatewayResponse(result);
			}
		}
		catch (Exception e) {
			result.setSuccess(false);
			result.setErrorMessage(e.getMessage());
		}
		return result;
	}

	private Map<String, String> buildRequestPayload(String phoneNumber, String content, boolean includePassword) {
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("username", config.getUsername());
		payload.put("password", includePassword ? config.getPassword() : "********");
		payload.put("to", phoneNumber);
		payload.put("from", config.getSender());
		payload.put("content", content);
		payload.put("coding", config.getCoding());
		if (StringUtils.isNotBlank(config.getDlrUrl())) {
			payload.put("dlr-url", config.getDlrUrl());
			payload.put("dlr-level", config.getDlrLevel());
		}
		return payload;
	}

	private List<NameValuePair> toFormParameters(Map<String, String> payload) {
		List<NameValuePair> parameters = new ArrayList<>();
		for (Map.Entry<String, String> entry : payload.entrySet()) {
			if (StringUtils.isNotBlank(entry.getValue())) {
				parameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
			}
		}
		return parameters;
	}

	private void normalizeGatewayResponse(IntouchSmsResponse result) {
		String body = StringUtils.defaultString(result.getResponseBody()).trim();
		boolean httpSuccess = result.getResponseCode() != null && result.getResponseCode() >= 200 &&
				result.getResponseCode() < 300;
		if (httpSuccess && body.toLowerCase().startsWith("success")) {
			result.setSuccess(true);
			result.setProviderMessageId(extractQuotedValue(body));
			return;
		}
		result.setSuccess(false);
		if (body.toLowerCase().startsWith("error")) {
			result.setErrorMessage(stripGatewayPrefix(body));
		}
		else if (StringUtils.isNotBlank(body)) {
			result.setErrorMessage(body);
		}
		else {
			result.setErrorMessage("InTouch SMS gateway returned HTTP " + result.getResponseCode());
		}
	}

	private String extractQuotedValue(String body) {
		String normalized = body.replace('\u201c', '"').replace('\u201d', '"');
		int firstQuote = normalized.indexOf('"');
		int lastQuote = normalized.lastIndexOf('"');
		if (firstQuote >= 0 && lastQuote > firstQuote) {
			return normalized.substring(firstQuote + 1, lastQuote).trim();
		}
		return normalized.replaceFirst("(?i)^success", "").trim();
	}

	private String stripGatewayPrefix(String body) {
		return body.replaceFirst("(?i)^error", "").replace('\u201c', ' ').replace('\u201d', ' ')
				.replace('"', ' ').trim();
	}

	private String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			log.warn("Unable to serialize InTouch SMS payload", e);
			return null;
		}
	}
}
