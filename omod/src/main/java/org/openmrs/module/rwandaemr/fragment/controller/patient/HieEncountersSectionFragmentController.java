package org.openmrs.module.rwandaemr.fragment.controller.patient;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.EncounterService;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.ShrEncounter;
import org.openmrs.module.rwandaemr.integration.ShrEncounterProvider;
import org.openmrs.module.rwandaemr.integration.ShrEncounterTranslator;
import org.openmrs.module.rwandaemr.integration.ShrVisit;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.FragmentConfiguration;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.ui.framework.page.PageModel;

public class HieEncountersSectionFragmentController {

    protected final Log log = LogFactory.getLog(HieEncountersSectionFragmentController.class);

	public void controller(FragmentConfiguration config,
    PageModel pageModel,
    FragmentModel model,
    UiUtils ui,
    UiSessionContext sessionContext,
    @FragmentParam("app") AppDescriptor appDescriptor,
    @SpringBean("encounterService") EncounterService encounterService,
    @InjectBeans PatientDomainWrapper patientWrapper,
    @SpringBean IntegrationConfig integrationConfig,
    @SpringBean RwandaEmrConfig rwandaEmrConfig,
    @SpringBean ShrEncounterProvider shrEncounterProvider,
    @SpringBean ShrEncounterTranslator shrEncounterTranslator
    ) {
        config.require("patient");
        Object patient = config.get("patient");

        if(patient instanceof Patient){
            patientWrapper.setPatient((Patient) patient);
            config.addAttribute("patient", patientWrapper);
        } else if(patient instanceof PatientDomainWrapper) {
            patientWrapper = (PatientDomainWrapper) patient;
        }

        List<Encounter> encounters = new ArrayList<Encounter>();
        List<ShrVisit> visits = new ArrayList<>();
        List<String> visitedLocation = new ArrayList<>();
        
        //Initialize error attribute to avoid property not found errors
        model.addAttribute("error", "");

        //Here make sure to get information from HIE
        try{
            if(integrationConfig.isHieEnabled()){
                //Here the settings are OK and ready to fetch SHR information from the server
                PatientIdentifier upidPatientIdentifier = patientWrapper.getPatient().getPatientIdentifier(rwandaEmrConfig.getUPID());
                if(upidPatientIdentifier == null){
                    model.addAttribute("error", "No UPID found");
                } else {
                    //as we have the UPID make sure to send the shr request
                    try{
                        List<ShrEncounter> shrEncounters = shrEncounterProvider.fetchEncounterFromShr(upidPatientIdentifier.getIdentifier());

                        if(shrEncounters != null && !shrEncounters.isEmpty()){
                            //From here make sure to translate the shrEncounter into OpenMRS encounter to easy the listing operation
                            for(ShrEncounter shrEncounter: shrEncounters){
                                Encounter myEncounter = shrEncounterTranslator.toEncounter(shrEncounter);
                                String locationName = "";
                                if(myEncounter.getLocation() != null){
                                    locationName = myEncounter.getLocation().getName();
                                } else {
                                    locationName = "Unspecified";
                                }
                                int location_index = visitedLocation.indexOf(locationName);
                                if(location_index == -1){
                                    //make sure to create the record
                                    
                                    ShrVisit shrVisit = new ShrVisit();
                                    if(myEncounter.getLocation() != null){
                                        shrVisit.setLocation(locationName);
                                    } else {
                                        shrVisit.setLocation(locationName);
                                    }
                                    shrVisit.clearEncounters();
                                    shrVisit.addEncounter(myEncounter);
                                    visits.add(shrVisit);
                                    visitedLocation.add(locationName);
                                } else {
                                    visits.get(location_index).addEncounter(myEncounter);
                                    
                                }
                                encounters.add(myEncounter);
                            }
                        }
                    } catch(IllegalStateException ise){
                        String errorMsg = ise.getMessage();
                        log.error("IllegalStateException caught in HIE encounters fetch: " + errorMsg);
                        if(errorMsg == null || errorMsg.trim().isEmpty()){
                            errorMsg = "Error retrieving HIE encounters";
                        }
                        model.addAttribute("error", errorMsg);
                        log.info("Error message set in model: '" + errorMsg + "'");
                        //Don't process encounters when there's an error - encounters list will remain empty
                    } catch(Exception e){
                        log.error("Unexpected exception in HIE encounters fetch: " + e.getClass().getName());
                        String errorMsg = e.getMessage();
                        if(errorMsg == null || errorMsg.trim().isEmpty()){
                            errorMsg = "Error retrieving HIE encounters: " + e.getClass().getSimpleName();
                        }
                        model.addAttribute("error", errorMsg);
                        log.info("Error message set in model from unexpected exception: '" + errorMsg + "'");
                    }
                }
                //log.debug("First get Patient UPID if not available stop the operation identifiers: " + upidPatientIdentifier.getIdentifier());
            } else {
                model.addAttribute("error", "Hie is not enabled on this server");
            }
        } catch(Exception e){
            //Only handle if error hasn't been set yet (to avoid overriding more specific error messages)
            if(model.get("error") == null){
                String errorMsg = e.getMessage();
                if(errorMsg == null || errorMsg.trim().isEmpty()){
                    errorMsg = "Error retrieving HIE encounters: " + e.getClass().getSimpleName();
                }
                model.addAttribute("error", errorMsg);
                log.error("Error retrieving HIE encounters", e);
            }
        }

        model.addAttribute("encounters", encounters);
        model.addAttribute("visit_list", visits);
        model.addAttribute("visited_locations", visitedLocation);
        
        //Log for debugging
        log.info("HIE Encounters Section - Encounters: " + encounters.size() + ", Visits: " + visits.size() + ", Error: '" + model.get("error") + "'");
    }
}
