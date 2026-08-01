package org.openmrs.module.rwandaemr.page.controller.labreports;

import org.openmrs.Concept;
import org.openmrs.api.LocationService;
import org.openmrs.module.pihapps.PihAppsConfig;
import org.openmrs.module.pihapps.orders.LabTestCategory;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class AggregatePageController {

	public void get(PageModel model,
	                @SpringBean("pihAppsConfig") PihAppsConfig pihAppsConfig,
	                @SpringBean("locationService") LocationService locationService) {
		model.addAttribute("labTestCategories", getResultCategories(pihAppsConfig));
		model.addAttribute("locations", locationService.getAllLocations(false));
	}

	private List<LabTestCategory> getResultCategories(PihAppsConfig pihAppsConfig) {
		List<LabTestCategory> ret = new ArrayList<LabTestCategory>();
		Concept resultCategories = pihAppsConfig.getLabOrderConfig().getLabResultCategoriesConceptSet();
		if (resultCategories == null || resultCategories.getSetMembers() == null || resultCategories.getSetMembers().isEmpty()) {
			return pihAppsConfig.getLabOrderConfig().getAvailableLabTestsByCategory();
		}
		for (Concept category : resultCategories.getSetMembers()) {
			LabTestCategory labTestCategory = new LabTestCategory();
			labTestCategory.setCategory(category);
			labTestCategory.setLabTests(getLeafConcepts(category, new HashSet<Integer>()));
			ret.add(labTestCategory);
		}
		return ret;
	}

	private List<Concept> getLeafConcepts(Concept concept, Set<Integer> visitedConceptIds) {
		List<Concept> ret = new ArrayList<Concept>();
		if (concept == null) {
			return ret;
		}
		if (!visitedConceptIds.add(concept.getConceptId())) {
			return ret;
		}
		if (concept.isSet() && concept.getSetMembers() != null && !concept.getSetMembers().isEmpty()) {
			for (Concept member : concept.getSetMembers()) {
				ret.addAll(getLeafConcepts(member, visitedConceptIds));
			}
		} else {
			ret.add(concept);
		}
		return ret;
	}
}
