package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

@Controller
public class LabNotificationRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @RequestMapping(value = "/rest/v1/rwandaemr/lab/notify", method = RequestMethod.POST)
    @ResponseBody
    public Object notifyPatient(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        try {
            throw new RuntimeException("Lab result notifications are not yet implemented");
        } catch (Exception e) {
            log.error("Error notifying patient of lab results", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return Collections.singletonMap("error", e.getMessage());
        }
    }
}
