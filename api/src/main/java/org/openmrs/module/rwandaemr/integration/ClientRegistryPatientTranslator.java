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

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.StringType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Translation layer between the FHIR Patient returned from the client registry and an OpenMRS patient
 */
@Component
public class ClientRegistryPatientTranslator {

	protected Log log = LogFactory.getLog(getClass());

	private final RwandaEmrConfig rwandaEmrConfig;
	private final IntegrationConfig integrationConfig;

	public ClientRegistryPatientTranslator(@Autowired RwandaEmrConfig rwandaEmrConfig, @Autowired IntegrationConfig integrationConfig) {
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.integrationConfig = integrationConfig;
	}

	public static final String EDUCATION_EXTENSION_URL = "https://fhir.hie.moh.gov.rw/fhir/StructureDefinition/extensions/patient-educational-level";
	public static final String RELIGION_EXTENSION_URL = "https://hl7.org/fhir/StructureDefinition/patient-religion";
	public static final String PROFESSION_EXTENSION_URL = "https://fhir.hie.moh.gov.rw/fhir/StructureDefinition/extensions/patient-profession";

	public Patient toPatient(@Nonnull ClientRegistryPatient crPatient) {
		Patient p = new Patient();

		if (crPatient.getPatient().hasIdentifier()) {
			for (Identifier identifier : crPatient.getPatient().getIdentifier()) {
				String value = identifier.getValue();
				if (StringUtils.isNotBlank(value)) {
					String system = identifier.getSystem();
					PatientIdentifierType identifierType = integrationConfig.getIdentifierSystems().get(system);
					if (identifierType != null) {
						PatientIdentifier pi = new PatientIdentifier();
						pi.setPatient(p);
						pi.setIdentifierType(identifierType);
						pi.setIdentifier(value);
						p.addIdentifier(pi);
					} else {
						log.debug("Not adding identifier of type: " + system);
					}
				}
			}
		}

		if (crPatient.getPatient().hasName()) {
			for (HumanName humanName : crPatient.getPatient().getName()) {
				PersonName name = new PersonName();
				name.setPerson(p);
				if (humanName.hasGiven()) {
					name.setGivenName(getGivenName(humanName));
				}
				if (humanName.hasFamily()) {
					name.setFamilyName(humanName.getFamily());
				}
				if (p.getNames().isEmpty()) {
					name.setPreferred(true);
				}
				p.addName(name);
			}
		}

		if (crPatient.getPatient().hasGender()) {
			if (crPatient.getPatient().getGender() == Enumerations.AdministrativeGender.MALE) {
				p.setGender("M");
			} else if (crPatient.getPatient().getGender() == Enumerations.AdministrativeGender.FEMALE) {
				p.setGender("F");
			} else {
				// TODO: Do we handle UNKNOWN or OTHER cases?  If so, do we do U and O like the fhir2 module?
				log.debug("Not adding gender of type: " + crPatient.getPatient().getGender());
			}
		}

		if (crPatient.getPatient().hasBirthDateElement()) {
			p.setBirthdate(crPatient.getPatient().getBirthDateElement().getValue());
			TemporalPrecisionEnum precision = crPatient.getPatient().getBirthDateElement().getPrecision();
			if (precision != null && precision != TemporalPrecisionEnum.DAY) {
				p.setBirthdateEstimated(true);
			}
		}

		if (crPatient.getPatient().hasDeceasedBooleanType()) {
			p.setDead(crPatient.getPatient().getDeceasedBooleanType().booleanValue());
		}

		if (crPatient.getPatient().hasDeceasedDateTimeType()) {
			p.setDead(true);
			p.setDeathDate(crPatient.getPatient().getDeceasedDateTimeType().getValue());
		}

		for (Address fhirAddress : crPatient.getPatient().getAddress()) {
			PersonAddress personAddress = new PersonAddress();
			personAddress.setPerson(p);
			personAddress.setCountry(fhirAddress.getCountry());
			personAddress.setStateProvince(fhirAddress.getState());
			personAddress.setCountyDistrict(fhirAddress.getDistrict());
			personAddress.setCityVillage(fhirAddress.getCity());
			if (fhirAddress.hasLine()) {
				for (int i = 0; i < fhirAddress.getLine().size(); i++) {
					StringType lineEntry = fhirAddress.getLine().get(i);
					if (i == 0) {
						String[] lineComponents = lineEntry.getValue().split("\\|");
						for (int j = 0; j < lineComponents.length; j++) {
							if (j == 0) {
								if (StringUtils.isNotBlank(lineComponents[j])) {
									personAddress.setAddress3(lineComponents[j]); // Cell, TODO is this right?
								}
							}
							else if (j == 1) {
								if (StringUtils.isNotBlank(lineComponents[j])) {
									personAddress.setAddress1(lineComponents[j]); // Umudugudu, TODO is this right?
								}
							}
							else {
								log.debug("Not adding address line 1, component " + j + " = " + lineComponents[j]);
							}
						}
					} else {
						log.debug("Not adding address line element # " + i + " = " + lineEntry.getValue());
					}
				}
			}

			// TODO: Which to set as preferred?  How to use FhirAddress type
			p.addAddress(personAddress);
		}

		if (crPatient.getPatient().hasTelecom()) {
			for (ContactPoint contactPoint : crPatient.getPatient().getTelecom()) {
				if (contactPoint.hasValue()) {
					// TODO: Ignoring system and use properties, assume these indicate the patient's primary phone
					PersonAttributeType phoneNumber = rwandaEmrConfig.getTelephoneNumber();
					if (phoneNumber != null) {
						PersonAttribute personAttribute = new PersonAttribute();
						personAttribute.setPerson(p);
						personAttribute.setAttributeType(phoneNumber);
						personAttribute.setValue(contactPoint.getValue());
						p.addAttribute(personAttribute);
					} else {
						log.debug("Not adding phone number as no phone number attribute found");
					}
				}
			}
		}

		if (crPatient.getPatient().hasContact()) {
			for (org.hl7.fhir.r4.model.Patient.ContactComponent contactComponent : crPatient.getPatient().getContact()) {
				if (contactComponent.hasName()) {
					HumanName contactName = contactComponent.getName();
					if (contactName.hasGiven()) {
						PersonAttributeType contactNameType = null;
						if (contactName.getFamily().equals("MOTHER NAME")) {
							contactNameType = rwandaEmrConfig.getMothersName();
						}
						else if (contactName.getFamily().equals("FATHER NAME")) {
							contactNameType = rwandaEmrConfig.getFathersName();
						}
						if (contactNameType != null) {
							PersonAttribute personAttribute = new PersonAttribute();
							personAttribute.setPerson(p);
							personAttribute.setAttributeType(contactNameType);
							personAttribute.setValue(getGivenName(contactName));
							p.addAttribute(personAttribute);
						}
					}
				}
			}
		}

		if (crPatient.getPatient().hasExtension(EDUCATION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(crPatient.getPatient().getExtensionByUrl(EDUCATION_EXTENSION_URL));
			if (StringUtils.isNotBlank(value)) {
				PersonAttribute personAttribute = new PersonAttribute();
				personAttribute.setPerson(p);
				personAttribute.setAttributeType(rwandaEmrConfig.getEducationLevel());
				personAttribute.setValue(value);
				p.addAttribute(personAttribute);
			}
		}

		if (crPatient.getPatient().hasExtension(RELIGION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(crPatient.getPatient().getExtensionByUrl(RELIGION_EXTENSION_URL));
			if (StringUtils.isNotBlank(value)) {
				PersonAttribute personAttribute = new PersonAttribute();
				personAttribute.setPerson(p);
				personAttribute.setAttributeType(rwandaEmrConfig.getReligion());
				personAttribute.setValue(value);
				p.addAttribute(personAttribute);
			}
		}

		if (crPatient.getPatient().hasExtension(PROFESSION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(crPatient.getPatient().getExtensionByUrl(PROFESSION_EXTENSION_URL));
			if (StringUtils.isNotBlank(value)) {
				PersonAttribute personAttribute = new PersonAttribute();
				personAttribute.setPerson(p);
				personAttribute.setAttributeType(rwandaEmrConfig.getProfession());
				personAttribute.setValue(value);
				p.addAttribute(personAttribute);
			}
		}

		return p;
	}

	/**
	 * Updates a client registry patient with data from an OpenMRS patient
	 */
	public void updateClientRegistryPatient(@NotNull ClientRegistryPatient crPatient, @Nonnull Patient p) {

		// Add new identifiers, do not modify any existing identifiers
		for (PatientIdentifier pi : p.getActiveIdentifiers()) {
			String system = integrationConfig.getIdentifierSystem(pi.getIdentifierType());
			if (system != null) {
				String existingValue = crPatient.getIdentifierValue(system);
				if (StringUtils.isBlank(existingValue)) {
					Identifier identifier = new Identifier();
					identifier.setSystem(system);
					identifier.setValue(pi.getIdentifier());
					crPatient.getPatient().addIdentifier(identifier);
				}
			}
		}

		// Update name if available
		HumanName name = crPatient.getName();
		if (name == null) {
			name = new HumanName();
			crPatient.getPatient().addName(name);
		}
		List<StringType> givenNames = new ArrayList<>();
		if (StringUtils.isNotBlank(p.getGivenName())) {
			givenNames.add(new StringType(p.getGivenName()));
		}
		if (StringUtils.isNotBlank(p.getMiddleName())) {
			givenNames.add(new StringType(p.getMiddleName()));
		}
		if (!givenNames.isEmpty()) {
			name.setGiven(givenNames);
		}
		if (StringUtils.isNotBlank(p.getFamilyName())) {
			name.setFamily(p.getFamilyName());
		}

		// Gender
		if ("m".equalsIgnoreCase(p.getGender())) {
			crPatient.getPatient().setGender(Enumerations.AdministrativeGender.MALE);
		}
		else if ("f".equalsIgnoreCase(p.getGender())) {
			crPatient.getPatient().setGender(Enumerations.AdministrativeGender.FEMALE);
		}

		// Birthdate
		if (p.getBirthdate() != null) {
			crPatient.getPatient().setBirthDate(p.getBirthdate());
			if (BooleanUtils.isTrue(p.getBirthdateEstimated())) {
				crPatient.getPatient().getBirthDateElement().setPrecision(TemporalPrecisionEnum.YEAR);
			}
		}

		// Deceased
		if (BooleanUtils.isTrue(p.getDead())) {
			if (p.getDeathDate() != null) {
				crPatient.getPatient().setDeceased(new DateTimeType(p.getDeathDate()));
			} else {
				crPatient.getPatient().setDeceased(new BooleanType(true));
			}
		}

		// Address
		Address address = crPatient.getAddress();
		if (address == null) {
			address = new Address();
			crPatient.getPatient().addAddress(address);
		}
		if (p.getPersonAddress() != null) {
			PersonAddress a = p.getPersonAddress();
			address.setCountry(a.getCountry());
			address.setState(a.getStateProvince());
			address.setDistrict(a.getCountyDistrict());
			address.setCity(a.getCityVillage());
			if (StringUtils.isNotBlank(a.getAddress3())) {
				address.addLine(a.getAddress3());
			}
			if (StringUtils.isNotBlank(a.getAddress1())) {
				address.addLine(a.getAddress1());
			}
		}

		// Telephone Number
		PersonAttribute phoneNumber = p.getAttribute(rwandaEmrConfig.getTelephoneNumber());
		if (phoneNumber != null) {
			ContactPoint contactPoint = crPatient.getPhoneNumber();
			if (contactPoint == null) {
				contactPoint = new ContactPoint();
				crPatient.getPatient().addTelecom(contactPoint);
			}
			contactPoint.setValue(phoneNumber.getValue());
		}

		// Mothers and Fathers Name
		PersonAttribute mothersName = p.getAttribute(rwandaEmrConfig.getMothersName());
		if (mothersName != null) {
			org.hl7.fhir.r4.model.Patient.ContactComponent contactComponent = crPatient.getMothersName();
			if (contactComponent == null) {
				contactComponent = new org.hl7.fhir.r4.model.Patient.ContactComponent();
				crPatient.getPatient().addContact(contactComponent);
			}
			HumanName humanName;
			if (contactComponent.hasName()) {
				humanName = contactComponent.getName();
			}
			else {
				humanName = new HumanName();
			}
			humanName.setFamily("MOTHER NAME");
			humanName.setGiven(Collections.singletonList(new StringType(mothersName.getValue())));
			contactComponent.setName(humanName);
		}

		PersonAttribute fathersName = p.getAttribute(rwandaEmrConfig.getFathersName());
		if (fathersName != null) {
			org.hl7.fhir.r4.model.Patient.ContactComponent contactComponent = crPatient.getFathersName();
			if (contactComponent == null) {
				contactComponent = new org.hl7.fhir.r4.model.Patient.ContactComponent();
				crPatient.getPatient().addContact(contactComponent);
			}
			HumanName humanName;
			if (contactComponent.hasName()) {
				humanName = contactComponent.getName();
			}
			else {
				humanName = new HumanName();
			}
			humanName.setFamily("FATHER NAME");
			humanName.setGiven(Collections.singletonList(new StringType(fathersName.getValue())));
			contactComponent.setName(humanName);
		}

		// Education, Religion, Profession
		// TODO: Determine how to best handle these
	}

	// Returns a given name as a string
	private String getGivenName(HumanName humanName) {
		List<String> l = new ArrayList<>();
		if (humanName.hasGiven()) {
			for (StringType st : humanName.getGiven()) {
				if (st.hasValue()) {
					l.add(st.getValue());
				}
			}
		}
		return String.join(" ", l);
	}

	private String getExtensionCodedValue(Extension extension) {
		if (extension.getValue() != null && extension.getValue() instanceof CodeableConcept) {
			CodeableConcept concept = ((CodeableConcept) extension.getValue());
			if (concept != null && concept.hasCoding()) {
				return concept.getCodingFirstRep().getCode();
			}
		}
		return null;
	}
}
