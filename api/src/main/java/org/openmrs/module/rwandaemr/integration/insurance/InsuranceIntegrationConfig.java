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
