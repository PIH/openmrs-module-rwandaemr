/*
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

package org.openmrs.module.rwandaemr.fragment.controller.field;

import org.openmrs.Location;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentModel;

public class LocationFragmentController {

    public void controller(@SpringBean AdtService adtService,
                           UiSessionContext sessionContext,
                           FragmentModel model) {

        Location sessionLocation = sessionContext.getSessionLocation();
        model.addAttribute("sessionLocation", sessionLocation);
        Location visitLocation = null;
        if (sessionLocation != null) {
            visitLocation = adtService.getLocationThatSupportsVisits(sessionLocation);
        }
        model.addAttribute("visitLocationForSessionLocation", visitLocation);
    }

}
