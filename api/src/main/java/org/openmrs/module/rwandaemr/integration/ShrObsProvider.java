package org.openmrs.module.rwandaemr.integration;

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
import org.hl7.fhir.r4.model.Observation;
import org.openmrs.Obs;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

@Component("shrObsProvider")
public class ShrObsProvider {
    protected Log log = LogFactory.getLog(getClass());

    private final FhirContext fhirContext;
    private final IntegrationConfig integrationConfig;
    private final RwandaEmrConfig rwandaEmrConfig;
    private final ShrObsTranslator shrObsTranslator;

    public ShrObsProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired RwandaEmrConfig rwandaEmrConfig,
        @Autowired ShrObsTranslator shrObsTranslator
    ) {
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
        this.rwandaEmrConfig = rwandaEmrConfig;
        this.shrObsTranslator = shrObsTranslator;
    }

    public List<ShrObservation> fetchObservationFromShr(String encounterUuid){
        if(!integrationConfig.isHieEnabled()){
            throw new IllegalStateException("The HIE connection is not enabled on this server");
        }
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            if(httpClient == null){
                throw new IllegalStateException("HIE client could not be created. Check HIE credentials configuration.");
            }
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

    public ShrObservation fetchObservationFromShrByUuid(String uuid){
        log.info("The Implementation of fetch by UUID is ongoing please complete it ASAP");
        return null;
    }
    
    public void updateObsInShr(Obs obs) throws Exception{

        //Make sure to continue only if HIE settings are ready
        if(!integrationConfig.isHieEnabled()){
            log.debug("Incomplete credentials suplied to connect to SHR, skip OBS pushing process!");
            return;
        }

        //Make sure the patient hold the UPID as if no UPID records will not be accepted.
        PatientIdentifier upid = obs.getEncounter().getPatient().getPatientIdentifier(rwandaEmrConfig.getUPID());
        if(upid == null){
            log.debug("The patient who ownes this observation does not hold a valid UPID, Record can't be pushed to SHR!");
            return;
        }

        ShrObservation shrObservation = fetchObservationFromShrByUuid(obs.getUuid());

        if(shrObservation == null){
            shrObservation = new ShrObservation(new Observation());
            shrObservation.getObservation().setId(obs.getUuid());
        }
        log.info("Here the translator comes in handy to have the FHIR Object");
        shrObsTranslator.updateShrObservation(shrObservation, obs);

        String endPoint = "/shr/Observation";
        String postBody = fhirContext.newJsonParser().encodeResourceToString(shrObservation.getObservation());

        //log.debug("End Point: " + endPoint);
        log.debug("Data: " + postBody);
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            if(httpClient == null){
                throw new IllegalStateException("HIE client could not be created. Check HIE credentials configuration.");
            }
            HttpPost httpPost = new HttpPost(integrationConfig.getHieEndpointUrl(endPoint));
            httpPost.setEntity(new StringEntity(postBody));
            httpPost.setHeader("Content-Type", "application/json");

            //Make sure to send the request
            try(CloseableHttpResponse response = httpClient.execute(httpPost)){
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                String data = "";
                try {
                    data = EntityUtils.toString(entity);
                } catch(Exception ignored){
                    log.info(ignored.getMessage());
                }
                if(statusCode != 201){
                    throw new IllegalStateException("Http Status code: " + statusCode + "; Response was: " + data);
                }
                log.debug("Observation request submitted successfuly");
            }
        }
    }
    
}
