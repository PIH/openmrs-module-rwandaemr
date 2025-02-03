package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Encounter;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

public class SimpleEncounterViewPageController {

    public void get(PageModel model, UiUtils ui,
                    @RequestParam(value = "encounter") Encounter encounter) throws IOException {

        model.put("encounter", encounter);
    }
}
