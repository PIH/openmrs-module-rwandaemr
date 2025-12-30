package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceEligibilityProvider;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class InsuranceEligibilityRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    InsuranceEligibilityProvider insuranceEligibilityProvider;

    @RequestMapping(value = "/rest/v1/rwandaemr/insurance/eligibility", method = RequestMethod.GET)
    @ResponseBody
    public Object checkInsuranceEligibility(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        String type = request.getParameter("type");
        String identifier = request.getParameter("identifier");
        IntegrationResponse ret = insuranceEligibilityProvider.checkEligibility(type, identifier);
        return ret;
    }
}