package org.openmrs.module.rwandaemr.page.controller.patient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;
import org.openmrs.ui.framework.UiUtils;

public class HieObservationsSectionFragmentPageController {
    protected final Log log = LogFactory.getLog(getClass());
    public void get(
        PageModel model, 
        UiUtils ui,
        @RequestParam(value = "encounterUuid", required = true) String encounterUuid
        ) {
            model.addAttribute("message", "Message From controller!!!!");
            model.addAttribute("uuid", encounterUuid);
    }
}
