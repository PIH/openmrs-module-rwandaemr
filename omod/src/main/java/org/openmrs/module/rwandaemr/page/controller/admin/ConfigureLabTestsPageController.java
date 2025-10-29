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
package org.openmrs.module.rwandaemr.page.controller.admin;

import org.openmrs.Concept;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls page which is used to configure the lab tests that should be available for ordering on the current systems
 */
@Controller
public class ConfigureLabTestsPageController {

    public void get(PageModel model,
                    @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig) {

        model.addAttribute("pihAppsConfig", pihAppsConfig);
        model.addAttribute("labOrderConfig", pihAppsConfig.getLabOrderConfig());
        model.addAttribute("labSet", pihAppsConfig.getLabOrderConfig().getLabOrderablesConceptSet());
        model.addAttribute("labTestsByCategory", pihAppsConfig.getLabOrderConfig().getAvailableLabTestsByCategory());
    }

    public String post(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                       @SpringBean("adminService") AdministrationService administrationService,
                       @RequestParam(value = "labTests") List<Concept> labTests) {

        try {
            List<String> conceptIds = new ArrayList<>();
            if (labTests != null) {
                for (Concept concept : labTests) {
                    conceptIds.add(concept.getId().toString());
                }
            }
            String gpVal = String.join(",", conceptIds);
            administrationService.setGlobalProperty("laboratorymanagement.currentLabRequestFormConceptIDs", gpVal);
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, "Configuration Saved");
        }
        catch (Exception e) {
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
        }

        return "redirect:" + ui.pageLink("rwandaemr", "admin/configureLabTests");
    }
}
