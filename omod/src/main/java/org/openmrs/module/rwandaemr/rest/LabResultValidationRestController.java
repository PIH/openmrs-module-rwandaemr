package org.openmrs.module.rwandaemr.rest;

import lombok.Data;
import org.openmrs.Order;
import org.openmrs.User;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup;
import org.openmrs.module.rwandaemr.billing.OrderPaymentLookup.OrderPayment;
import org.openmrs.module.rwandaemr.labs.LabResultValidationService;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation and payment status of lab orders, for the lab order list and the lab results page
 */
@Controller
public class LabResultValidationRestController {

	@Autowired
	OrderService orderService;

	@Autowired
	LabResultValidationService labResultValidationService;

	@Autowired
	OrderPaymentLookup orderPaymentLookup;

	/**
	 * @return for each order, by uuid: whether its results await validation, who entered them, and whether the current
	 * user may validate them
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/resultValidation", method = RequestMethod.GET)
	@ResponseBody
	public Object getValidation(@RequestParam(value = "order") List<String> orderUuids) {
		requirePrivilege(PrivilegeConstants.GET_ORDERS);
		User user = Context.getAuthenticatedUser();
		boolean mayValidate = Context.hasPrivilege(LabResultValidationService.PRIVILEGE_VALIDATE_RESULTS);
		SimpleObject ret = new SimpleObject();
		for (Order order : getOrders(orderUuids)) {
			List<String> enteredBy = new ArrayList<String>();
			boolean enteredByUser = false;
			for (User editor : labResultValidationService.getResultEditors(order)) {
				enteredBy.add(editor.getPersonName() != null ? editor.getPersonName().getFullName() : editor.getUsername());
				enteredByUser = enteredByUser || editor.equals(user);
			}
			SimpleObject o = new SimpleObject();
			o.put("awaitingValidation", LabResultValidationService.isAwaitingValidation(order));
			o.put("enteredBy", enteredBy);
			o.put("enteredByCurrentUser", enteredByUser);
			o.put("canValidate", mayValidate && !enteredByUser && LabResultValidationService.isAwaitingValidation(order));
			ret.put(order.getUuid(), o);
		}
		return ret;
	}

	@RequestMapping(value = "/rest/v1/rwandaemr/lab/resultValidation", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> decide(@RequestBody ValidationRequest request) {
		try {
			List<Order> orders = getOrders(request.getOrders());
			if ("VALIDATE".equals(request.getAction())) {
				labResultValidationService.validate(orders);
			}
			else if ("RETURN".equals(request.getAction())) {
				labResultValidationService.returnForCorrection(orders, request.getReason());
			}
			else {
				return ResponseEntity.badRequest().body(error("Action must be VALIDATE or RETURN"));
			}
			return ResponseEntity.ok(new SimpleObject().add("success", true));
		}
		catch (APIAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
		}
		catch (APIException | IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(error(e.getMessage()));
		}
	}

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
		Map<String, Object> ret = new LinkedHashMap<>();
		ret.put("success", false);
		ret.put("message", message);
		return ret;
	}

	@Data
	public static class ValidationRequest {
		private List<String> orders;
		private String action;
		private String reason;
	}
}
