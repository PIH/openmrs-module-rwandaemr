package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceOtpVerificationProvider;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@Controller
public class InsuranceOtpVerificationRestController {

	protected final Log log = LogFactory.getLog(getClass());

	@Autowired
	InsuranceOtpVerificationProvider insuranceOtpVerificationProvider;

	@RequestMapping(value = "/rest/v1/rwandaemr/insurance/otp-verification", method = RequestMethod.POST)
	@ResponseBody
	public Object verifyOtp(@RequestBody(required = false) Map<String, Object> body,
	                        HttpServletRequest request,
	                        HttpServletResponse response) throws ResponseException {
		String insuranceType = firstNonBlank(stringValue(body, "insuranceType"), request.getParameter("insuranceType"));
		String identifier = firstNonBlank(stringValue(body, "identifier"), request.getParameter("identifier"));
		String otpCode = firstNonBlank(stringValue(body, "otpCode"), request.getParameter("otpCode"));
		String fosaid = firstNonBlank(stringValue(body, "fosaid"), request.getParameter("fosaid"));

		IntegrationResponse ret = new IntegrationResponse();
		if (StringUtils.isBlank(insuranceType)) {
			insuranceType = "MMI";
		}
		if (StringUtils.isBlank(identifier)) {
			ret.setErrorMessage("identifier is required");
			return ret;
		}
		if (StringUtils.isBlank(otpCode)) {
			ret.setErrorMessage("otpCode is required");
			return ret;
		}
		if (!"MMI".equalsIgnoreCase(insuranceType.trim())) {
			ret.setErrorMessage("insuranceType must be MMI for otp verification");
			return ret;
		}

		return insuranceOtpVerificationProvider.verifyOtp(insuranceType.trim(), identifier.trim(), otpCode.trim(), fosaid);
	}

	private String stringValue(Map<String, Object> body, String key) {
		if (body == null || !body.containsKey(key) || body.get(key) == null) {
			return null;
		}
		return body.get(key).toString();
	}

	private String firstNonBlank(String first, String second) {
		return StringUtils.isNotBlank(first) ? first : second;
	}
}
