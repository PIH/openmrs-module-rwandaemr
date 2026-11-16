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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * REST endpoints for IremboPay: initiate payment and check payment status.
 */
@Controller
public class IremboPayRestController {

    protected final Log log = LogFactory.getLog(getClass());

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

    @RequestMapping(value = "/rest/v1/rwandaemr/irembopay/status", method = RequestMethod.GET)
    @ResponseBody
    public Object isBillPaidByInvoiceNumber(
            @RequestParam(value = "invoiceNumber", required = true) String invoiceNumber,
            @RequestParam(value = "forceUpdate", required = true) Boolean forceUpdate,
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

            if (!bill.isPaid() && forceUpdate) {
                PatientBill updated = billingService.getInvoiceStatus(invNum);
                if (updated != null) {
                    bill = updated;
                }
            }

            result.put("invoiceNumber", invNum);
            result.put("found", true);
            result.put("paid", bill != null && bill.isPaid());
            if (bill != null && bill.getPatientBillId() != null) {
                result.put("billId", bill.getPatientBillId());
            }
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

    @RequestMapping(value = "/rest/v1/rwandaemr/irembopay/init-batch", method = RequestMethod.POST)
    @ResponseBody
    public Object initIremboPayBatch(
            @RequestParam(value = "billIds", required = true) String billIds,
            @RequestParam(value = "phoneNumber", required = true) String phoneNumber,
            HttpServletResponse response
    ) {
        SimpleObject result = new SimpleObject();
        try {
            if (StringUtils.isBlank(billIds)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "billIds is required");
                return result;
            }
            if (StringUtils.isBlank(phoneNumber)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "Phone number is required");
                return result;
            }
            String phone = phoneNumber.trim();

            List<Integer> parsedBillIds = new ArrayList<Integer>();
            LinkedHashSet<Integer> seen = new LinkedHashSet<Integer>();
            Arrays.stream(billIds.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(id -> {
                        try {
                            int bid = Integer.parseInt(id);
                            if (seen.add(bid)) {
                                parsedBillIds.add(bid);
                            }
                        } catch (Exception ignored) {
                        }
                    });

            if (parsedBillIds.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                result.put("status", "error");
                result.put("message", "No valid bill IDs were provided");
                return result;
            }

            BillingService billingService = Context.getService(BillingService.class);
            List<String> invoices = new ArrayList<>();
            Patient[] patientForBatch = new Patient[1];

            for (Integer billId : parsedBillIds) {
                String invoiceNumber = tryInitIremboPayForBill(billingService, billId, phone, patientForBatch);
                if (invoiceNumber != null) {
                    log.info("Batch IremboPay: billId=" + billId + " childInvoice=" + invoiceNumber);
                    invoices.add(invoiceNumber);
                }
            }

            if (invoices.isEmpty()) {
                result.put("status", "error");
                result.put("message", "No bills could be submitted for batch payment (check bills are unpaid and linked to a patient).");
                result.put("requestedCount", parsedBillIds.size());
                result.put("invoiceCount", 0);
                return result;
            }

            if (patientForBatch[0] == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                result.put("status", "error");
                result.put("message", "Patient context missing for batch payment.");
                return result;
            }

            billingService.initIremboPayBatch(patientForBatch[0], invoices, phone);

            boolean partial = invoices.size() < parsedBillIds.size();
            result.put("status", partial ? "partial" : "success");
            result.put("message", partial
                    ? ("IremboPay batch started for " + invoices.size() + " of " + parsedBillIds.size()
                    + " bill(s). Complete MoMo payment; this page will refresh to show status.")
                    : "IremboPay batch started. Complete MoMo payment; this page will refresh to show status.");
            result.put("invoiceCount", invoices.size());
            result.put("requestedCount", parsedBillIds.size());
            result.put("invoices", invoices);
            return result;
        } catch (Exception e) {
            log.error("Error initializing IremboPay batch for billIds=" + billIds, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("status", "error");
            result.put("message", e.getMessage() != null ? e.getMessage() : "Failed to initialize IremboPay batch");
            return result;
        }
    }

    /**
     * Creates a child Irembo invoice for one bill (no MoMo yet). Sets patientForBatch[0] on first success.
     *
     * @return Irembo child invoice number, or null if skipped / failed
     */
    private String tryInitIremboPayForBill(BillingService billingService, Integer billId, String phoneNumber,
            Patient[] patientForBatch) {
        try {
            PatientBill patientBill = billingService.getPatientBill(billId);
            if (patientBill == null) {
                return null;
            }
            if (patientBill.isPaid()) {
                return null;
            }
            Consommation consommation = billingService.getConsommationByPatientBill(patientBill);
            if (consommation == null || consommation.getBeneficiary() == null) {
                return null;
            }
            Patient patient = consommation.getBeneficiary().getPatient();
            if (patient == null) {
                return null;
            }
            if (patientForBatch[0] == null) {
                patientForBatch[0] = patient;
            }
            return billingService.createIremboInvoice(patient, patientBill, phoneNumber);
        } catch (Exception e) {
            log.warn("Batch IremboPay: failed for billId=" + billId + ": " + e.getMessage());
            return null;
        }
    }
}
