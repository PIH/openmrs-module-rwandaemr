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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Supports connections to and operations with the clientregistry/Patient endpoint
 */
@Component("clientRegistryPatientProvider")
public class ClientRegistryPatientProvider {

	protected Log log = LogFactory.getLog(getClass());

	private final FhirContext fhirContext;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final IntegrationConfig integrationConfig;
	private final ClientRegistryPatientTranslator clientRegistryPatientTranslator;

	public ClientRegistryPatientProvider(
			@Autowired @Qualifier("fhirR4") FhirContext fhirContext,
			@Autowired RwandaEmrConfig rwandaEmrConfig,
			@Autowired IntegrationConfig integrationConfig,
			@Autowired ClientRegistryPatientTranslator clientRegistryPatientTranslator
	) {
		this.fhirContext = fhirContext;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.integrationConfig = integrationConfig;
		this.clientRegistryPatientTranslator = clientRegistryPatientTranslator;
	}

	/**
	 * This looks up a patient based on the given identifier in the client registry.
	 * If exactly 1 result is found, it is returned, otherwise, null is returned
	 */
	public ClientRegistryPatient fetchPatientFromClientRegistry(String identifier, String identifierSystem) {
		if (!integrationConfig.isHieEnabled()) {
			throw new IllegalStateException("The HIE connection is not enabled on this server");
		}
		try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
			String url = integrationConfig.getHieEndpointUrl("/clientregistry/Patient", "identifier", identifier);
			HttpGet httpGet = new HttpGet(url);
			log.debug("Attempting to find patient " + identifier + " from client registry");
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				int statusCode = response.getStatusLine().getStatusCode();
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}
				log.debug("Data: " + data);
				if (statusCode != 200) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}

				Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);
				List<ClientRegistryPatient> candidates = new ArrayList<>();
				if (bundle != null && bundle.hasEntry()) {
					for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
						org.hl7.fhir.r4.model.Patient fhirPatient = (org.hl7.fhir.r4.model.Patient) entry.getResource();
						if (fhirPatient != null) {
							ClientRegistryPatient crPatient = new ClientRegistryPatient(fhirPatient);
							if (identifier.equalsIgnoreCase(crPatient.getIdentifierValue(identifierSystem))) {
								candidates.add(crPatient);
							}
						}
					}
				}

				if (candidates.isEmpty()) {
					log.debug("No patients found for " + identifier);
					return null;
				}
				if (candidates.size() != 1) {
					log.warn("Unable to uniquely retrieve patient with identifier " + identifier + ". " + candidates.size() + " found.");
					return null;
				}
				return candidates.get(0);
			}
		} catch (Exception e) {
			log.debug("An error occurred trying to fetch patients from client registry, returning null", e);
		}
		return null;
	}

	/**
	 * This updates the patient record in the client registry based on the given patient record
	 * If the patient does not have a UPID patient identifier, then this returns without performing any action
	 */
	public void updatePatientInClientRegistry(Patient patient) throws Exception {
		if (!integrationConfig.isHieEnabled()) {
			log.debug("Incomplete credentials supplied to connect to client registry, skipping");
			return;
		}
		log.debug("Updating patient in client registry.  Patient uuid: " + patient.getUuid());

		// All patients must first have a UPID before they can be added to the client registry
		PatientIdentifier upid = patient.getPatientIdentifier(rwandaEmrConfig.getUPID());
		if (upid == null) {
			log.debug("Patient requires a upid to update in client registry, not updating: " + patient.getUuid());
			return;
		}
		log.debug("Patient has a upid: " + upid);

		// First see if there is an existing record in the client registry that should be updated, or create new
		ClientRegistryPatient crPatient = fetchPatientFromClientRegistry(upid.getIdentifier(), IntegrationConfig.IDENTIFIER_SYSTEM_UPI);
		if (crPatient == null) {
			crPatient = new ClientRegistryPatient(new org.hl7.fhir.r4.model.Patient());
			crPatient.getPatient().setId(upid.getIdentifier());
			log.debug("Patient not found in client registry.  Creating new FHIR patient");
		}
		else {
			log.debug("Patient found in client registry, updating existing FHIR patient");
		}

		// Update relevant properties with those from the patient
		clientRegistryPatientTranslator.updateClientRegistryPatient(crPatient, patient);

		String endpoint = "/clientregistry/Patient";
		String postBody = fhirContext.newJsonParser().encodeResourceToString(crPatient.getPatient());

		// Post data to the client registry
		try (CloseableHttpClient httpClient = HttpUtils.getHieClient()) {
			HttpPost httpPost = new HttpPost(integrationConfig.getHieEndpointUrl(endpoint));
			log.debug("POSTING " + endpoint + ": " + postBody);
			httpPost.setEntity(new StringEntity(postBody));
			httpPost.setHeader("Content-Type", "application/json");
			try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
				int statusCode = response.getStatusLine().getStatusCode();
				HttpEntity entity = response.getEntity();
				String data = "";
				try {
					data = EntityUtils.toString(entity);
				} catch (Exception ignored) {
				}
				log.debug("Data: " + data);
				if (statusCode != 201) {
					throw new IllegalStateException("Http Status Code: " + statusCode + "; Response: " + data);
				}
				log.debug("Submission successful");
			}
		}
	}
}
