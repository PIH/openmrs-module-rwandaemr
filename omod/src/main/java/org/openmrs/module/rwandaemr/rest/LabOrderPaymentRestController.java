package org.openmrs.module.rwandaemr.rest;

import org.openmrs.Order;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment status of lab orders, for the lab order list and the lab results page
 */
@Controller
public class LabOrderPaymentRestController {

	@Autowired
	OrderService orderService;

	@Autowired
	OrderPaymentLookup orderPaymentLookup;

	/**
	 * @return the payment status of each order, by uuid, as found in the billing module
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/orderPayments", method = RequestMethod.GET)
	@ResponseBody
	public Object getPayments(@RequestParam(value = "order") List<String> orderUuids) {
		requirePrivilege(PrivilegeConstants.GET_ORDERS);
		List<Order> orders = getOrders(orderUuids);
		Map<Integer, OrderPayment> payments = orderPaymentLookup.getPayments(orders);
		SimpleObject ret = new SimpleObject();
		for (Order order : orders) {
			OrderPayment payment = payments.get(order.getOrderId());
			if (payment != null) {
				SimpleObject o = new SimpleObject();
				o.put("status", payment.getStatus().name());
				o.put("label", payment.getStatus().getLabel());
				o.put("lastPaymentDate", payment.getLastPaymentDate());
				ret.put(order.getUuid(), o);
			}
		}
		return ret;
	}

	@ExceptionHandler(APIAuthenticationException.class)
	@ResponseBody
	public ResponseEntity<?> handleAuthenticationException(APIAuthenticationException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
	}

	@ExceptionHandler({ APIException.class, IllegalArgumentException.class })
	@ResponseBody
	public ResponseEntity<?> handleBadRequest(RuntimeException e) {
		return ResponseEntity.badRequest().body(error(e.getMessage()));
	}

	private List<Order> getOrders(List<String> orderUuids) {
		List<Order> ret = new ArrayList<Order>();
		if (orderUuids != null) {
			for (String uuid : orderUuids) {
				Order order = orderService.getOrderByUuid(uuid);
				if (order == null) {
					throw new IllegalArgumentException("Order " + uuid + " not found");
				}
				ret.add(order);
			}
		}
		return ret;
	}

	private void requirePrivilege(String privilege) {
		if (!Context.hasPrivilege(privilege)) {
			throw new APIAuthenticationException("Privilege required: " + privilege);
		}
	}

	private Map<String, Object> error(String message) {
		Map<String, Object> ret = new LinkedHashMap<String, Object>();
		ret.put("success", false);
		ret.put("message", message);
		return ret;
	}
}
