package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.PatientBill;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;

/**
 * REST endpoints for IremboPay: initiate payment and check payment status.
 */
@Controller
public class IremboPayRestController {

    protected final Log log = LogFactory.getLog(getClass());

    /**
     * Initialize IremboPay for the given bill and phone number.
     * Uses BillingService.initIremboPay(patient, patientBill, phoneNumber) from the mohbilling module.
     *
     * @param billId     patient bill id (required)
     * @param ownerCode  phone number for payment (required)
     * @param response   HTTP response (for status codes)
     * @return JSON with status and message
     */
    @RequestMapping(value = "/rest/v1/rwandaemr/irembopay/init", method = RequestMethod.POST)
    @ResponseBody
    public Object initIremboPay(
            @RequestParam(value = "billId", required = true) Integer billId,
            @RequestParam(value = "ownerCode", required = true) String ownerCode,
            HttpServletResponse response
    ) {
        SimpleObject result = new SimpleObject();
        try {
            if (ownerCode == null || ownerCode.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "Phone number (ownerCode) is required");
                return result;
            }
            String phoneNumber = ownerCode.trim();

            BillingService billingService = Context.getService(BillingService.class);
            PatientBill patientBill = billingService.getPatientBill(billId);
            if (patientBill == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                result.put("status", "error");
                result.put("message", "Bill not found for id: " + billId);
                return result;
            }
            if (patientBill.isPaid()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                result.put("status", "error");
                result.put("message", "Bill " + billId + " is already paid");
                return result;
            }

            Consommation consommation = billingService.getConsommationByPatientBill(patientBill);
            if (consommation == null || consommation.getBeneficiary() == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "Bill has no associated beneficiary/patient");
                return result;
            }
            Patient patient = consommation.getBeneficiary().getPatient();
            if (patient == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "Beneficiary has no patient");
                return result;
            }

            billingService.initIremboPay(patient, patientBill, phoneNumber);
            result.put("status", "success");
            result.put("message", "IremboPay initiated successfully");
            return result;
        } catch (Exception e) {
            log.error("Error initializing IremboPay for billId=" + billId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("status", "error");
            result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to initialize IremboPay");
            return result;
        }
    }

    /**
     * Check if the PatientBill with the given invoice number is paid.
     *
     * @param invoiceNumber invoice number (required)
     * @param response      HTTP response (for status codes)
     * @return JSON with invoiceNumber, found (boolean), paid (boolean)
     */
    @RequestMapping(value = "/rest/v1/rwandaemr/irembopay/status", method = RequestMethod.GET)
    @ResponseBody
    public Object isBillPaidByInvoiceNumber(
            @RequestParam(value = "invoiceNumber", required = true) String invoiceNumber,
            HttpServletResponse response
    ) {
        SimpleObject result = new SimpleObject();
        try {
            if (StringUtils.isBlank(invoiceNumber)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("invoiceNumber", invoiceNumber);
                result.put("found", false);
                result.put("paid", false);
                result.put("message", "invoiceNumber is required");
                return result;
            }
            String invNum = invoiceNumber.trim();

            BillingService billingService = Context.getService(BillingService.class);
            PatientBill bill = billingService.getPatientBillStatus(invNum);

            if (bill == null) {
                result.put("invoiceNumber", invNum);
                result.put("found", false);
                result.put("paid", false);
                result.put("message", "No bill found with this invoice number");
                return result;
            }

            result.put("invoiceNumber", invNum);
            result.put("found", true);
            result.put("paid", bill.isPaid());
            result.put("billId", bill.getPatientBillId());
            return result;
        } catch (Exception e) {
            log.error("Error checking paid status for invoiceNumber=" + invoiceNumber, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("invoiceNumber", invoiceNumber);
            result.put("found", false);
            result.put("paid", false);
            result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to check payment status");
            return result;
        }
    }
}
