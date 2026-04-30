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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.mohbilling.businesslogic.InsuranceUtil;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration of the insurance eligibility checking endpoint
 */
@Component
public class InsuranceIntegrationConfig {

	protected Log log = LogFactory.getLog(getClass());

	private static final String ELIGIBILITY_CHECK_PREFIX = "rwandaemr.insuranceEligibility.";
	public static final String ELIGIBILITY_CHECK_URL = ELIGIBILITY_CHECK_PREFIX + "url";
	public static final String ELIGIBILITY_CHECK_API_KEY = ELIGIBILITY_CHECK_PREFIX + "apiKey";
	public static final String ELIGIBILITY_CHECK_API_ORIGIN = ELIGIBILITY_CHECK_PREFIX + "apiOrigin";
	public static final String OTP_VERIFY_URL = ELIGIBILITY_CHECK_PREFIX + "otpVerifyUrl";
	public static final String OTP_VERIFY_API_KEY = ELIGIBILITY_CHECK_PREFIX + "otpVerifyApiKey";
	public static final String OTP_VERIFY_API_ORIGIN = ELIGIBILITY_CHECK_PREFIX + "otpVerifyApiOrigin";
	public static final String PATIENT_RECEPTION_URL = ELIGIBILITY_CHECK_PREFIX + "patientReceptionUrl";
	public static final String PATIENT_RECEPTION_API_KEY = ELIGIBILITY_CHECK_PREFIX + "patientReceptionApiKey";
	public static final String PATIENT_RECEPTION_API_ORIGIN = ELIGIBILITY_CHECK_PREFIX + "patientReceptionApiOrigin";
	public static final String PATIENT_RECEPTION_FACILITY_FOSA_ID_OVERRIDE = ELIGIBILITY_CHECK_PREFIX + "patientReceptionFacilityFosaIdOverride";

	public InsuranceIntegrationConfig() {
	}

	public String getEligibilityCheckUrl() {
		return ConfigUtil.getProperty(ELIGIBILITY_CHECK_URL);
	}

	public String getEligibilityCheckApiKey() {
		return ConfigUtil.getProperty(ELIGIBILITY_CHECK_API_KEY);
	}

	public String getEligibilityCheckApiOrigin() {
		return ConfigUtil.getProperty(ELIGIBILITY_CHECK_API_ORIGIN);
	}

	public boolean isEligibilityCheckEnabled() {
		return StringUtils.isNotBlank(getEligibilityCheckUrl());
	}

	public String getOtpVerifyUrl() {
		return ConfigUtil.getProperty(OTP_VERIFY_URL);
	}

	public String getOtpVerifyApiKey() {
		String apiKey = ConfigUtil.getProperty(OTP_VERIFY_API_KEY);
		if (StringUtils.isNotBlank(apiKey)) {
			return apiKey;
		}
		return getEligibilityCheckApiKey();
	}

	public String getOtpVerifyApiOrigin() {
		String apiOrigin = ConfigUtil.getProperty(OTP_VERIFY_API_ORIGIN);
		if (StringUtils.isNotBlank(apiOrigin)) {
			return apiOrigin;
		}
		return getEligibilityCheckApiOrigin();
	}

	public boolean isOtpVerificationEnabled() {
		return StringUtils.isNotBlank(getOtpVerifyUrl());
	}

	public String getPatientReceptionUrl() {
		return ConfigUtil.getProperty(PATIENT_RECEPTION_URL);
	}

	public String getPatientReceptionApiKey() {
		String apiKey = ConfigUtil.getProperty(PATIENT_RECEPTION_API_KEY);
		if (StringUtils.isNotBlank(apiKey)) {
			return apiKey;
		}
		return getEligibilityCheckApiKey();
	}

	public String getPatientReceptionApiOrigin() {
		String apiOrigin = ConfigUtil.getProperty(PATIENT_RECEPTION_API_ORIGIN);
		if (StringUtils.isNotBlank(apiOrigin)) {
			return apiOrigin;
		}
		return getEligibilityCheckApiOrigin();
	}

	public boolean isPatientReceptionEnabled() {
		return StringUtils.isNotBlank(getPatientReceptionUrl());
	}

	public String getPatientReceptionFacilityFosaIdOverride() {
		return StringUtils.trimToNull(ConfigUtil.getProperty(PATIENT_RECEPTION_FACILITY_FOSA_ID_OVERRIDE));
	}

	public List<String> getInsuranceTypesToVerify() {
		List<String> ret = new ArrayList<>();
		String property = ConfigUtil.getProperty(ELIGIBILITY_CHECK_PREFIX + "types");
		if (StringUtils.isNotBlank(property)) {
			for (String type : property.split(",")) {
				ret.add(type.trim());
			}
		}
		return ret;
	}

	public Map<Insurance, String> getInsurancesToVerify() {
		Map<Insurance, String> insurancesToVerify = new HashMap<>();
		List<Insurance> allInsurances = InsuranceUtil.getAllInsurances();
		for (String category : getInsuranceTypesToVerify()) {
			String property = ConfigUtil.getProperty(ELIGIBILITY_CHECK_PREFIX + category);
			if (StringUtils.isNotBlank(property)) {
				for (String s : property.split(",")) {
					for (Insurance insurance : allInsurances) {
						if (insurance.getCategory().equals(s) || insurance.getName().equals(s) || insurance.getInsuranceId().toString().equals(s)) {
							insurancesToVerify.put(insurance, category);
						}
					}
				}
			}
		}
		return insurancesToVerify;
	}

}
