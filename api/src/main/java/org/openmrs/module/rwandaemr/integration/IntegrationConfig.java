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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrConstants;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
	private final LocationTagUtil locationTagUtil;
	private Map<String, PatientIdentifierType> identifierSystems = null;

	public IntegrationConfig(@Autowired RwandaEmrConfig rwandaEmrConfig, @Autowired LocationTagUtil locationTagUtil) {
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.locationTagUtil = locationTagUtil;
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

	public String getIdentifierSystem(PatientIdentifierType patientIdentifierType) {
		for (String system : getIdentifierSystems().keySet()) {
			if (getIdentifierSystems().get(system).equals(patientIdentifierType)) {
				return system;
			}
		}
		return null;
	}

	public boolean isMPIEnabled() {
		String url = ConfigUtil.getProperty(RwandaEmrConstants.MPI_URL_PROPERTY);
		String username = ConfigUtil.getProperty(RwandaEmrConstants.MPI_USERNAME_PROPERTY);
		String password = ConfigUtil.getProperty(RwandaEmrConstants.MPI_PASSWORD_PROPERTY);
		return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password);
	}

	/**
	 * @return the base url configured for the MPI, with trailing slashes removed, or null if no mpi url is configured
	 */
	public String getMpiBaseUrl() {
		String baseUrl = ConfigUtil.getProperty(RwandaEmrConstants.MPI_URL_PROPERTY);
		if (!StringUtils.isBlank(baseUrl)) {
			if (baseUrl.endsWith("/")) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}
			return baseUrl;
		}
		return null;
	}

	/**
	 * @return the full endpoint url for the given path and parameters, or null if no mpi base url is configured
	 */
	public String getMpiEndpointUrl(String path, String... parameterNamesAndValues) {
		String baseUrl = getMpiBaseUrl();
		if (!StringUtils.isBlank(baseUrl)) {
			StringBuilder sb = new StringBuilder(baseUrl);
			if (!path.startsWith("/")) {
				sb.append("/");
			}
			sb.append(path);
			for (int i=0; i<parameterNamesAndValues.length; i+=2) {
				sb.append(i == 0 ? "?" : "&").append(parameterNamesAndValues[i]).append("=").append(parameterNamesAndValues[i+1]);
			}
			return sb.toString();
		}
		return null;
	}

	/**
	 * Return the FOSA ID associated with the given location.  This will determine the visit location associated
	 * with the given location, and return the FOSA ID associated with this visit location.  If more than one FOSA
	 * ID is found that could be a match, then null is returned
	 */
	public String getFosaId(Location location) {
		if (location != null) {
			LocationAttributeType fosaIdType = rwandaEmrConfig.getFosaId();
			if (fosaIdType != null) {
				Set<String> fosaIdsFound = new HashSet<>();
				for (Location visitLocation : locationTagUtil.getVisitLocationsForLocation(location)) {
					for (LocationAttribute attribute : visitLocation.getActiveAttributes()) {
						if (attribute.getAttributeType().equals(fosaIdType)) {
							fosaIdsFound.add(attribute.getValueReference());
						}
					}
				}
				if (fosaIdsFound.size() == 1) {
					return fosaIdsFound.iterator().next();
				}
				else if (fosaIdsFound.size() > 1) {
					log.warn("Multiple FOSA IDs are found associated with location " + location.getName());
				}
				else {
					log.warn("No FOSA IDs are associated with location " + location.getName());
				}
			}
			else {
				log.warn("No FOSA ID location attribute is defined");
			}
		}
		else {
			log.warn("No location is provided to determine FOSA ID");
		}
		return null;
	}
}
