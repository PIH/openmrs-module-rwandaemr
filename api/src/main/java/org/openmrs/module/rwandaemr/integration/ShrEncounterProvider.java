package org.openmrs.module.rwandaemr.integration;

import ca.uhn.fhir.context.FhirContext;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("shrEncounterProvider")
public class ShrEncounterProvider {

    protected Log log = LogFactory.getLog(getClass());

    private final FhirContext fhirContext;
    private final IntegrationConfig integrationConfig;
    private final RwandaEmrConfig rwandaEmrConfig;
    private final ShrEncounterTranslator shrEncounterTranslator;
    
    public ShrEncounterProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired RwandaEmrConfig rwandaEmrConfig,
        @Autowired ShrEncounterTranslator shrEncounterTranslator
    ){
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
        this.rwandaEmrConfig = rwandaEmrConfig;
        this.shrEncounterTranslator = shrEncounterTranslator;
    }

    public List<ShrEncounter> fetchEncounterFromShr(String upid){
        if(!integrationConfig.isHieEnabled()){
            throw new IllegalStateException("The HIE connection is not enabled on this server");
        }

        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            String url = integrationConfig.getHieEndpointUrl("/shr/Encounter", "searchSet", "ANY","value", upid, "page", "1", "size", "50");
            HttpGet httpGet = new HttpGet(url);
            //log.debug("Getting Encounters for " + upid + " from " + url);

            try(CloseableHttpResponse response = httpClient.execute(httpGet)){
                int statusCode = response.getStatusLine().getStatusCode();
                if(statusCode != 200){
                    throw new IllegalStateException("HIE responded with " + statusCode + ", Please contact HIE support team");
                }
                HttpEntity httpEntity = response.getEntity();
                String data = "";
                try{
                    data = EntityUtils.toString(httpEntity);
                } catch(Exception e){
                    //Here we ignored the string as it can't be catched
                }

                //Process the Bundle information
                Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);

                List<ShrEncounter> encounters = new ArrayList<>();
                if(bundle != null && bundle.hasEntry()){
                    for(Bundle.BundleEntryComponent entryComponent: bundle.getEntry()){
                        Encounter fhirEncounter = (Encounter) entryComponent.getResource();
                        if(fhirEncounter != null){
                            ShrEncounter shrEncounter = new ShrEncounter(fhirEncounter);
                            encounters.add(shrEncounter);
                        }
                    }

                    //From here we can return the list of SHR encounters to be presanted to dashboard later
                    return encounters;
                }
            }
        } catch (Exception e) {
            log.debug("Unable to get information from SHR registry");
        }
        return null;
    }

    public ShrEncounter fetchEncounterFromShrByUuid(String uuid){
        log.info("Please make sure to implement fetch by UUID: " + uuid);
        return null;
    }

    public void updateEncounterInShr(org.openmrs.Encounter encounter) throws Exception {
        //check if the HIE is enabled
        if(!integrationConfig.isHieEnabled()){
            log.debug("Incomplete credentials supplied to connect to shr, skipping");
			return;
        }

        //make sure the patient record has UPID cause if no UPID in the subject the record will not be accepted
        // Patient patient = ;
        PatientIdentifier upid = encounter.getPatient().getPatientIdentifier(rwandaEmrConfig.getUPID());

        if(upid == null){
            log.debug("The patient under this encounter does not have UPID, so records can't be pushed to SHR");
			return;
        }
        //log.info("Updating in SHR but first check if the encounter exist.");
        //check if the SHR had record first
        ShrEncounter shrEncounter = fetchEncounterFromShrByUuid(encounter.getUuid());

        //if the record is new make sure to create it
        if(shrEncounter == null) {
            shrEncounter = new ShrEncounter(new Encounter());
            shrEncounter.getEncounter().setId(encounter.getUuid());
        }
        log.info("Here we need the translator in order to have FHIR Object");
        //make sure to update the registry with the current data
        shrEncounterTranslator.updateShrEncounter(shrEncounter, encounter);

        String endPoint = "/shr/Encounter";
        String postBody = fhirContext.newJsonParser().encodeResourceToString(shrEncounter.getEncounter());
        // log.debug("End Point: " + endPoint);
        // log.debug("Data: " + postBody);

        //Now we are sending the request to HIE server
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){

            HttpPost httpPost = new HttpPost(integrationConfig.getHieEndpointUrl(endPoint));
            httpPost.setEntity(new StringEntity(postBody));
            httpPost.setHeader("Content-Type", "application/json");

            //sent the request and wait for the response
            try(CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String data = "";
                try{
                    data = EntityUtils.toString(entity);
                } catch(Exception ignored){
                    log.info(ignored.getMessage());
                }
                if(statusCode != 201){
                    throw new IllegalStateException("Http Status code: " + statusCode + "; Response was: " + data);
                }
                log.debug("Encounter request submitted successfuly");
            }
        }
    }
}
