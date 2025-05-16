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
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
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

	protected Log log = LogFactory.getLog(getClass());

	public static final String GP_SUPPORTED_MODALITIES = "rwandaemr.radiology.supportedModalities";
	public static final String GP_ORDERABLES_PREFIX = "rwandaemr.radiology.orderables.";

	private final AdministrationService administrationService;
	private final ConceptService conceptService;

	public RadiologyConfig(@Autowired @Qualifier("adminService") AdministrationService administrationService,
						   @Autowired ConceptService conceptService
                           ) {
		this.administrationService = administrationService;
		this.conceptService = conceptService;
	}

	public Map<Modality, Concept> getSupportedRadiologyOrderablesByModality() {
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

	public Map<Concept, Modality> getRadiologyOrderables() {
		Map<Concept, Modality> ret = new LinkedHashMap<>();
		Map<Modality, Concept> modalities = getSupportedRadiologyOrderablesByModality();
		for (Modality modality : modalities.keySet()) {
			Concept conceptSet = modalities.get(modality);
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
}
