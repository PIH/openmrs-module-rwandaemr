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
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
    private final ShrConsentProvider shrConsentProvider;
    private final ShrConsentTranslator shrConsentTranslator;
    
    public ShrEncounterProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired RwandaEmrConfig rwandaEmrConfig,
        @Autowired ShrEncounterTranslator shrEncounterTranslator,
        @Autowired ShrConsentProvider shrConsentProvider,
        @Autowired ShrConsentTranslator shrConsentTranslator
    ){
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
        this.rwandaEmrConfig = rwandaEmrConfig;
        this.shrEncounterTranslator = shrEncounterTranslator;
        this.shrConsentProvider = shrConsentProvider;
        this.shrConsentTranslator = shrConsentTranslator;
    }

    public List<ShrEncounter> fetchEncounterFromShr(String upid){
        if(!integrationConfig.isHieEnabled()){
            throw new IllegalStateException("The HIE connection is not enabled on this server");
        }

        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            String url = integrationConfig.getHieEndpointUrl("/shr/Encounter", "searchSet", "ALL","value", upid, "page", "1", "size", "50");
            HttpGet httpGet = new HttpGet(url);
            //log.debug("Getting Encounters for " + upid + " from " + url);

            try(CloseableHttpResponse response = httpClient.execute(httpGet)){
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity httpEntity = response.getEntity();
                String data = "";
                try{
                    data = EntityUtils.toString(httpEntity);
                } catch(Exception e){
                    //Here we ignored the string as it can't be catched
                }

                //Check if the response is an OperationOutcome (error response) - can occur with any status code
                if(data != null && !data.trim().isEmpty()){
                    try{
                        IBaseResource resource = fhirContext.newJsonParser().parseResource(data);
                        if(resource instanceof OperationOutcome){
                            OperationOutcome operationOutcome = (OperationOutcome) resource;
                            String errorMessage = "Error retrieving HIE encounters";
                            if(operationOutcome.hasIssue() && !operationOutcome.getIssue().isEmpty()){
                                OperationOutcome.OperationOutcomeIssueComponent issue = operationOutcome.getIssue().get(0);
                                if(issue.hasDiagnostics()){
                                    String diagnostics = issue.getDiagnostics();
                                    if(diagnostics != null && !diagnostics.trim().isEmpty()){
                                        errorMessage = diagnostics;
                                    }
                                }
                                if(errorMessage.equals("Error retrieving HIE encounters") && issue.hasDetails() && issue.getDetails().hasText()){
                                    errorMessage = issue.getDetails().getText();
                                }
                            }
                            log.info("OperationOutcome received from HIE (status " + statusCode + "): " + errorMessage);
                            log.debug("Full OperationOutcome response: " + data);
                            throw new IllegalStateException(errorMessage);
                        }
                    } catch(IllegalStateException ise){
                        //Re-throw IllegalStateException (OperationOutcome errors) so they reach the controller
                        throw ise;
                    } catch(Exception parseException){
                        //If parsing fails (not an OperationOutcome), continue with status code check below
                        log.debug("Failed to parse response as OperationOutcome: " + parseException.getMessage());
                    }
                }

                //If not an OperationOutcome, check status code
                if(statusCode != 200){
                    String errorMessage = "HIE responded with " + statusCode;
                    if(statusCode == 403){
                        errorMessage += " (Forbidden)";
                    } else if(statusCode == 401){
                        errorMessage += " (Unauthorized)";
                    } else if(statusCode == 404){
                        errorMessage += " (Not Found)";
                    } else if(statusCode >= 500){
                        errorMessage += " (Server Error)";
                    }
                    errorMessage += ". Please contact HIE support team";
                    throw new IllegalStateException(errorMessage);
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
        } catch (IllegalStateException ise) {
            //Re-throw IllegalStateException so it reaches the controller for proper error handling
            log.debug("Re-throwing IllegalStateException: " + ise.getMessage());
            throw ise;
        } catch (Exception e) {
            log.debug("Unable to get information from SHR registry: " + e.getMessage(), e);
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

        //Here we need check if the encounter type is of type registration
        log.info("Checking if encounter type is registration. Config registration type: " + integrationConfig.getRegistrationEncounterType() + ", Encounter type: " + (encounter.getEncounterType() != null ? encounter.getEncounterType().getId() : "null"));
        if(integrationConfig.getRegistrationEncounterType() != 0 && encounter.getEncounterType() != null && integrationConfig.getRegistrationEncounterType() == encounter.getEncounterType().getId()){
            log.info("Registration encounter type matched. Starting consent check for patient: " + upid.getIdentifier());
            //Now we can need to complete the consent process
            //Create ShrConsent with a new Consent object (not null) since the consent field is final
            ShrConsent existingConsent = new ShrConsent(new Consent());
            boolean hasValidConsentResult = shrConsentProvider.hasValidConsent(existingConsent, upid.getIdentifier());
            log.info("hasValidConsent returned: " + hasValidConsentResult);
            if(!hasValidConsentResult){
                //Here Make sure to create one
                log.info("No valid consent found. Starting consent creation process for patient: " + upid.getIdentifier());

                //Try Consent translator now
                log.info("Translating encounter to FHIR Consent object");
                try {
                    shrConsentTranslator.updateShrConsent(existingConsent, encounter);
                    log.info("Consent translation completed successfully");
                } catch(Exception e) {
                    log.error("Error during consent translation: " + e.getMessage(), e);
                    throw e;
                }

                //Prepare Consent endpoint
                String consentEndPoint = "/shr/Consent";
                String consentPostBody = fhirContext.newJsonParser().encodeResourceToString(existingConsent.getConsent());
                log.info("Prepared consent POST body (length: " + consentPostBody.length() + " chars)");
                log.debug("Consent POST body: " + consentPostBody);
                
                try(CloseableHttpClient httpClientConsent = HttpUtils.getHieClient()){
                    String consentUrl = integrationConfig.getHieEndpointUrl(consentEndPoint);
                    log.info("Sending consent creation request to: " + consentUrl);
                    HttpPost httpPost = new HttpPost(consentUrl);
                    httpPost.setEntity(new StringEntity(consentPostBody));
                    httpPost.setHeader("Content-Type", "application/json");

                    //sent the request and wait for the response
                    try(CloseableHttpResponse response = httpClientConsent.execute(httpPost)) {
                        int statusCode = response.getStatusLine().getStatusCode();
                        log.info("Consent creation request sent. Response status code: " + statusCode);
                        HttpEntity entity = response.getEntity();
                        String dataConsent = "";
                        try{
                            dataConsent = EntityUtils.toString(entity);
                            log.info("Consent creation response body: " + dataConsent);
                        } catch(Exception ignored){
                            log.warn("Could not read consent response body: " + ignored.getMessage());
                        }
                        if(statusCode != 201){
                            log.error("Consent creation failed with status code: " + statusCode + ". Response: " + dataConsent);
                            throw new IllegalStateException("Http Status code: " + statusCode + "; Response was: " + dataConsent);
                        }
                        log.info("Consent request submitted successfully for patient: " + upid.getIdentifier());
                    }
                } catch(Exception e) {
                    log.error("Error sending consent creation request: " + e.getMessage(), e);
                    throw e;
                }
            } else {
                log.info("Valid consent already exists for patient: " + upid.getIdentifier() + ". Skipping consent creation.");
            }
        } else {
            log.debug("Encounter type mismatch. Config registration type: " + integrationConfig.getRegistrationEncounterType() + ", Submitted encounter type: " + (encounter.getEncounterType() != null ? encounter.getEncounterType().getId() : "null"));
        }
    }
}
