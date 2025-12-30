package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Patient;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class LabOrdersPageController {

    public void get(PageModel model, @RequestParam("patientId") Patient patient) {
        model.addAttribute("patient", patient);
    }
}
