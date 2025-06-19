/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config used by the Rwanda EMR module
 */
@Component
public class RadiologyConfig {

	protected static Log log = LogFactory.getLog(RadiologyConfig.class);

	public static final String PROPERTY_INCOMING_HL7_PORT = "rwandaemr.radiology.incomingHL7Port";
	public static final String PROPERTY_OUTGOING_HL7_HOST = "rwandaemr.radiology.outgoingHL7Host";
	public static final String PROPERTY_OUTGOING_HL7_PORT = "rwandaemr.radiology.outgoingHL7Port";

	public static final String GP_SUPPORTED_MODALITIES = "rwandaemr.radiology.supportedModalities";
	public static final String GP_ORDERABLES_PREFIX = "rwandaemr.radiology.orderables.";

	public static final String RADIOLOGY_REQUEST_ENCOUNTER_UUID = "02965343-bbe7-4c3c-83c5-c3d084e715b7";
	public static final String RADIOLOGY_STUDY_ENCOUNTER_UUID = "7d2fdd23-2cfc-11f0-bad6-56aa92787bc1";
	public static final String RADIOLOGY_REPORT_ENCOUNTER_UUID = "207c2518-4ba8-11f0-a5b6-d21e7ba45d33";

	public static final String RADIOLOGY_TECHNICIAN_ENCOUNTER_ROLE_UUID = "8f4d96e2-c97c-4285-9319-e56b9ba6029c";
	public static final String PRINCIPAL_RESULTS_INTERPRETER_ENCOUNTER_ROLE_UUID = "08f73be2-9452-44b5-801b-bdf7418c2f71";

	private final AdministrationService administrationService;
	private final ConceptService conceptService;
	private final EncounterService encounterService;

	public RadiologyConfig(@Autowired @Qualifier("adminService") AdministrationService administrationService,
						   @Autowired ConceptService conceptService,
						   @Autowired EncounterService encounterService
                           ) {
		this.administrationService = administrationService;
		this.conceptService = conceptService;
		this.encounterService = encounterService;
	}

	public static Integer getIncomingHl7Port() {
		String property = ConfigUtil.getProperty(PROPERTY_INCOMING_HL7_PORT, null);
		if (StringUtils.isNotBlank(property)) {
			try {
				return Integer.parseInt(property);
			}
			catch (NumberFormatException e) {
				log.error("Invalid configuration for " + PROPERTY_INCOMING_HL7_PORT + ": " + property);
			}
		}
		return null;
	}

	public static String getOutgoingHl7Host() {
		return ConfigUtil.getProperty(PROPERTY_OUTGOING_HL7_HOST, null);
	}

	public static Integer getOutgoingHl7Port() {
		String property = ConfigUtil.getProperty(PROPERTY_OUTGOING_HL7_PORT, null);
		if (StringUtils.isNotBlank(property)) {
			try {
				return Integer.parseInt(property);
			}
			catch (NumberFormatException e) {
				log.error("Invalid configuration for " + PROPERTY_OUTGOING_HL7_PORT + ": " + property);
			}
		}
		return null;
	}

	public Map<Modality, Concept> getModalityConceptSets() {
		Map<Modality, Concept> ret = new LinkedHashMap<>();
		String supportedModalities = administrationService.getGlobalProperty(GP_SUPPORTED_MODALITIES, "");
		if (StringUtils.isNotBlank(supportedModalities)) {
			String[] modalities = supportedModalities.split(",");
			for (String modality : modalities) {
				modality = modality.trim().toUpperCase();
				Modality modalityEnum = Modality.valueOf(modality);
				Concept conceptSet = null;
				String modalitySet = administrationService.getGlobalProperty(GP_ORDERABLES_PREFIX + modality, "");
				if (StringUtils.isNotBlank(modalitySet)) {
					conceptSet = conceptService.getConceptByReference(modalitySet);
				}
				ret.put(modalityEnum, conceptSet);
			}
		}
		return ret;
	}

	public Map<Concept, Modality> getOrderables(Map<Modality, Concept> modalityConceptSets) {
		Map<Concept, Modality> ret = new LinkedHashMap<>();
		for (Modality modality : modalityConceptSets.keySet()) {
			Concept conceptSet = modalityConceptSets.get(modality);
			if (conceptSet != null) {
				for (Concept setMember : conceptSet.getSetMembers()) {
					if (ret.containsKey(setMember)) {
						throw new IllegalStateException(setMember + " cannot be present in multiple modalities");
					}
					ret.put(setMember, modality);
				}
			}
		}
		return ret;
	}

	public EncounterType getRadiologyStudyEncounterType() {
		return encounterService.getEncounterTypeByUuid(RADIOLOGY_STUDY_ENCOUNTER_UUID);
	}

	public EncounterType getRadiologyReportEncounterType() {
		return encounterService.getEncounterTypeByUuid(RADIOLOGY_REPORT_ENCOUNTER_UUID);
	}

	public EncounterRole getRadiologyTechnicianEncounterRole() {
		return encounterService.getEncounterRoleByUuid(RADIOLOGY_TECHNICIAN_ENCOUNTER_ROLE_UUID);
	}

	public EncounterRole getPrincipalResultsInterpreterEncounterRole() {
		return encounterService.getEncounterRoleByUuid(PRINCIPAL_RESULTS_INTERPRETER_ENCOUNTER_ROLE_UUID);
	}

	public Concept getRadiologyStudyConstruct() {
		return getEmrApiMappedConcept("Radiology study construct");
	}

	public Concept getRadiologyReportConstruct() {
		return getEmrApiMappedConcept("Radiology report construct");
	}

	public Concept getRadiologyProcedurePerformed() {
		return getEmrApiMappedConcept("Radiology procedure performed");
	}

	public Concept getDateOfTest() {
		return getEmrApiMappedConcept("Date of test");
	}

	public Concept getRadiologyAccessionNumber() {
		return getEmrApiMappedConcept("Radiology accession number");
	}

	public Concept getRadiologyImagesAvailable() {
		return getEmrApiMappedConcept("Radiology images available");
	}

	public Concept getRadiologyReportType() {
		return getEmrApiMappedConcept("Type of radiology report");
	}

	public Concept getRadiologyReportComments() {
		return getEmrApiMappedConcept("Radiology report comments");
	}

	public Concept getPreliminaryStatusConcept() {
		return getEmrApiMappedConcept("Radiology report preliminary");
	}

	public Concept getFinalStatusConcept() {
		return getEmrApiMappedConcept("Radiology report final");
	}

	public Concept getCorrectionStatusConcept() {
		return getEmrApiMappedConcept("Radiology report correction");
	}

	private Concept getEmrApiMappedConcept(String code) {
		return conceptService.getConceptByReference("org.openmrs.module.emrapi:" + code);
	}
}
