package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationService;
import org.openmrs.module.rwandaemr.paymentclearance.PaymentClearanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Receives Intouch (Jasmin) delivery reports for any SMS sent by RwandaEMR, and records them against the lab
 * notification or payment clearance message with that provider message id
 */
@Controller
public class SmsDeliveryReportRestController {

	protected final Log log = LogFactory.getLog(getClass());

	@Autowired
	private LabNotificationService labNotificationService;

	@Autowired
	private PaymentClearanceService paymentClearanceService;

	@RequestMapping(value = "/rest/v1/rwandaemr/sms/dlr", method = RequestMethod.POST,
			produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public String receiveDeliveryReport(@RequestParam(value = "id", required = false) String id,
	                                    @RequestParam(value = "message_status", required = false) String messageStatus,
	                                    @RequestParam(value = "level", required = false) String level) {
		try {
			boolean updated = labNotificationService.recordDeliveryReportIfPresent(id, messageStatus, level)
					|| paymentClearanceService.recordDeliveryReport(id, messageStatus, level);
			if (!updated) {
				log.warn("Received SMS delivery report for unknown message id: " + id);
			}
		}
		catch (Exception e) {
			log.warn("Error processing SMS delivery report", e);
		}
		return "ACK/Jasmin";
	}
}
