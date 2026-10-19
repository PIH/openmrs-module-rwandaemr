package org.openmrs.module.rwandaemr.page.controller.labs;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.labs.LabResultValidationService;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Enters or validates the lab results of a patient on one page: the tests ordered in one encounter, with every specimen
 * (Lab ID) collected for them, and a section for each lab test category.  When the patient has tests from several
 * order encounters, the page offers a choice between them.
 */
public class LabResultsPageController {

	public static final String MODE_ENTER = "enter";
	public static final String MODE_VALIDATE = "validate";

	public void get(PageModel model,
					@RequestParam("patient") Patient patient,
					@RequestParam(value = "mode", required = false) String mode,
					@RequestParam(value = "orderEncounter", required = false) String orderEncounter) {
		boolean validate = MODE_VALIDATE.equals(mode);
		boolean authorized = validate ? Context.hasPrivilege(LabResultValidationService.PRIVILEGE_VALIDATE_RESULTS)
				: Context.hasPrivilege(PrivilegeConstants.ADD_ENCOUNTERS);
		model.addAttribute("authorized", authorized);
		model.addAttribute("patient", patient);
		model.addAttribute("mode", validate ? MODE_VALIDATE : MODE_ENTER);
		model.addAttribute("orderEncounter", orderEncounter);
	}
}
