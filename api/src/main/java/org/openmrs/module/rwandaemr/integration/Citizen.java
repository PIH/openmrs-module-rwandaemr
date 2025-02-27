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

/**
 * Represents the citizen component of the response from the citizen endpoints in the HIE
 */
@Data
public class Citizen {
    private String fosaid;
    private String documentType; // NID, PASSPORT
    private String documentNumber;
    private String issueNumber;
    private String dateOfIssue;  // dd/MM/yyyy
    private String dateOfExpiry; // dd/MM/yyyy
    private String placeOfIssue;
    private String applicationNumber;
    private String nin;
    private String nid;
    private String upi;
    private String passportNumber;
    private String surName;
    private String postNames;
    private String fatherName;
    private String motherName;
    private String sex;  // MALE or
    private String dateOfBirth; // dd/MM/yyyy
    private String placeOfBirth;
    private String countryOfBirth;
    private String birthCountry;
    private String villageId;
    private String domicileCountry;
    private String domicileDistrict;
    private String domicileProvince;
    private String domicileSector;
    private String domicileCell;
    private String domicileVillage;
    private String civilStatus; // S
    private String maritalStatus;  // SINGLE
    private String spouse;
    private String photo; // Path to file
    private String citizenStatus;
    private String nationality;
    private String applicantType;
}