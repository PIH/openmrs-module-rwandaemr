package org.openmrs.module.rwandaemr.page.controller.pathology;

import org.openmrs.Concept;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.billing.PaymentStatus;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pathology queue: the pathology requests made in a date range, with where each is in the lab and whether it is paid.
 * The table is loaded and refreshed from {@link org.openmrs.module.rwandaemr.rest.PathologyQueueRestController}.
 */
@Controller
public class PathologyQueuePageController {

	public Object get(PageModel model,
					  @SpringBean PathologyQueueService pathologyQueueService,
					  @SpringBean PathologyConfig pathologyConfig,
					  @RequestParam(value = "startDate", required = false) String startDate,
					  @RequestParam(value = "endDate", required = false) String endDate,
					  @RequestParam(value = "status", required = false) String status,
					  @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
					  @RequestParam(value = "test", required = false) String test,
					  @RequestParam(value = "query", required = false) String query,
					  @RequestParam(value = "inLab", required = false) String inLab,
					  @RequestParam(value = "urgent", required = false) String urgent,
					  @RequestParam(value = "export", required = false) String export) {

		boolean authorized = Context.hasPrivilege(PathologyConfig.PRIVILEGE_VIEW_QUEUE);
		model.addAttribute("authorized", authorized);
		if (!authorized) {
			return null;
		}

		PathologyQueueRequest request = new PathologyQueueRequest(startDate, endDate, status, paymentStatus, test, query,
				inLab);
		request.setUrgentOnly("YES".equalsIgnoreCase(urgent));
		if ("excel".equalsIgnoreCase(export) && request.isValid()) {
			List<PathologyQueueRow> rows = request.filter(pathologyQueueService.getQueue(request.getFromDate(),
					request.getToDate(), request.isIncludeCasesInLab()));
			String filename = "pathology-queue-" + request.getStartDate() + "-to-" + request.getEndDate() + ".xls";
			return new FileDownload(filename, PathologyQueueExcel.CONTENT_TYPE, PathologyQueueExcel.create(rows));
		}

		List<String> tests = new ArrayList<String>();
		for (Concept concept : pathologyConfig.getOrderables()) {
			tests.add(concept.getFullySpecifiedName(Locale.ENGLISH) != null ?
					concept.getFullySpecifiedName(Locale.ENGLISH).getName() : concept.getDisplayString());
		}
		model.addAttribute("queueRequest", request);
		model.addAttribute("statuses", PathologyStatus.values());
		model.addAttribute("paymentStatuses", PaymentStatus.values());
		model.addAttribute("tests", tests);
		return null;
	}
}
