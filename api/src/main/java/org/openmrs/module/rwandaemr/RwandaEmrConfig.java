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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.EncounterType;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttributeType;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.module.initializer.api.InitializerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Config used by the Rwanda EMR module
 */
@Component
public class RwandaEmrConfig {

	protected Log log = LogFactory.getLog(getClass());

	private final PersonService personService;
	private final PatientService patientService;
	private final EncounterService encounterService;
	private final InitializerService initializerService;

	public RwandaEmrConfig(@Autowired PatientService patientService,
						   @Autowired PersonService personService,
						   @Autowired EncounterService encounterService,
						   @Autowired InitializerService initializerService) {
		this.patientService = patientService;
		this.personService = personService;
		this.encounterService = encounterService;
		this.initializerService = initializerService;
	}

	public PatientIdentifierType getPrimaryCareIdentifierType() {
		return getPatientIdentifierTypeByJsonKey("identifierType.primaryCareId.uuid");
	}

	public PatientIdentifierType getNationalId() {
		return getPatientIdentifierTypeByJsonKey("identifierType.nationalId.uuid");
	}

	public PatientIdentifierType getNidApplicationNumber() {
		return getPatientIdentifierTypeByJsonKey("identifierType.applicationNumber.uuid");
	}

	public PatientIdentifierType getUPID() {
		return getPatientIdentifierTypeByJsonKey("identifierType.upid.uuid");
	}

	public PatientIdentifierType getNIN() {
		return getPatientIdentifierTypeByJsonKey("identifierType.nin.uuid");
	}

	public PatientIdentifierType getPassportNumber() {
		return getPatientIdentifierTypeByJsonKey("identifierType.passportNumber.uuid");
	}

	public PersonAttributeType getTelephoneNumber() {
		return getPersonAttributeTypeByJsonKey("personAttribute.phoneNumber.uuid");
	}

	public PersonAttributeType getMothersName() {
		return getPersonAttributeTypeByJsonKey("personAttribute.mothersName.uuid");
	}

	public PersonAttributeType getFathersName() {
		return getPersonAttributeTypeByJsonKey("personAttribute.fathersName.uuid");
	}

	public PersonAttributeType getEducationLevel() {
		return getPersonAttributeTypeByJsonKey("personAttribute.educationLevel.uuid");
	}

	public PersonAttributeType getProfession() {
		return getPersonAttributeTypeByJsonKey("personAttribute.profession.uuid");
	}

	public PersonAttributeType getReligion() {
		return getPersonAttributeTypeByJsonKey("personAttribute.religion.uuid");
	}

	public EncounterType getRegistrationEncounterType() {
		return getEncounterTypeByJsonKey("encounterType.registration.uuid");
	}

	public PatientIdentifierType getPatientIdentifierTypeByJsonKey(String jsonKey) {
		String uuid = initializerService.getValueFromKey(jsonKey);
		if (StringUtils.isBlank(uuid)) {
			return null;
		}
		return patientService.getPatientIdentifierTypeByUuid(uuid);
	}

	public PersonAttributeType getPersonAttributeTypeByJsonKey(String jsonKey) {
		String uuid = initializerService.getValueFromKey(jsonKey);
		if (StringUtils.isBlank(uuid)) {
			return null;
		}
		return personService.getPersonAttributeTypeByUuid(uuid);
	}

	public EncounterType getEncounterTypeByJsonKey(String jsonKey) {
		String uuid = initializerService.getValueFromKey(jsonKey);
		if (StringUtils.isBlank(uuid)) {
			return null;
		}
		return encounterService.getEncounterTypeByUuid(uuid);
	}
}
