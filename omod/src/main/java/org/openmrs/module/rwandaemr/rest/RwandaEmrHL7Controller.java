package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.module.rwandaemr.radiology.OrderToORMTranslator;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class RwandaEmrHL7Controller {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    OrderToORMTranslator orderToORMTranslator;

    @Autowired
    OrderService orderService;

    @RequestMapping(value = "/rest/v1/rwandaemr/hl7/{orderUuid}/orm001", method = RequestMethod.GET)
    @ResponseBody
    public Object getORMO01(HttpServletRequest request, HttpServletResponse response,
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
                    hl7Message = orderToORMTranslator.toORM_001((TestOrder) order);
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
}