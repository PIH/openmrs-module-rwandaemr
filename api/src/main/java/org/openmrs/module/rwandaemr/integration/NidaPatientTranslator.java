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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of MpiProvider that connects to the Rwanda NIDA
 */
@Component
public class NidaPatientTranslator {

	protected Log log = LogFactory.getLog(getClass());

	private final RwandaEmrConfig rwandaEmrConfig;
	private Map<String, PatientIdentifierType> identifierSystems = null;

	public NidaPatientTranslator(
			@Autowired RwandaEmrConfig rwandaEmrConfig
	) {
		this.rwandaEmrConfig = rwandaEmrConfig;
	}

	public static final String EDUCATION_EXTENSION_URL = "https://fhir.hie.moh.gov.rw/fhir/StructureDefinition/extensions/patient-educational-level";
	public static final String RELIGION_EXTENSION_URL = "https://hl7.org/fhir/StructureDefinition/patient-religion";
	public static final String PROFESSION_EXTENSION_URL = "https://fhir.hie.moh.gov.rw/fhir/StructureDefinition/extensions/patient-profession";

	private synchronized Map<String, PatientIdentifierType> getIdentifierSystems() {
		if (identifierSystems == null) {
			identifierSystems = new HashMap<>();
			identifierSystems.put("NID", rwandaEmrConfig.getNationalId());
			identifierSystems.put("NID_APPLICATION_NUMBER", rwandaEmrConfig.getNidApplicationNumber());
			identifierSystems.put("NIN", rwandaEmrConfig.getNIN());
			identifierSystems.put("UPI", rwandaEmrConfig.getUPID());
			identifierSystems.put("PASSPORT", rwandaEmrConfig.getPassportNumber());
		}
		return identifierSystems;
	}

	public Patient toOpenmrsType(@Nonnull org.hl7.fhir.r4.model.Patient fhirPatient) {
		Patient p = new Patient();

		if (fhirPatient.hasIdentifier()) {
			for (Identifier identifier : fhirPatient.getIdentifier()) {
				String value = identifier.getValue();
				if (StringUtils.isNotBlank(value)) {
					String system = identifier.getSystem();
					PatientIdentifierType identifierType = getIdentifierSystems().get(system);
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

		if (fhirPatient.hasName()) {
			for (HumanName humanName : fhirPatient.getName()) {
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

		if (fhirPatient.hasGender()) {
			if (fhirPatient.getGender() == Enumerations.AdministrativeGender.MALE) {
				p.setGender("M");
			} else if (fhirPatient.getGender() == Enumerations.AdministrativeGender.FEMALE) {
				p.setGender("F");
			} else {
				// TODO: Do we handle UNKNOWN or OTHER cases?  If so, do we do U and O like the fhir2 module?
				log.debug("Not adding gender of type: " + fhirPatient.getGender());
			}
		}

		if (fhirPatient.hasBirthDateElement()) {
			p.setBirthdate(fhirPatient.getBirthDateElement().getValue());
			TemporalPrecisionEnum precision = fhirPatient.getBirthDateElement().getPrecision();
			if (precision != null && precision != TemporalPrecisionEnum.DAY) {
				p.setBirthdateEstimated(true);
			}
		}

		if (fhirPatient.hasDeceasedBooleanType()) {
			p.setDead(fhirPatient.getDeceasedBooleanType().booleanValue());
		}

		if (fhirPatient.hasDeceasedDateTimeType()) {
			p.setDead(true);
			p.setDeathDate(fhirPatient.getDeceasedDateTimeType().getValue());
		}

		for (Address fhirAddress : fhirPatient.getAddress()) {
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

		if (fhirPatient.hasTelecom()) {
			for (ContactPoint contactPoint : fhirPatient.getTelecom()) {
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

		if (fhirPatient.hasContact()) {
			for (org.hl7.fhir.r4.model.Patient.ContactComponent contactComponent : fhirPatient.getContact()) {
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

		if (fhirPatient.hasExtension(EDUCATION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(fhirPatient.getExtensionByUrl(EDUCATION_EXTENSION_URL));
			if (StringUtils.isNotBlank(value)) {
				PersonAttribute personAttribute = new PersonAttribute();
				personAttribute.setPerson(p);
				personAttribute.setAttributeType(rwandaEmrConfig.getEducationLevel());
				personAttribute.setValue(value);
				p.addAttribute(personAttribute);
			}
		}

		if (fhirPatient.hasExtension(RELIGION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(fhirPatient.getExtensionByUrl(RELIGION_EXTENSION_URL));
			if (StringUtils.isNotBlank(value)) {
				PersonAttribute personAttribute = new PersonAttribute();
				personAttribute.setPerson(p);
				personAttribute.setAttributeType(rwandaEmrConfig.getReligion());
				personAttribute.setValue(value);
				p.addAttribute(personAttribute);
			}
		}

		if (fhirPatient.hasExtension(PROFESSION_EXTENSION_URL)) {
			String value = getExtensionCodedValue(fhirPatient.getExtensionByUrl(PROFESSION_EXTENSION_URL));
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
