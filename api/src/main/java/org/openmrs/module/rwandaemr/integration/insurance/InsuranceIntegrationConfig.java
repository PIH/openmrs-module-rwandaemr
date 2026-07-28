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
	private static final String SHARED_API_PREFIX = "/insurance_integration/api/v2";
	public static final String SHARED_BASE_URL = "rwandaemr.insuranceIntegration.baseUrl";
	public static final String ELIGIBILITY_CHECK_URL = ELIGIBILITY_CHECK_PREFIX + "url";
	public static final String ELIGIBILITY_OTP_VERIFY_URL = ELIGIBILITY_CHECK_PREFIX + "otpVerifyUrl";
	public static final String ELIGIBILITY_MMI_PATIENT_TYPES_URL = ELIGIBILITY_CHECK_PREFIX + "mmiPatientTypesUrl";
	public static final String ELIGIBILITY_MMI_RECEPTION_URL = ELIGIBILITY_CHECK_PREFIX + "mmiReceptionUrl";
	public static final String ELIGIBILITY_CHECK_API_KEY = ELIGIBILITY_CHECK_PREFIX + "apiKey";
	public static final String ELIGIBILITY_CHECK_API_ORIGIN = ELIGIBILITY_CHECK_PREFIX + "apiOrigin";
	public static final String PATIENT_RECEPTION_URL = ELIGIBILITY_CHECK_PREFIX + "patientReceptionUrl";
	public static final String PATIENT_RECEPTION_API_KEY = ELIGIBILITY_CHECK_PREFIX + "patientReceptionApiKey";
	public static final String PATIENT_RECEPTION_API_ORIGIN = ELIGIBILITY_CHECK_PREFIX + "patientReceptionApiOrigin";
	public static final String PATIENT_RECEPTION_FACILITY_FOSA_ID_OVERRIDE = ELIGIBILITY_CHECK_PREFIX + "patientReceptionFacilityFosaIdOverride";

	public InsuranceIntegrationConfig() {
	}

	public String getEligibilityCheckUrl() {
		return configuredUrlOrSharedPath(ELIGIBILITY_CHECK_URL, "/eligibility-check");
	}

	public String getEligibilityOtpVerifyUrl() {
		return configuredUrlOrSharedPath(ELIGIBILITY_OTP_VERIFY_URL, "/otp-verification");
	}

	public String getMmiPatientTypesUrl() {
		return configuredUrlOrSharedPath(ELIGIBILITY_MMI_PATIENT_TYPES_URL, "/patient-types");
	}

	public String getMmiReceptionUrl() {
		return configuredUrlOrSharedPath(ELIGIBILITY_MMI_RECEPTION_URL, "/patient-reception");
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

	public String getPatientReceptionUrl() {
		return configuredUrlOrSharedPath(PATIENT_RECEPTION_URL, "/patient-reception");
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

	public String getSharedBaseUrl() {
		return ConfigUtil.getProperty(SHARED_BASE_URL);
	}

	private String configuredUrlOrSharedPath(String property, String endpointPath) {
		String configured = ConfigUtil.getProperty(property);
		if (StringUtils.isNotBlank(configured)) {
			return configured;
		}
		String baseUrl = getSharedBaseUrl();
		if (StringUtils.isBlank(baseUrl)) {
			return null;
		}
		return joinUrl(baseUrl, endpointPath);
	}

	private String joinUrl(String baseUrl, String endpointPath) {
		String normalizedBase = baseUrl.trim();
		if (normalizedBase.endsWith("/")) {
			normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
		}
		if (!normalizedBase.endsWith(SHARED_API_PREFIX)) {
			normalizedBase = normalizedBase + SHARED_API_PREFIX;
		}
		String normalizedPath = endpointPath == null ? "" : endpointPath.trim();
		if (!normalizedPath.startsWith("/")) {
			normalizedPath = "/" + normalizedPath;
		}
		return normalizedBase + normalizedPath;
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
