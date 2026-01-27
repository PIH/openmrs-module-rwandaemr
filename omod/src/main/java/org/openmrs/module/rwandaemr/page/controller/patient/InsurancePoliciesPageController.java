package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

public class InsurancePoliciesPageController {

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patientId") Patient patient) throws IOException {

        BillingService billingService = Context.getService(BillingService.class);
        List<InsurancePolicy> insurancePolicies = billingService.getAllInsurancePoliciesByPatient(patient);
        insurancePolicies.sort((a, b) -> a.getInsurance().getName().compareToIgnoreCase(b.getInsurance().getName()));

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("insurancePolicies", insurancePolicies);
    }
}
