package org.openmrs.module.rwandaemr.page.controller.labreports;

import org.openmrs.api.LocationService;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;

@Controller
public class TurnaroundTimePageController {

	public void get(PageModel model,
	                @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig,
	                @SpringBean("locationService") LocationService locationService) {
		model.addAttribute("labTestCategories", pihAppsConfig.getLabOrderConfig().getAvailableLabTestsByCategory());
		model.addAttribute("locations", locationService.getAllLocations(false));
	}
}
