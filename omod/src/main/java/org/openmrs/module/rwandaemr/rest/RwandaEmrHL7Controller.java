package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.module.rwandaemr.radiology.OrderToORMO01Translator;
import org.openmrs.module.rwandaemr.radiology.PatientToADTA08Translator;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class RwandaEmrHL7Controller {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    PatientToADTA08Translator patientToADTTranslator;

    @Autowired
    OrderToORMO01Translator orderToORMTranslator;

    @Autowired
    RadiologyOrderEventListener radiologyOrderEventListener;

    @Autowired
    PatientService patientService;

    @Autowired
    OrderService orderService;

    @RequestMapping(value = "/rest/v1/rwandaemr/hl7/{orderUuid}/orm001", method = RequestMethod.GET)
    @ResponseBody
    public Object getORMO01(HttpServletRequest request, HttpServletResponse response,
                            @RequestParam(name = "action", required = false) String action,
                            @PathVariable("orderUuid") String orderUuid) throws ResponseException {
        SimpleObject ret = new SimpleObject();
        Order order = orderService.getOrderByUuid(orderUuid);
        String hl7Message = "";
        String errorMessage = "";
        if (order == null) {
            errorMessage = "Order with UUID " + orderUuid + " not found";
        }
        else {
            if (order instanceof TestOrder) {
                try {
                    TestOrder testOrder = (TestOrder) order;
                    if ("send".equals(action)) {
                        radiologyOrderEventListener.sendRadiologyOrderMessage(testOrder);
                    }
                    hl7Message = orderToORMTranslator.toORM_O01(testOrder).encode();
                }
                catch (Exception e) {
                    errorMessage = e.getMessage();
                }
            }
            else {
                errorMessage = "Order is not a TestOrder";
            }
        }
        ret.put("hl7Message", hl7Message);
        ret.put("errorMessage", errorMessage);
        return ret;
    }

    @RequestMapping(value = "/rest/v1/rwandaemr/hl7/{patientUuid}/adta08", method = RequestMethod.GET)
    @ResponseBody
    public Object getADTA08(HttpServletRequest request, HttpServletResponse response,
                            @PathVariable("patientUuid") String patientUuid) throws ResponseException {
        SimpleObject ret = new SimpleObject();
        Patient patient = patientService.getPatientByUuid(patientUuid);
        String hl7Message = "";
        String errorMessage = "";
        if (patient == null) {
            errorMessage = "Patient with UUID " + patientUuid + " not found";
        }
        try {
            hl7Message = patientToADTTranslator.toADT_A08(patient);
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
        }
        ret.put("hl7Message", hl7Message);
        ret.put("errorMessage", errorMessage);
        return ret;
    }
}