package org.openmrs.module.rwandaemr.rest;

import org.openmrs.Order;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.page.controller.radiology.RadiologyQueueRequest;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyPaymentLookup;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueRow;
import org.openmrs.module.rwandaemr.radiology.queue.RadiologyQueueService;
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
 * Data for the radiology queue page, which polls this to refresh its table
 */
@Controller
public class RadiologyQueueRestController {

	@Autowired
	RadiologyQueueService radiologyQueueService;

	@Autowired
	RadiologyPaymentLookup radiologyPaymentLookup;

	@Autowired
	OrderService orderService;

	/**
	 * Explains why an order shows the payment status it does.  Returns only ids, concept ids, times and statuses.
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/radiologyqueue/paymentcheck", method = RequestMethod.GET)
	@ResponseBody
	public Object checkPayment(@RequestParam(value = "orderNumber") String orderNumber) {
		if (!Context.hasPrivilege(RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE)) {
			throw new APIAuthenticationException("Privilege required: " + RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE);
		}
		Order order = orderService.getOrderByOrderNumber(orderNumber);
		if (order == null) {
			SimpleObject ret = new SimpleObject();
			ret.put("error", "Order " + orderNumber + " not found");
			return ret;
		}
		return radiologyPaymentLookup.explain(order);
	}

	@RequestMapping(value = "/rest/v1/rwandaemr/radiologyqueue", method = RequestMethod.GET)
	@ResponseBody
	public Object getQueue(@RequestParam(value = "startDate", required = false) String startDate,
						   @RequestParam(value = "endDate", required = false) String endDate) {
		if (!Context.hasPrivilege(RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE)) {
			throw new APIAuthenticationException("Privilege required: " + RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE);
		}
		RadiologyQueueRequest request = new RadiologyQueueRequest(startDate, endDate, null, null, null, null, null);
		SimpleObject ret = new SimpleObject();
		ret.put("startDate", request.getStartDate());
		ret.put("endDate", request.getEndDate());
		ret.put("generatedAt", new Date().getTime());
		if (!request.isValid()) {
			ret.put("error", request.getError());
			ret.put("rows", new ArrayList<Object>());
			return ret;
		}
		List<SimpleObject> rows = new ArrayList<SimpleObject>();
		for (RadiologyQueueRow row : radiologyQueueService.getQueue(request.getFromDate(), request.getToDate())) {
			SimpleObject o = new SimpleObject();
			o.put("key", row.getOrderIds().isEmpty() ? null : row.getOrderIds().get(0));
			o.put("orderNumbers", row.getOrderNumbers());
			o.put("orderIds", row.getOrderIds());
			o.put("accessionNumber", row.getAccessionNumber());
			o.put("patientId", row.getPatientId());
			o.put("patientIdentifier", row.getPatientIdentifier());
			o.put("patientName", row.getPatientName());
			o.put("age", row.getAge());
			o.put("sex", row.getSex());
			o.put("modality", row.getModality());
			o.put("typeOfStudy", row.getTypeOfStudy());
			o.put("clinicalHistory", row.getClinicalHistory());
			o.put("requestingPhysician", row.getRequestingPhysician());
			o.put("requestingDepartment", row.getRequestingDepartment());
			o.put("timeRequested", row.getTimeRequested() == null ? null : row.getTimeRequested().getTime());
			o.put("pacsStatus", row.getPacsStatus() == null ? null : row.getPacsStatus().name());
			o.put("pacsStatusLabel", row.getPacsStatus() == null ? null : row.getPacsStatus().getLabel());
			o.put("paymentStatus", row.getPaymentStatus() == null ? null : row.getPaymentStatus().name());
			o.put("paymentStatusLabel", row.getPaymentStatus() == null ? null : row.getPaymentStatus().getLabel());
			o.put("stat", row.isStat());
			o.put("reason", row.getReason());
			o.put("readyForExam", row.isReadyForExam());
			o.put("imageUrl", safeUrl(row.getImageUrl()));
			o.put("imagesReceivedAt", row.getImagesReceivedAt() == null ? null : row.getImagesReceivedAt().getTime());
			o.put("reportStatus", row.getReportStatus());
			o.put("reportedAt", row.getReportedAt() == null ? null : row.getReportedAt().getTime());
			o.put("reportOrderId", row.getReportOrderId());
			List<SimpleObject> tests = new ArrayList<SimpleObject>();
			for (RadiologyQueueRow.Test test : row.getTests()) {
				SimpleObject t = new SimpleObject();
				t.put("orderId", test.getOrderId());
				t.put("orderNumber", test.getOrderNumber());
				t.put("name", test.getName());
				t.put("stat", test.isStat());
				tests.add(t);
			}
			o.put("tests", tests);
			o.put("processed", row.isProcessed());
			o.put("processedLabel", row.getProcessedLabel());
			o.put("timeOfPayment", row.getTimeOfPayment() == null ? null : row.getTimeOfPayment().getTime());
			rows.add(o);
		}
		ret.put("rows", rows);
		return ret;
	}

	/**
	 * @return the link only if it is a web address, so a link from PACS can never run script in the page
	 */
	private String safeUrl(String url) {
		if (url == null) {
			return null;
		}
		String lower = url.trim().toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://") ? url.trim() : null;
	}
}
