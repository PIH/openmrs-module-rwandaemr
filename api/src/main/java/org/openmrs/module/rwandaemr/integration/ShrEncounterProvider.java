package org.openmrs.module.rwandaemr.integration;

import ca.uhn.fhir.context.FhirContext;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("shrEncounterProvider")
public class ShrEncounterProvider {

    protected Log log = LogFactory.getLog(getClass());

    private final FhirContext fhirContext;
    private final IntegrationConfig integrationConfig;
    
    public ShrEncounterProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig
    ){
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
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
}
