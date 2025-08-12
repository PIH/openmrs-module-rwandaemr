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
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Component;

/**
 * Configuration of the insurance eligibility checking endpoint
 */
@Component
public class InsuranceIntegrationConfig {

	protected Log log = LogFactory.getLog(getClass());

	private static final String ELIGIBILITY_CHECK_PREFIX = "rwandaemr.insuranceEligibility.";
	public static final String ELIGIBILITY_CHECK_URL = ELIGIBILITY_CHECK_PREFIX + "url";

	public InsuranceIntegrationConfig() {
	}

	public String getEligibilityCheckUrl() {
		return ConfigUtil.getProperty(ELIGIBILITY_CHECK_URL);
	}

	public boolean isEligibilityCheckEnabled() {
		return StringUtils.isNotBlank(getEligibilityCheckUrl());
	}

}
