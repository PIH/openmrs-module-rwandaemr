package org.openmrs.module.rwandaemr.page.controller.labs;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The reported bacteriology results of a patient, for the clinicians: for each exam, the wet mount, Gram stain, Indian
 * ink and culture, and a table of the antimicrobial susceptibility of the organisms isolated
 */
public class BacteriologyResultsPageController {

	public void get(PageModel model, @RequestParam("patient") Patient patient) {
		model.addAttribute("authorized", Context.hasPrivilege(PrivilegeConstants.GET_OBS));
		model.addAttribute("patient", patient);
	}
}
