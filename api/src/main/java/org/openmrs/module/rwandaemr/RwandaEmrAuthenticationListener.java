/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Person;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.UserSessionListener;
import org.openmrs.api.ProviderService;
import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Implementation of UserSessionListener which ensures a provider account exists for every user that logs in
 */
@Component
public class RwandaEmrAuthenticationListener implements UserSessionListener {

	protected Log log = LogFactory.getLog(getClass());

	private final ProviderService providerService;

	public RwandaEmrAuthenticationListener(@Autowired ProviderService providerService) {
		this.providerService = providerService;
	}

	@Override
	public void loggedInOrOut(User user, Event event, Status status) {
		if (event == Event.LOGIN && status == Status.SUCCESS) {
			log.debug(user + " logged in");
			try {
				Person person = user.getPerson();
				Collection<Provider> providers = providerService.getProvidersByPerson(person, false);
				log.debug("Found " + providers.size() + " providers for " + person);
				if (providers.isEmpty()) {
					Provider p = new Provider();
					p.setPerson(person);
					p.setIdentifier(person.getId().toString());
					p.setDescription("provider created automatically");
					Context.getProviderService().saveProvider(p);
					log.info("Created provider " + p);
				}
			} catch (Exception e) {
				log.warn("Error creating provider account for " + user, e);
			}
		}
	}
}
