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

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrConstants;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Supports MPI-related functionality that requires integration with the Rwandan Client Register and UPID Generator
 */
@Component("nidaMpiProvider")
public class NidaMpiProvider {

	protected Log log = LogFactory.getLog(getClass());

	private final FhirContext fhirContext;
	private final ClientRegistryPatientTranslator clientRegistryPatientTranslator;
	private final UpidPatientTranslator upidPatientTranslator;
	private final LocationTagUtil locationTagUtil;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final IntegrationConfig integrationConfig;

	public NidaMpiProvider(
			@Autowired @Qualifier("fhirR4") FhirContext fhirContext,
			@Autowired ClientRegistryPatientTranslator clientRegistryPatientTranslator,
			@Autowired UpidPatientTranslator upidPatientTranslator,
			@Autowired LocationTagUtil locationTagUtil,
			@Autowired RwandaEmrConfig rwandaEmrConfig,
			@Autowired IntegrationConfig integrationConfig
	) {
		this.fhirContext = fhirContext;
		this.clientRegistryPatientTranslator = clientRegistryPatientTranslator;
		this.upidPatientTranslator = upidPatientTranslator;
		this.locationTagUtil = locationTagUtil;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.integrationConfig = integrationConfig;
	}

	public boolean isEnabled() {
		String url = ConfigUtil.getProperty(RwandaEmrConstants.MPI_URL_PROPERTY);
		String username = ConfigUtil.getProperty(RwandaEmrConstants.MPI_USERNAME_PROPERTY);
		String password = ConfigUtil.getProperty(RwandaEmrConstants.MPI_PASSWORD_PROPERTY);
		return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password);
	}

	/**
	 * This method attempts to fulfill the workflow laid out in the Rwanda HIE guidelines, which is to first look up
	 * an existing patient in the Client Registry with an eligible identifier.  If no matching patient is found,
	 * then use the UPID generator to retrieve a UPID and retrieve patient details from the national population registry
	 * The registrationLocation is what will be used to determine the FOSA ID to send with the UPID generation request
	 */
	public Patient fetchPatientFromClientOrPopulationRegistry(Map<String, String> identifiersToSearch, Location registrationLocation) {
		Patient patient = null;
		for (String identifierType : identifiersToSearch.keySet()) {
			if (patient == null) {
				String identifierSystem = getIdentifierSystem(identifierType);
				if (identifierSystem != null) {
					String identifier = identifiersToSearch.get(identifierType);
					patient = fetchPatientFromClientRegistry(identifier);
				}
			}
		}
		if (patient == null) {
			for (String identifierType : identifiersToSearch.keySet()) {
				if (patient == null) {
					String identifierSystem = getIdentifierSystem(identifierType);
					if (identifierSystem != null) {
						String identifier = identifiersToSearch.get(identifierType);
						patient = generateUpidAndFetchPatientFromPopulationRegistry(identifierSystem, identifier, registrationLocation);
					}
				}
			}
		}
		return patient;
	}

	/**
	 * This will attempt to generate a UPID and retrieve patient details for the given identifierSystem and  identifier.
	 * The registrationLocation is used to determine the FOSA ID to send with the request.  If this is not found, this will return null.
	 * If this is successful, it will return the results.  If it is not successful, it will return null
	 */
	public Patient generateUpidAndFetchPatientFromPopulationRegistry(String identifierSystem, String identifier, Location registrationLocation) {
		if (!isEnabled()) {
			log.debug("Incomplete credentials supplied to connect to NIDA, skipping");
			return null;
		}
		if (!integrationConfig.getIdentifierSystems().containsKey(identifierSystem)) {
			log.debug("Identifier system " + identifierSystem + " is not supported, skipping UPID generation");
			return null;
		}
		String fosaId = getFosaId(registrationLocation);
		if (fosaId == null) {
			log.debug("Unable to determine the FOSA ID for " + registrationLocation.getName() + ", skipping UPID generation");
			return null;
		}
		try (CloseableHttpClient httpClient = getMpiClient()) {
			String endpoint = "/api/v1/citizens/getCitizen";
			String[] parameters = {"documentType", identifierSystem, "documentNumber", identifier, "fosaid", fosaId};
			HttpPost httpPost = new HttpPost(getMpiEndpointUrl(endpoint, parameters));
			log.debug("Attempting to generate UPID and retrieve patient " + identifier + " from NIDA");
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				int statusCode = response.getStatusLine().getStatusCode();
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}
				if (statusCode != 200) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}
				ObjectMapper mapper = new ObjectMapper();
				UpidPatientTranslator.UpidResponse upidResponse = mapper.readValue(data, UpidPatientTranslator.UpidResponse.class);
				if (upidResponse.getData() == null || !"ok".equalsIgnoreCase(upidResponse.getStatus())) {
					throw new IllegalStateException("No patient retrieve.  Status: " + upidResponse.getStatus());
				}
				return upidPatientTranslator.toOpenmrsType(upidResponse.getData());
			}
		} catch (Exception e) {
			log.debug("An error occurred trying to generate UPID, returning null", e);
		}
		return null;
	}

	/**
	 * This looks up a patient based on the given identifier in the client registry.
	 * If exactly 1 result is found, it is returned, otherwise, null is returned
	 */
	public Patient fetchPatientFromClientRegistry(String identifier) {
		if (!isEnabled()) {
			log.debug("Incomplete credentials supplied to connect to NIDA, skipping");
			return null;
		}
		try (CloseableHttpClient httpClient = getMpiClient()) {
			String url = getMpiEndpointUrl("/clientregistry/Patient", "identifier", identifier);
			HttpGet httpGet = new HttpGet(url);
			log.debug("Attempting to find patient " + identifier + " from NIDA");
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				int statusCode = response.getStatusLine().getStatusCode();
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}
				if (statusCode != 200) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}
				Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);
				if (bundle == null || bundle.getEntry() == null || bundle.getEntry().size() != 1) {
					throw new IllegalStateException("Unexpected bundle found: " + bundle);
				}
				org.hl7.fhir.r4.model.Patient fhirPatient = (org.hl7.fhir.r4.model.Patient) bundle.getEntry().get(0).getResource();
				return clientRegistryPatientTranslator.toOpenmrsType(fhirPatient);
			}
		} catch (Exception e) {
			log.debug("An error occurred trying to fetch patients from NIDA, returning null", e);
		}
		return null;
	}

	/**
	 * @return the http client to use to interact with the mpi, or null if no mpi credentials are configured
	 */
	public CloseableHttpClient getMpiClient() {
		String username = ConfigUtil.getProperty(RwandaEmrConstants.MPI_USERNAME_PROPERTY);
		String password = ConfigUtil.getProperty(RwandaEmrConstants.MPI_PASSWORD_PROPERTY);
		if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
			return HttpUtils.getHttpClient(username, password, true);
		}
		return null;
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
	 * Return the identifier system used to represent a particular identifier type in NIDA, given an identifier type uuid
	 */
	public String getIdentifierSystem(String patientIdentifierTypeUuid) {
		for (String system : integrationConfig.getIdentifierSystems().keySet()) {
			PatientIdentifierType type = integrationConfig.getIdentifierSystems().get(system);
			if (type != null && type.getUuid().equalsIgnoreCase(patientIdentifierTypeUuid)) {
				return system;
			}
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
