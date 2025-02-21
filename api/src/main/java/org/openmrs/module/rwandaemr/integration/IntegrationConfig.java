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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Translation layer between the FHIR Patient returned from the client registry and an OpenMRS patient
 */
@Component
public class IntegrationConfig {

	protected Log log = LogFactory.getLog(getClass());

	public static final String IDENTIFIER_SYSTEM_NID = "NID";
	public static final String IDENTIFIER_SYSTEM_NID_APPLICATION_NUMBER = "NID_APPLICATION_NUMBER";
	public static final String IDENTIFIER_SYSTEM_NIN = "NIN";
	public static final String IDENTIFIER_SYSTEM_UPI = "UPI";
	public static final String IDENTIFIER_SYSTEM_PASSPORT = "PASSPORT";

	private final RwandaEmrConfig rwandaEmrConfig;
	private Map<String, PatientIdentifierType> identifierSystems = null;

	public IntegrationConfig(@Autowired RwandaEmrConfig rwandaEmrConfig) {
		this.rwandaEmrConfig = rwandaEmrConfig;
	}

	public synchronized Map<String, PatientIdentifierType> getIdentifierSystems() {
		if (identifierSystems == null) {
			identifierSystems = new HashMap<>();
			identifierSystems.put(IDENTIFIER_SYSTEM_NID, rwandaEmrConfig.getNationalId());
			identifierSystems.put(IDENTIFIER_SYSTEM_NID_APPLICATION_NUMBER, rwandaEmrConfig.getNidApplicationNumber());
			identifierSystems.put(IDENTIFIER_SYSTEM_NIN, rwandaEmrConfig.getNIN());
			identifierSystems.put(IDENTIFIER_SYSTEM_UPI, rwandaEmrConfig.getUPID());
			identifierSystems.put(IDENTIFIER_SYSTEM_PASSPORT, rwandaEmrConfig.getPassportNumber());
		}
		return identifierSystems;
	}
}
