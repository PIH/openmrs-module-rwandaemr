package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.math.BigDecimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.mohbilling.model.PatientBill;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class IremboPayStatusSectionFragmentController {

    protected final Log log = LogFactory.getLog(IremboPayStatusSectionFragmentController.class);

    public void controller(
            FragmentConfiguration config,
            PageModel pageModel,
            FragmentModel model,
            UiUtils ui,
            UiSessionContext sessionContext,
            @FragmentParam("app") AppDescriptor appDescriptor,
            @RequestParam(value = "billId") String billId,
            @RequestParam(value = "phoneNumber", required = false) String phoneNumber,
            @SpringBean IntegrationConfig integrationConfig
    ) {
        String invoiceNumber = "";
        BigDecimal amount = BigDecimal.ZERO;
        try {
            BillingService billingService = Context.getService(BillingService.class);
            PatientBill bill = billingService.getPatientBill(Integer.valueOf(billId));
            if (bill != null) {
                invoiceNumber = bill.getInvoiceNumber() != null ? bill.getInvoiceNumber() : "";
                amount = bill.getAmount() != null ? bill.getAmount() : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            log.warn("Could not load bill details for billId=" + billId, e);
        }
        model.addAttribute("billId", billId);
        model.addAttribute("invoiceNumber", invoiceNumber);
        model.addAttribute("phoneNumber", phoneNumber != null ? phoneNumber : "");
        model.addAttribute("amount", amount);
    }
}
