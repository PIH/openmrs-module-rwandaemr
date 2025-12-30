/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.resource.api.PageableResult;
import org.openmrs.module.webservices.rest.web.resource.api.SearchConfig;
import org.openmrs.module.webservices.rest.web.resource.impl.EmptySearchResult;
import org.openmrs.module.webservices.rest.web.resource.impl.NeedsPaging;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.search.openmrs1_8.PatientByIdentifierSearchHandler1_8;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RwandaEmrPatientSearchHandler extends PatientByIdentifierSearchHandler1_8 {

	protected final Log log = LogFactory.getLog(getClass());

	public static final String SEARCH_ID = "rwandaemrPatientSearch";

	private final PatientService patientService;

	public RwandaEmrPatientSearchHandler(@Autowired PatientService patientService) {
		this.patientService = patientService;
	}

	/**
	 * If multiple search handlers are found for a particular resource and the same supported parameters, the REST
	 * module will return the one whose id = "default".  We configure that here to ensure this search handler is used.
	 */
	@Override
	public SearchConfig getSearchConfig() {
		SearchConfig p = super.getSearchConfig();
		return new SearchConfig(SEARCH_ID, p.getSupportedResource(), p.getSupportedOpenmrsVersions(), p.getSearchQueries());
	}

	/**
	 * This has the same logic as the superclass, with the addition of searching by insurance number and phone number
	 * There are 2 possible parameters that will be passed to this:
	 *   - identifier = search with no white-space
	 *   - q = search with white-space
	 * This handles both the same way currently
	 */
	@Override
	public PageableResult search(RequestContext context) throws ResponseException {
		String searchString = context.getRequest().getParameter("identifier");
		if (StringUtils.isBlank(searchString)) {
			searchString = context.getRequest().getParameter("q");
		}
		if (StringUtils.isNotBlank(searchString)) {
			log.trace("Searching patients by: " + searchString);

			// The core patient search matches on identifier, name, and attributes based on core configuration(s)
			List<Patient> patients = patientService.getPatients(null, searchString, null, true);
			log.trace("Found " + patients.size() + " patients from core patient search");

			BillingService billingService = Context.getService(BillingService.class);
			InsurancePolicy policy = billingService.getInsurancePolicyByCardNo(searchString);
			if (policy != null) {
				patients.add(policy.getOwner());
				log.trace("Found " + patients.size() + " patients by insurance card number");
			}

			if (!patients.isEmpty()) {
				return new NeedsPaging<>(patients, context);
			}
		}

		log.trace("No patients found that match the search criteria");
		return new EmptySearchResult();
	}
	
}
