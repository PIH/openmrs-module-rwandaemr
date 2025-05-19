/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr.radiology;

/**
 * This Modality enum is based on the list of Modality codes in the Medsynapse RIS HL7 Integration Guide
 */
public enum Modality {

    CR("Computed Radiography"),
    CT("Computed Tomography"),
    DX("Digital Radiography"),
    ES("Endoscopy"),
    GM("General Microscopy"),
    IO("Intra-oral Radiography"),
    MG("Mammography"),
    MR("Magnetic Resonance"),
    NM("Nuclear Medicine"),
    PX("Panoramic X-Ray"),
    RF("Radio Fluoroscopy"),
    OT("Other"),
    PT("Positron emission tomography"),
    SC("Secondary Capture Image"),
    SM("Slide Microscopy"),
    US("Ultrasound"),
    XA("X-Ray Angiography"),
    XC("External-camera Photography"),
    ECG("Electrocardiography")
    ;

    private final String description;

    Modality(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
