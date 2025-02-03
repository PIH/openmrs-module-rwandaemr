/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;

import javax.transaction.Transactional;
import java.util.List;

/**
 * Service methods
 */
@Transactional
public interface RwandaEmrService extends OpenmrsService {

	List<String> triggerSyncForPatient(Patient patient);

	void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations);
}
