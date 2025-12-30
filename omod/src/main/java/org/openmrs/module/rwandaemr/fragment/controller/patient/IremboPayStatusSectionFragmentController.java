package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.math.BigDecimal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
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
        @SpringBean IntegrationConfig integrationConfig
        ){
            //Here we need to get the phone number that can be used during payment process

            String phoneNumber = "0783095523";
            String invoiceNumber = "";
            BigDecimal amount = BigDecimal.valueOf(Double.MAX_VALUE);
            model.addAttribute("billId", billId);
            model.addAttribute("invoiceNumber", invoiceNumber);
            model.addAttribute("phoneNumber", phoneNumber);
            model.addAttribute("amount", amount);

    }
}
