package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * REST API endpoint for patient transfer information
 * Returns transfer data in JSON format
 */
@Controller
public class TransferRestController {

    protected final Log log = LogFactory.getLog(getClass());

    /**
     * Get transfer information
     * 
     * @param request HTTP request
     * @param response HTTP response
     * @return Transfer information as JSON
     * @throws ResponseException
     */
    @RequestMapping(value = "/rest/v1/rwandaemr/transfer", method = RequestMethod.GET)
    @ResponseBody
    public Object getTransfer(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        log.info("Transfer REST API endpoint called");
        
        // TODO: Implement logic to fetch real transfer data
        
        // For now, returning sample data as requested
        SimpleObject transferData = new SimpleObject();
        transferData.put("id", "61762374-07e5-442b-90e7-b6e466dc324f");
        transferData.put("status", "unknown");
        transferData.put("subject", "251218-0022-4872");
        transferData.put("origin", "Kacyiru HC");
        transferData.put("admitSource", "From accident/emergency department");
        transferData.put("destination", "Kibagabaga L2TH");
        
        log.debug("Returning transfer data: " + transferData);
        return transferData;
    }
}
