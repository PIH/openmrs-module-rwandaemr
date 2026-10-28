package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueService;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pathology requests of a patient, with where each is in the lab, and the way to request a new one with the pathology
 * request form, as the radiology orders page does for radiology
 */
public class PathologyOrdersPageController {

	public void get(PageModel model,
					@InjectBeans PatientDomainWrapper patientDomainWrapper,
					@RequestParam(value = "patientId") Patient patient,
					@SpringBean PathologyQueueService pathologyQueueService) {

		patientDomainWrapper.setPatient(patient);
		model.addAttribute("patient", patientDomainWrapper);
		model.addAttribute("requestForm", PathologyConfig.REQUEST_FORM_RESOURCE);
		model.addAttribute("canSeeQueue", Context.hasPrivilege(PathologyConfig.PRIVILEGE_VIEW_QUEUE));

		// Most recent first
		List<PathologyQueueRow> requests = new ArrayList<PathologyQueueRow>(pathologyQueueService.getRequests(patient));
		Collections.reverse(requests);
		model.addAttribute("requests", requests);
	}
}
