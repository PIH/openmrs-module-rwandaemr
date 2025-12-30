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

import lombok.Data;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

import javax.validation.constraints.NotNull;

/**
 * The representation of a Patient as returned from the clientregistry/Patient
 * This is essentially a Fhir Patient with additional convenience methods
 */
@Data
public class ClientRegistryPatient {

    private final Patient patient;

    public ClientRegistryPatient(@NotNull Patient patient) {
        this.patient = patient;
    }

    public String getIdentifierValue(String system) {
        if (patient.hasIdentifier() && system != null) {
            for (Identifier identifier : patient.getIdentifier()) {
                if (system.equalsIgnoreCase(identifier.getSystem())) {
                    return identifier.getValue();
                }
            }
        }
        return null;
    }

    public HumanName getName() {
        if (patient.hasName()) {
            return patient.getNameFirstRep();
        }
        return null;
    }

    public Address getAddress() {
        if (patient.hasAddress()) {
            return patient.getAddressFirstRep();
        }
        return null;
    }

    public ContactPoint getPhoneNumber() {
        if (patient.hasTelecom()) {
            return patient.getTelecomFirstRep();
        }
        return null;
    }

    public Patient.ContactComponent getMothersName() {
        if (patient.hasContact()) {
            for (Patient.ContactComponent contactComponent : patient.getContact()) {
                if (contactComponent.getName() != null && contactComponent.getName().getFamily().equalsIgnoreCase("MOTHER NAME")) {
                    return contactComponent;
                }
            }
        }
        return null;
    }

    public Patient.ContactComponent getFathersName() {
        if (patient.hasContact()) {
            for (Patient.ContactComponent contactComponent : patient.getContact()) {
                if (contactComponent.getName() != null && contactComponent.getName().getFamily().equalsIgnoreCase("FATHER NAME")) {
                    return contactComponent;
                }
            }
        }
        return null;
    }
}