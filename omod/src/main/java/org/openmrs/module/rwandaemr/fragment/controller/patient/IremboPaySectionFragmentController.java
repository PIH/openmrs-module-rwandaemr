package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.mohbilling.model.PatientBill;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;

public class IremboPaySectionFragmentController {

    protected final Log log = LogFactory.getLog(IremboPaySectionFragmentController.class);

    public void controller(
        FragmentConfiguration config,
        PageModel pageModel,
        FragmentModel model,
        UiUtils ui,
        UiSessionContext sessionContext,
        @FragmentParam("app") AppDescriptor appDescriptor,
        @SpringBean IntegrationConfig integrationConfig,
        @InjectBeans PatientDomainWrapper patientWrapper
    ){

        List<PatientBill> unpaidBills = new ArrayList<>();
        if(!integrationConfig.isIremboPayEnabled()){
            model.addAttribute("error", "Irembo Pay Feature is disabled");
        } else {
            config.require("patient");
            Object patient = config.get("patient");

            if(patient instanceof Patient){
                patientWrapper.setPatient((Patient) patient);
                config.addAttribute("patient", patientWrapper);
            } else if(patient instanceof PatientDomainWrapper){
                patientWrapper = (PatientDomainWrapper) patient;
            }
            try{
                //unpaidBills = ConsommationUtil.getUnpaidBills(patientWrapper.getPatient());

                model.addAttribute("error", "");
            } catch(Exception e){
                System.out.println("An error occured: " + e.getMessage());
                model.addAttribute("error", e.getMessage());
                
            }
        }

        model.addAttribute("bills", unpaidBills);
    }
    
}
