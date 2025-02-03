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
package org.openmrs.module.rwandaemr.page.controller;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.LocationTagWebUtil;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Controls page which is used to set the user's current session location
 */
@Controller
public class LoginLocationPageController {

    public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext, HttpServletResponse response,
                    @SpringBean LocationTagUtil locationTagUtil) {

        model.addAttribute("locationTagUtil", locationTagUtil);
        model.addAttribute("authenticatedUser", sessionContext.getCurrentUser());

        if (!locationTagUtil.isLocationSetupRequired()) {
            List<Location> loginLocations = locationTagUtil.getValidLoginLocations();
            if (loginLocations.size() == 1) {
                return post(sessionContext, response, loginLocations.get(0));
            }
        }
        return "loginLocation";
    }

    public String post(UiSessionContext sessionContext, HttpServletResponse response,
                       @RequestParam("sessionLocation") Location sessionLocation) {
        LocationTagWebUtil.setLoginLocation(sessionLocation, sessionContext, response);
        return "redirect:/";
    }
}
