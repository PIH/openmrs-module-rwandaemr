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
package org.openmrs.module.rwandaemr.web;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class TriggerSyncController {

	@Autowired
	PatientService patientService;

    @Autowired
    RwandaEmrService rwandaEmrService;

    @RequestMapping(value = "/module/rwandaemr/triggerSync.form")
    public void triggerSyncList(HttpServletResponse response,
                                @RequestParam(required=false, value="patientId") String patientId) throws Exception {

        Patient patient = null;
        Map<String, Object> data = new HashMap<String, Object>();
        List<String> messages = new ArrayList<String>();
        data.put("messages", messages);
        if (StringUtils.isNotBlank(patientId)) {
            try {
                patient = patientService.getPatientByUuid(patientId);
                if (patient == null) {
                    patient = patientService.getPatient(Integer.parseInt(patientId));
                }
            } catch (Exception e) {
                data.put("error", e.getMessage());
            }
            if (patient == null) {
                messages.add("Cannot find patient by id or uuid: " + patientId);
            }
            try {
                List<String> log = rwandaEmrService.triggerSyncForPatient(patient);
                messages.addAll(log);
            }
            catch (Exception e) {
                data.put("error", e.getMessage());
            }
        }

        response.setContentType("text/json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), data);
    }
}
