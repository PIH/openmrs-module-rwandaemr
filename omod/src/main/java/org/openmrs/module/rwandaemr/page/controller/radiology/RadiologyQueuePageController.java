package org.openmrs.module.rwandaemr.page.controller.radiology;

import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Radiology queue: the radiology studies requested in a date range with their PACS and payment status.  The table is
 * loaded and refreshed from {@link org.openmrs.module.rwandaemr.rest.RadiologyQueueRestController}.
 */
@Controller
public class RadiologyQueuePageController {

	public Object get(PageModel model,
					  @SpringBean RadiologyQueueService radiologyQueueService,
					  @SpringBean("radiologyConfig") RadiologyConfig radiologyConfig,
					  @RequestParam(value = "startDate", required = false) String startDate,
					  @RequestParam(value = "endDate", required = false) String endDate,
					  @RequestParam(value = "modality", required = false) String modality,
					  @RequestParam(value = "pacsStatus", required = false) String pacsStatus,
					  @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
					  @RequestParam(value = "processed", required = false) String processed,
					  @RequestParam(value = "query", required = false) String query,
					  @RequestParam(value = "export", required = false) String export) {

		boolean authorized = Context.hasPrivilege(RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE);
		model.addAttribute("authorized", authorized);
		if (!authorized) {
			return null;
		}

		RadiologyQueueRequest request = new RadiologyQueueRequest(startDate, endDate, modality, pacsStatus,
				paymentStatus, processed, query);
		if ("excel".equalsIgnoreCase(export) && request.isValid()) {
			List<RadiologyQueueRow> rows = request.filter(
					radiologyQueueService.getQueue(request.getFromDate(), request.getToDate()));
			String filename = "radiology-queue-" + request.getStartDate() + "-to-" + request.getEndDate() + ".xls";
			return new FileDownload(filename, RadiologyQueueExcel.CONTENT_TYPE, RadiologyQueueExcel.create(rows));
		}

		List<String> modalities = new ArrayList<String>();
		for (Modality m : radiologyConfig.getModalityConceptSets().keySet()) {
			modalities.add(m.name());
		}
		model.addAttribute("queueRequest", request);
		model.addAttribute("modalities", modalities);
		model.addAttribute("pacsStatuses", RadiologyQueueRow.PacsStatus.values());
		model.addAttribute("paymentStatuses", RadiologyQueueRow.PaymentStatus.values());
		return null;
	}
}
