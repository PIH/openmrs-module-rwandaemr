package org.openmrs.module.rwandaemr.integration;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;

@Component("shrConsentProvider")
public class ShrConsentProvider {
    protected Log log = LogFactory.getLog(getClass());

    private final FhirContext fhirContext;
    private final IntegrationConfig integrationConfig;
    public ShrConsentProvider(
        @Autowired @Qualifier("fhirR4") FhirContext fhirContext,
        @Autowired IntegrationConfig integrationConfig,
        @Autowired RwandaEmrConfig rwandaEmrConfig,
        @Autowired ShrConsentTranslator shrConsentTranslator
    ){
        this.fhirContext = fhirContext;
        this.integrationConfig = integrationConfig;
    }
    
    public List<ShrConsent> fetchConsentFromShr(String upid){
        if(!integrationConfig.isHieEnabled()){
            throw new IllegalStateException("The HIE connection is not enabled on this server");
        }
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            if(httpClient == null){
                throw new IllegalStateException("HIE client could not be created. Check HIE credentials configuration.");
            }
            String url = integrationConfig.getHieEndpointUrl("/shr/Consent/$list-consents", "patient", upid, "page", "1", "size", "5","sort", "_lastUpdated,desc");
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
                            String errorMessage = "Error retrieving Consents from HIE";
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

                List<ShrConsent> consents = new ArrayList<>();
                if(bundle != null && bundle.hasEntry()){
                    for(Bundle.BundleEntryComponent entryComponent: bundle.getEntry()){
                        Consent fhirConsent = (Consent) entryComponent.getResource();
                        if(fhirConsent != null){
                            ShrConsent shrConsent = new ShrConsent(fhirConsent);
                            consents.add(shrConsent);
                        }
                    }

                    //From here we can return the list of SHR Consents to be presanted to dashboard later
                    return consents;
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

    public Boolean hasValidConsent(@NotNull ShrConsent shrConsent, String upid){
        try {
            List<ShrConsent> consents = fetchConsentFromShr(upid);
            if(consents != null && consents.size() > 0){
                for(ShrConsent consent : consents){
                    if(consent.getCategory().equalsIgnoreCase("Information access") && consent.getStatus().equalsIgnoreCase("active")){
                        shrConsent = consent;
                        return true;
                    }
                }
            }
        } catch(IllegalStateException ise){
            //If HIE returns access denied or no consent found, treat it as no valid consent
            log.info("IllegalStateException caught in hasValidConsent for patient " + upid + ": " + ise.getMessage());
            //Note: shrConsent.getConsent() may be null if created with new ShrConsent(null)
            //The consent will be created later by shrConsentTranslator.updateShrConsent()
            //This allows the system to proceed with creating a new consent
            log.info("No valid consent found for patient " + upid + " (HIE response: " + ise.getMessage() + "). Will create new consent.");
            return false;
        } catch(Exception e){
            log.warn("Exception caught in hasValidConsent for patient " + upid + ": " + e.getMessage(), e);
            //Note: shrConsent.getConsent() may be null if created with new ShrConsent(null)
            //The consent will be created later by shrConsentTranslator.updateShrConsent()
            //For any other exception, log it and return false to allow consent creation
            log.warn("Error checking consent for patient " + upid + ": " + e.getMessage() + ". Will attempt to create new consent.");
            return false;
        }
        log.info("No consents found in response, will create new consent for patient: " + upid);
        return false;
    }

}
