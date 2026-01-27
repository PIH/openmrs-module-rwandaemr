package org.openmrs.module.rwandaemr.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.openmrs.module.rwandaemr.integration.HttpUtils;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API endpoint for patient transfer information
 * Returns transfer data in JSON format
 */
@Controller
public class TransferRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    IntegrationConfig integrationConfig;

    /**
     * Get transfer information for a patient by UPID
     * 
     * @param request HTTP request (should contain 'upid' query parameter and optional 'activeOnly' parameter)
     * @param response HTTP response
     * @return Transfer information as JSON
     * @throws ResponseException
     */
    @RequestMapping(value = "/rest/v1/rwandaemr/transfer", method = RequestMethod.GET)
    @ResponseBody
    public Object getTransfer(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        // Get UPID from request parameter
        String upid = request.getParameter("upid");
        
        // Validate UPID parameter
        if(upid == null || upid.trim().isEmpty()){
            log.warn("Transfer REST API called without UPID parameter");
            throw new IllegalRequestException("UPID parameter is required. Usage: /rest/v1/rwandaemr/transfer?upid=<patient-upid>[&activeOnly=true]");
        }
        
        upid = upid.trim();
        
        // Get activeOnly parameter (optional, defaults to false)
        String activeOnlyParam = request.getParameter("activeOnly");
        boolean activeOnly = false;
        if(activeOnlyParam != null && !activeOnlyParam.trim().isEmpty()){
            activeOnly = Boolean.parseBoolean(activeOnlyParam.trim());
        }
        
        // If activeOnly is true, set fromDate to the first day of the current month
        String fromDate = null;
        String endDate = null;
        if(activeOnly){
            LocalDate firstDayOfMonth = LocalDate.now().withDayOfMonth(1);
            fromDate = firstDayOfMonth.format(DateTimeFormatter.ISO_LOCAL_DATE);
            endDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            log.info("activeOnly is true, setting fromDate to first day of current month: " + fromDate + ", endDate to next day: " + endDate);
        }
        
        log.info("Transfer REST API endpoint called for UPID: " + upid + ", activeOnly: " + activeOnly + (fromDate != null ? ", fromDate: " + fromDate + ", endDate: " + endDate : ""));
        
        SimpleObject transferResponse = new SimpleObject();
        
        // Check if HIE is enabled
        if(!integrationConfig.isHieEnabled()){
            log.warn("HIE is not enabled, returning sample data");
            transferResponse.put("status", "error");
            transferResponse.put("message", "HIE is not enabled");
            List<SimpleObject> sampleList = new ArrayList<SimpleObject>();
            transferResponse.put("data", sampleList);
            return transferResponse;
        }
        
        // Fetch transfers from HIE
        try {
            List<SimpleObject> transferList = fetchTransfersFromHie(upid, fromDate, endDate);
            log.info("Successfully retrieved transfer data from HIE for UPID: " + upid);
            
            transferResponse.put("status", "success");
            transferResponse.put("data", transferList);
            return transferResponse;
        } catch(Exception e){
            log.error("Error fetching transfers from HIE for UPID: " + upid, e);
            
            // Return error status with empty list
            transferResponse.put("status", "error");
            transferResponse.put("message", e.getMessage() != null ? e.getMessage() : "Failed to fetch transfers from HIE");
            transferResponse.put("data", new ArrayList<SimpleObject>());
            return transferResponse;
        }
    }
    
    /**
     * Parse the transfer response from HIE and map to SimpleObject format
     */
    private List<SimpleObject> parseTransferResponse(String jsonData) throws Exception {
        if(jsonData == null || jsonData.trim().isEmpty()){
            log.warn("Empty response from HIE");
            return new ArrayList<SimpleObject>();
        }
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonData);
        
        // Check if it's a Parameters resource
        if(!rootNode.has("resourceType") || !"Parameters".equals(rootNode.get("resourceType").asText())){
            log.warn("Unexpected response format from HIE");
            return new ArrayList<SimpleObject>();
        }
        
        // Find the bundle parameter
        JsonNode parameters = rootNode.get("parameter");
        if(parameters == null || !parameters.isArray()){
            log.warn("No parameters found in response");
            return new ArrayList<SimpleObject>();
        }
        
        JsonNode bundleNode = null;
        for(JsonNode param : parameters){
            if("bundle".equals(param.get("name").asText())){
                bundleNode = param.get("resource");
                break;
            }
        }
        
        if(bundleNode == null){
            log.warn("No bundle found in parameters");
            return new ArrayList<SimpleObject>();
        }
        
        // Get entries from bundle
        JsonNode entries = bundleNode.get("entry");
        if(entries == null || !entries.isArray() || entries.size() == 0){
            log.info("No transfer entries found in bundle");
            return new ArrayList<SimpleObject>();
        }
        
        List<SimpleObject> transferList = new ArrayList<SimpleObject>();
        
        // Process each entry
        for(JsonNode entry : entries){
            JsonNode resource = entry.get("resource");
            if(resource == null){
                continue;
            }
            
            SimpleObject transfer = new SimpleObject();
            
            // Map id: entry[].resource.id (always set, default to empty string if missing)
            if(resource.has("id") && !resource.get("id").isNull()){
                transfer.put("id", resource.get("id").asText());
            } else {
                transfer.put("id", "");
            }
            
            // Map status: entry[].resource.status (always set, default to "unknown" if missing)
            if(resource.has("status") && !resource.get("status").isNull()){
                transfer.put("status", resource.get("status").asText());
            } else {
                transfer.put("status", "unknown");
            }
            
            // Map subject: entry[].resource.subject.identifier.value (always set, default to empty string if missing)
            if(resource.has("subject")){
                JsonNode subject = resource.get("subject");
                if(subject.has("identifier") && !subject.get("identifier").isNull() && 
                   subject.get("identifier").has("value") && !subject.get("identifier").get("value").isNull()){
                    transfer.put("subject", subject.get("identifier").get("value").asText());
                } else {
                    transfer.put("subject", "");
                }
            } else {
                transfer.put("subject", "");
            }
            
            // Map date: entry[].resource.period.start (always set, default to empty string if missing)
            // Extract date part (YYYY-MM-DD) from datetime string if present
            if(resource.has("period") && !resource.get("period").isNull()){
                JsonNode period = resource.get("period");
                if(period.has("start") && !period.get("start").isNull()){
                    String startDateTime = period.get("start").asText();
                    // Extract date part (YYYY-MM-DD) from datetime string (e.g., "2026-01-10T13:34:35.513395Z" -> "2026-01-10")
                    if(startDateTime != null && !startDateTime.trim().isEmpty()){
                        // If it contains 'T', extract the date part before 'T'
                        if(startDateTime.contains("T")){
                            transfer.put("date", startDateTime.substring(0, startDateTime.indexOf("T")));
                        } else if(startDateTime.length() >= 10){
                            // If it's already just a date (YYYY-MM-DD), use it as is
                            transfer.put("date", startDateTime.substring(0, 10));
                        } else {
                            transfer.put("date", startDateTime);
                        }
                    } else {
                        transfer.put("date", "");
                    }
                } else {
                    transfer.put("date", "");
                }
            } else {
                transfer.put("date", "");
            }
            
            // Map hospitalization fields (always set, default to empty string if missing)
            if(resource.has("hospitalization")){
                JsonNode hospitalization = resource.get("hospitalization");
                
                // Map origin: entry[].resource.hospitalization.origin.display
                if(hospitalization.has("origin") && !hospitalization.get("origin").isNull() && 
                   hospitalization.get("origin").has("display") && !hospitalization.get("origin").get("display").isNull()){
                    transfer.put("origin", hospitalization.get("origin").get("display").asText());
                } else {
                    transfer.put("origin", "");
                }
                
                // Map destination: entry[].resource.hospitalization.destination.display
                if(hospitalization.has("destination") && !hospitalization.get("destination").isNull() && 
                   hospitalization.get("destination").has("display") && !hospitalization.get("destination").get("display").isNull()){
                    transfer.put("destination", hospitalization.get("destination").get("display").asText());
                } else {
                    transfer.put("destination", "");
                }
                
                // Map admitSource: entry[].resource.hospitalization.admitSource.coding[0].display
                if(hospitalization.has("admitSource")){
                    JsonNode admitSource = hospitalization.get("admitSource");
                    if(admitSource.has("coding") && admitSource.get("coding").isArray() && admitSource.get("coding").size() > 0){
                        JsonNode firstCoding = admitSource.get("coding").get(0);
                        if(firstCoding.has("display") && !firstCoding.get("display").isNull()){
                            transfer.put("admitSource", firstCoding.get("display").asText());
                        } else {
                            transfer.put("admitSource", "");
                        }
                    } else {
                        transfer.put("admitSource", "");
                    }
                } else {
                    transfer.put("admitSource", "");
                }
            } else {
                // If no hospitalization, set all fields to empty string
                transfer.put("origin", "");
                transfer.put("destination", "");
                transfer.put("admitSource", "");
            }
            
            transferList.add(transfer);
        }
        
        return transferList;
    }
    
    /**
     * Fetch transfers from HIE
     */
    private List<SimpleObject> fetchTransfersFromHie(String upid, String fromDate, String endDate) throws Exception {
        try(CloseableHttpClient httpClient = HttpUtils.getHieClient()){
            if(httpClient == null){
                throw new IllegalStateException("HIE client could not be created. Check HIE credentials configuration.");
            }
            
            // Build URL with parameters
            String url;
            if(fromDate != null && endDate != null){
                url = integrationConfig.getHieEndpointUrl("/shr/Encounter/$list-transfers", "Patient", upid, "fromDate", fromDate, "endDate", endDate);
            } else {
                url = integrationConfig.getHieEndpointUrl("/shr/Encounter/$list-transfers", "Patient", upid);
            }
            
            log.info("Requesting transfers from HIE: " + url);
            HttpGet httpGet = new HttpGet(url);
            
            try(CloseableHttpResponse response = httpClient.execute(httpGet)){
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity httpEntity = response.getEntity();
                String data = "";
                try{
                    data = EntityUtils.toString(httpEntity);
                } catch(Exception e){
                    log.warn("Could not read response body: " + e.getMessage());
                }
                
                log.info("HIE transfer request response status: " + statusCode);
                
                // Check if the response is an OperationOutcome (error response)
                if(data != null && !data.trim().isEmpty()){
                    try{
                        // Check for OperationOutcome
                        if(data.contains("\"resourceType\":\"OperationOutcome\"")){
                            String errorMessage = "HIE returned OperationOutcome error";
                            if(data.length() < 500){
                                errorMessage += ". Response: " + data;
                            }
                            throw new IllegalStateException(errorMessage);
                        }
                        
                        // Check HTTP status code
                        if(statusCode != 200){
                            String errorMessage = "HIE responded with status " + statusCode;
                            if(data.length() < 500){
                                errorMessage += ". Response: " + data;
                            }
                            throw new IllegalStateException(errorMessage);
                        }
                    } catch(IllegalStateException ise){
                        throw ise;
                    } catch(Exception parseException){
                        log.debug("Failed to parse response: " + parseException.getMessage());
                    }
                }
                
                // Parse the response data and map to SimpleObject
                return parseTransferResponse(data);
            }
        }
    }
    
    /**
     * Return sample transfer data (used as fallback or when HIE is disabled)
     * Always includes all required properties including status
     */
    // private SimpleObject getSampleTransferData(String upid, boolean activeOnly){
    //     SimpleObject transferData = new SimpleObject();
    //     transferData.put("id", "61762374-07e5-442b-90e7-b6e466dc324f");
    //     transferData.put("status", activeOnly ? "active" : "unknown"); // Always set
    //     transferData.put("subject", upid != null ? upid : "");
    //     transferData.put("origin", "Kacyiru HC");
    //     transferData.put("admitSource", "From accident/emergency department");
    //     transferData.put("destination", "Kibagabaga L2TH");
    //     return transferData;
    // }
}
