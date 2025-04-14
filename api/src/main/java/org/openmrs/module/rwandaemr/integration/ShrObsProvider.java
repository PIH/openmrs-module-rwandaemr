package org.openmrs.module.rwandaemr.integration;

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
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

@Component("shrObsProvider")
public class ShrObsProvider {
    protected Log log = LogFactory.getLog(getClass());

    private final FhirContext fhirContext;
    private final IntegrationConfig integrationConfig;

    public ShrObsProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig
    ) {
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
    }

    public List<ShrObservation> fetchObservationFromShr(String encounterUuid){
        if(!integrationConfig.isHieEnabled()){
            throw new IllegalStateException("The HIE connection is not enabled on this server");
        }
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            String url = integrationConfig.getHieEndpointUrl("/shr/Observation", "searchSet", "ENCOUNTER", "value", encounterUuid, "page", "1", "size", "500");

            HttpGet httpGet = new HttpGet(url);
            try(CloseableHttpResponse response = httpClient.execute(httpGet)){
                int statusCode = response.getStatusLine().getStatusCode();
                if(statusCode != 200){
                    throw new IllegalStateException("HIE responded wiht: " + statusCode);
                }

                HttpEntity httpEntity = response.getEntity();
                String data = "";

                try{
                    data = EntityUtils.toString(httpEntity);
                } catch(Exception e){
                    log.debug("Unable to capture the found Result as no entity is present in the responde");
                }

                Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, data);

                //create a variable to hold all observation to be returned to the user interface
                List<ShrObservation> observations = new ArrayList<>();
                if(bundle != null && bundle.hasEntry()){
                    //work through all bundle found in the 
                    for(Bundle.BundleEntryComponent entryComponent: bundle.getEntry()) {
                        Observation fhirObservation = (Observation) entryComponent.getResource();
                        if(fhirObservation != null){
                            ShrObservation shrObservation = new ShrObservation(fhirObservation);
                            observations.add(shrObservation);
                        }
                    }
                    return observations;
                }
            } catch(Exception e){
                log.debug("Sending request to Observation SHR end point failed: " + url);
            }
        } catch(Exception e){
            log.debug("Unable to get information from SHR Registry");
        }
        return null;
    }
    
}
