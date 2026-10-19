package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.page.controller.radiology.RadiologyQueueRequest;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.OrderToORMO01Translator;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportDraftRepository;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data for the radiology queue page, which polls this to refresh its table
 */
@Controller
public class RadiologyQueueRestController {

	@Autowired
	RadiologyQueueService radiologyQueueService;

	@Autowired
	OrderPaymentLookup radiologyPaymentLookup;

	@Autowired
	OrderService orderService;

	@Autowired
	RadiologyConfig radiologyConfig;

	@Autowired
	RadiologyOrderEventListener radiologyOrderEventListener;

	@Autowired
	RadiologyReportDraftRepository draftRepository;

	@Autowired
	OrderToORMO01Translator orderToORMTranslator;

	private final Log log = LogFactory.getLog(getClass());

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

	/**
	 * Sends a radiology order that was never sent to PACS.  Only the HL7 message is sent: no order is created and
	 * nothing is billed.
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/radiologyqueue/resend", method = RequestMethod.POST)
	@ResponseBody
	public Object resend(@RequestParam(value = "orderId") Integer orderId) {
		if (!Context.hasPrivilege(RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE)) {
			throw new APIAuthenticationException("Privilege required: " + RadiologyQueueService.PRIVILEGE_VIEW_RADIOLOGY_QUEUE);
		}
		SimpleObject ret = new SimpleObject();
		Order order = orderService.getOrder(orderId);
		if (!(order instanceof TestOrder) || order.getAction() == Order.Action.DISCONTINUE
				|| !orderToORMTranslator.isRadiologyOrder(order)) {
			ret.put("error", "Radiology order " + orderId + " not found");
			return ret;
		}
		try {
			String accessionNumber = radiologyOrderEventListener.resendUnsentOrder((TestOrder) order);
			ret.put("sent", true);
			ret.put("accessionNumber", accessionNumber);
		}
		catch (RuntimeException e) {
			log.warn("Unable to send radiology order " + order.getOrderNumber() + " to PACS", e);
			Throwable cause = e.getCause() != null && !(e instanceof IllegalStateException) ? e.getCause() : e;
			ret.put("error", "Order " + order.getOrderNumber() + " was not sent to PACS: " + cause.getMessage());
		}
		return ret;
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
		boolean mayEnterReports = Context.hasPrivilege(RadiologyReportService.PRIVILEGE_ENTER_REPORT);
		List<RadiologyQueueRow> queue = radiologyQueueService.getQueue(request.getFromDate(), request.getToDate());
		// Draft reports are only shown to radiologists
		Set<Integer> ordersWithDrafts = new HashSet<Integer>();
		if (mayEnterReports) {
			List<Integer> orderIds = new ArrayList<Integer>();
			for (RadiologyQueueRow row : queue) {
				orderIds.addAll(row.getOrderIds());
			}
			ordersWithDrafts = draftRepository.getOrdersWithDrafts(orderIds);
		}
		List<SimpleObject> rows = new ArrayList<SimpleObject>();
		for (RadiologyQueueRow row : queue) {
			SimpleObject o = new SimpleObject();
			boolean manualReport = row.getModality() != null
					&& radiologyConfig.isManualReportModality(Modality.valueOf(row.getModality()));
			o.put("manualReport", manualReport);
			// The radiologist reports on the images, so the report can only be entered once PACS has sent them
			o.put("canEnterReport", mayEnterReports && manualReport && row.getPacsStatus() != null
					&& row.getPacsStatus().isStudyDone());
			o.put("key", row.getOrderIds().isEmpty() ? null : row.getOrderIds().get(0));
			// A study that never reached PACS can be sent again from the queue
			o.put("canResend", row.getPacsStatus() == RadiologyQueueRow.PacsStatus.NOT_SENT && !row.getOrderIds().isEmpty());
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
				t.put("reported", test.isReported());
				t.put("reportStatus", test.getReportStatus());
				t.put("hasDraft", ordersWithDrafts.contains(test.getOrderId()));
				tests.add(t);
			}
			o.put("tests", tests);
			RadiologyQueueRow.Creatinine creatinine = row.getCreatinine();
			o.put("creatinine", creatinine == null ? null : creatinine.getDisplay());
			o.put("creatinineDate", creatinine == null || creatinine.getDate() == null ? null : creatinine.getDate().getTime());
			o.put("creatinineHigh", creatinine != null && creatinine.isHigh());
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
