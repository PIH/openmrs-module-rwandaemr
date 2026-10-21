package org.openmrs.module.rwandaemr.page.controller.labs;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The validated culture results of a patient: Gram stain, culture result and antibiogram
 */
public class CultureResultsPageController {

	public void get(PageModel model, @RequestParam("patient") Patient patient) {
		model.addAttribute("authorized", Context.hasPrivilege(PrivilegeConstants.GET_OBS));
		model.addAttribute("patient", patient);
	}
}
