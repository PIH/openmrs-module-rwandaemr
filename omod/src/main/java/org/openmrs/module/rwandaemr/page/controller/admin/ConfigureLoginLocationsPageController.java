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

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls page which is used to set the user's current session location
 */
@Controller
public class ConfigureLoginLocationsPageController {

    public void get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                    @RequestParam(name = "systemType", required = false) String systemType,
                    @SpringBean("locationService") LocationService locationService,
                    @SpringBean LocationTagUtil locationTagUtil) {

        if (StringUtils.isBlank(systemType)) {
            systemType = locationTagUtil.getConfiguredSystemType();
        }
        model.addAttribute("systemType", systemType);
        model.addAttribute("locationTagUtil", locationTagUtil);
        model.addAttribute("allLocations", locationService.getAllLocations());
        model.addAttribute("rootLocations", locationService.getRootLocations(false));
        model.addAttribute("authenticatedUser", sessionContext.getCurrentUser());
    }

    public String post(PageModel model, UiUtils ui, UiSessionContext sessionContext,
                       @SpringBean RwandaEmrService rwandaEmrService,
                       @RequestParam(value = "singleLocation", required = false) Location singleLocation,
                       @RequestParam(value = "multiDepartmentVisitLocation", required = false) Location multiDepartmentVisitLocation,
                       @RequestParam(value = "multiDepartmentLoginLocations", required = false) List<Location> multiDepartmentLoginLocations,
                       @RequestParam(value = "multiFacilityVisitLocations", required = false) List<Location> multiFacilityVisitLocations,
                       @RequestParam(value = "multiFacilityLoginLocations", required = false) List<Location> multiFacilityLoginLocations,
                       @RequestParam("systemType") String systemType) {

        try {
            List<Location> visitLocations = null;
            List<Location> loginLocations = null;

            if (LocationTagUtil.SINGLE_LOCATION.equals(systemType)) {
                if (singleLocation == null) {
                    throw new IllegalArgumentException("You must specify a location");
                }
                visitLocations = Collections.singletonList(singleLocation);
                loginLocations = Collections.singletonList(singleLocation);
            }
            else if (LocationTagUtil.MULTI_DEPARTMENT.equals(systemType)) {
                if (multiDepartmentVisitLocation == null) {
                    throw new IllegalArgumentException("You must specify a single facility visit location");
                }
                if (multiDepartmentLoginLocations == null || multiDepartmentLoginLocations.isEmpty()) {
                    throw new IllegalArgumentException("You must specify at least one login location");
                }
                for (Location location : multiDepartmentLoginLocations) {
                    if (location.getParentLocation() == null || !location.getParentLocation().equals(multiDepartmentVisitLocation)) {
                        throw new IllegalArgumentException("You can only configure login locations that are children of the selected visit location");
                    }
                }
                visitLocations = Collections.singletonList(multiDepartmentVisitLocation);
                loginLocations = multiDepartmentLoginLocations;
            }
            else if (LocationTagUtil.MULTI_FACILITY.equals(systemType)) {
                if (multiFacilityVisitLocations == null || multiFacilityVisitLocations.isEmpty()) {
                    throw new IllegalArgumentException("You must specify at least one facility visit location");
                }
                if (multiFacilityLoginLocations == null || multiFacilityLoginLocations.isEmpty()) {
                    throw new IllegalArgumentException("You must specify at least one login location");
                }
                visitLocations = multiFacilityVisitLocations;
                loginLocations = multiFacilityLoginLocations;
            }

            if (visitLocations != null && loginLocations != null) {
                //rwandaEmrService.updateVisitAndLoginLocations(visitLocations, loginLocations);

                Location sessionLocation = sessionContext.getSessionLocation();
                if (!loginLocations.contains(sessionLocation)) {
                    sessionContext.getSession().removeAttribute(UiSessionContext.LOCATION_SESSION_ATTRIBUTE);
                    return "redirect:/";
                }
            }
            else {
                throw new IllegalArgumentException("You must specify a valid set of visit and login locations");
            }
        }
        catch (Exception e) {
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, e.getMessage());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("systemType", systemType);
        return "redirect:" + ui.pageLink("rwandaemr", "admin/configureLoginLocations", params);
    }
}
