package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.page.controller.pathology.PathologyQueueRequest;
import org.openmrs.module.rwandaemr.pathology.PathologyCase;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseService;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueService;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportText;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Data for the pathology queue page, which polls this to refresh its table, and the steps the lab records from it
 */
@Controller
public class PathologyQueueRestController {

	@Autowired
	PathologyQueueService pathologyQueueService;

	@Autowired
	PathologyCaseService pathologyCaseService;

	@Autowired
	EncounterService encounterService;

	private final Log log = LogFactory.getLog(getClass());

	@RequestMapping(value = "/rest/v1/rwandaemr/pathologyqueue", method = RequestMethod.GET)
	@ResponseBody
	public Object getQueue(@RequestParam(value = "startDate", required = false) String startDate,
						   @RequestParam(value = "endDate", required = false) String endDate,
						   @RequestParam(value = "inLab", required = false) String inLab,
						   @RequestParam(value = "diagnostics", required = false) String diagnostics) {
		checkPrivilege(PathologyConfig.PRIVILEGE_VIEW_QUEUE);
		PathologyQueueRequest request = new PathologyQueueRequest(startDate, endDate, null, null, null, null, inLab);
		SimpleObject ret = new SimpleObject();
		ret.put("startDate", request.getStartDate());
		ret.put("endDate", request.getEndDate());
		ret.put("generatedAt", new Date().getTime());
		if (!request.isValid()) {
			ret.put("error", request.getError());
			ret.put("rows", new ArrayList<Object>());
			return ret;
		}
		if ("true".equalsIgnoreCase(diagnostics)) {
			ret.put("diagnostics", pathologyQueueService.getDiagnostics(request.getFromDate(), request.getToDate()));
		}
		boolean mayProcess = Context.hasPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		List<SimpleObject> rows = new ArrayList<SimpleObject>();
		for (PathologyQueueRow row : pathologyQueueService.getQueue(request.getFromDate(), request.getToDate(),
				request.isIncludeCasesInLab())) {
			SimpleObject o = new SimpleObject();
			o.put("key", row.getEncounterId());
			o.put("encounterId", row.getEncounterId());
			o.put("encounterUuid", row.getEncounterUuid());
			o.put("accessionNumber", row.getAccessionNumber());
			o.put("status", row.isCancelled() ? PathologyQueueRequest.CANCELLED : row.getStatus().name());
			o.put("statusLabel", row.getStatusLabel());
			o.put("inLab", !row.isCancelled() && row.getStatus().isInLab());
			o.put("statusChangedAt", row.getStatusChangedAt() == null ? null : row.getStatusChangedAt().getTime());
			o.put("rejectionReason", row.getRejectionReason());
			o.put("orderIds", row.getOrderIds());
			o.put("orderNumbers", row.getOrderNumbers());
			o.put("tests", row.getTests());
			o.put("urgent", row.isUrgent());
			o.put("additionalInformation", row.getAdditionalInformation());
			o.put("patientId", row.getPatientId());
			o.put("patientUuid", row.getPatientUuid());
			o.put("patientIdentifier", row.getPatientIdentifier());
			o.put("patientName", row.getPatientName());
			o.put("age", row.getAge());
			o.put("sex", row.getSex());
			o.put("hospitalOfOrigin", row.getHospitalOfOrigin());
			o.put("specimens", row.getSpecimens());
			o.put("otherTestsRequested", row.getOtherTestsRequested());
			o.put("requestingPhysician", row.getRequestingPhysician());
			o.put("requestingDepartment", row.getRequestingDepartment());
			o.put("timeRequested", row.getTimeRequested() == null ? null : row.getTimeRequested().getTime());
			o.put("paymentStatus", row.getPaymentStatus() == null ? null : row.getPaymentStatus().name());
			o.put("paymentStatusLabel", row.getPaymentStatus() == null ? null : row.getPaymentStatus().getLabel());
			o.put("timeOfPayment", row.getTimeOfPayment() == null ? null : row.getTimeOfPayment().getTime());
			List<SimpleObject> history = new ArrayList<SimpleObject>();
			for (PathologyQueueRow.HistoryEntry entry : row.getHistory()) {
				SimpleObject h = new SimpleObject();
				h.put("status", entry.getStatus().name());
				h.put("statusLabel", entry.getStatus().getLabel());
				h.put("reason", entry.getReason());
				h.put("by", entry.getBy());
				h.put("date", entry.getDate() == null ? null : entry.getDate().getTime());
				history.add(h);
			}
			o.put("history", history);
			// The pathologist signs out from the report, so it is not one of the steps recorded from the queue
			List<SimpleObject> actions = new ArrayList<SimpleObject>();
			if (mayProcess && !row.isCancelled()) {
				for (PathologyStatus next : row.getStatus().getNextStatuses()) {
					if (next != PathologyStatus.SIGNED_OUT) {
						SimpleObject action = new SimpleObject();
						action.put("status", next.name());
						action.put("label", next.getLabel());
						actions.add(action);
					}
				}
			}
			o.put("actions", actions);
			rows.add(o);
		}
		ret.put("rows", rows);
		return ret;
	}

	/**
	 * Records a step of the lab's work on a request: receiving or rejecting its specimen, grossing, processing or
	 * additional stains
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/pathologyqueue/status", method = RequestMethod.POST)
	@ResponseBody
	public Object changeStatus(@RequestParam(value = "encounterId") Integer encounterId,
							   @RequestParam(value = "status") String status,
							   @RequestParam(value = "reason", required = false) String reason) {
		checkPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS);
		SimpleObject ret = new SimpleObject();
		Encounter encounter = encounterService.getEncounter(encounterId);
		if (encounter == null || pathologyCaseService.getActiveOrders(encounter).isEmpty()
				&& pathologyCaseService.getCase(encounter) == null) {
			ret.put("error", "Pathology request " + encounterId + " not found");
			return ret;
		}
		try {
			PathologyStatus target = PathologyStatus.valueOf(status);
			PathologyCase pathologyCase;
			if (target == PathologyStatus.RECEIVED) {
				pathologyCase = pathologyCaseService.receive(encounter);
			}
			else if (target == PathologyStatus.REJECTED) {
				// The Legacy UI module's XSS filter escapes submitted values; the reason is kept as it was typed
				pathologyCase = pathologyCaseService.reject(encounter, RadiologyReportText.fromRequest(reason));
			}
			else {
				pathologyCase = pathologyCaseService.moveTo(encounter, target);
			}
			ret.put("status", pathologyCase.getStatus().name());
			ret.put("statusLabel", pathologyCase.getStatus().getLabel());
			ret.put("accessionNumber", pathologyCase.getAccessionNumber());
		}
		catch (IllegalArgumentException | IllegalStateException e) {
			ret.put("error", e.getMessage());
		}
		catch (RuntimeException e) {
			log.warn("Unable to move pathology request " + encounterId + " to " + status, e);
			ret.put("error", "The change was not saved: " + e.getMessage());
		}
		return ret;
	}

	private void checkPrivilege(String privilege) {
		if (!Context.hasPrivilege(privilege)) {
			throw new APIAuthenticationException("Privilege required: " + privilege);
		}
	}
}
