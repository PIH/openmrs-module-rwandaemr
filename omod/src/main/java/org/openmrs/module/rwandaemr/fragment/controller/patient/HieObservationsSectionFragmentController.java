package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Obs;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.ShrObsProvider;
import org.openmrs.module.rwandaemr.integration.ShrObsTranslator;
import org.openmrs.module.rwandaemr.integration.ShrObservation;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

public class HieObservationsSectionFragmentController {

    protected final Log log = LogFactory.getLog(HieObservationsSectionFragmentController.class);
    
    public void controller(
        FragmentConfiguration config,
        PageModel pageModel,
        FragmentModel model,
        UiUtils ui,
        UiSessionContext sessionContext,
        @FragmentParam("app") AppDescriptor appDescriptor,
        @RequestParam(value = "encounterUuid") String encounterUuid,
        @SpringBean IntegrationConfig integrationConfig,
        @SpringBean ShrObsProvider shrObsProvider,
        @SpringBean ShrObsTranslator shrObsTranslator
        ){

            List<Obs> obervationsList = new ArrayList<Obs>();
            //As here we have the uuid of the encounter we are allowed to call the SHR for observation information
            try{
                if(integrationConfig.isHieEnabled()){
                    //Make sure to fetch observations from SHR based on the found encounter uuid
                    try{
                        List<ShrObservation> shrObservations = shrObsProvider.fetchObservationFromShr(encounterUuid);
                        if(shrObservations != null){
                            log.debug(shrObservations.size() + " Observations are bound to " + encounterUuid);
                            //Now translate SHR encounter into appropriate Obs object for Openmrs Use
                            for(ShrObservation shrObservation : shrObservations){
                                Obs myObs = shrObsTranslator.toObs(shrObservation);
                                
                                obervationsList.add(myObs);
                            }
                        }
                    } catch(IllegalStateException ise){

                    }
                }
            } catch(Exception e){
                log.debug(e.getMessage());
            }
            //log.debug(obervationsList.size() + " is the list of found observation to be reflected on the UI");
            //
            model.addAttribute("uuid", encounterUuid);
            model.addAttribute("message", "Here some data are retrieved now we are good to go. thanks!!!!!!!!!!!!!!");
            model.addAttribute("observations", obervationsList);
        }
}
