package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.lab.LabPrintConfig;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class LabPrintConfigRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    LabPrintConfig labPrintConfig;

    @RequestMapping(value = "/rest/v1/rwandaemr/lab/printConfig", method = RequestMethod.GET)
    @ResponseBody
    public Object getLabPrintConfig(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("facilityName",      labPrintConfig.getFacilityName());
        config.put("facilityAddress",   labPrintConfig.getFacilityAddress());
        config.put("facilityEmail",     labPrintConfig.getFacilityEmail());
        config.put("facilityPhone",     labPrintConfig.getFacilityPhone());
        config.put("facilityLogo",  labPrintConfig.getFacilityLogo());
        config.put("labTechName",   labPrintConfig.getLabTechName());
        config.put("labTechStamp",  labPrintConfig.getLabTechStamp());
        return config;
    }
}
