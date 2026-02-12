package org.openmrs.module.rwandaemr.page.controller.patient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class IremboPayStatusSectionFragmentPageController {
    protected final Log log = LogFactory.getLog(IremboPayStatusSectionFragmentPageController.class);

    public void get(
        PageModel model, 
        UiUtils ui,
        @RequestParam(value = "billId", required = true) String billId
        ){
        model.addAttribute("message", "Message From controller!!!!");
        model.addAttribute("billId", billId);
    }
}
