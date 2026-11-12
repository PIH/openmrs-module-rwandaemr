package org.openmrs.module.rwandaemr.labs;

import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.util.ConfigUtil;

import java.util.List;

/**
 * Whether lab results must be validated before they are released, as pihapps decides
 * (see LabOrderConfig.GP_REQUIRE_RESULT_VALIDATION)
 */
public class LabResultValidation {

	private LabResultValidation() {
	}

	/**
	 * @return whether pihapps lab result validation is on
	 */
	public static boolean isOn() {
		return "true".equalsIgnoreCase(ConfigUtil.getProperty(LabOrderConfig.GP_REQUIRE_RESULT_VALIDATION, "false"));
	}

	/**
	 * @return whether the results of the order must be validated: validation is on and it is a lab test order (any
	 * test order when the lab test order type cannot be known)
	 */
	public static boolean isRequired(Order order) {
		if (!isOn() || !(order instanceof TestOrder)) {
			return false;
		}
		List<LabOrderConfig> configs = Context.getRegisteredComponents(LabOrderConfig.class);
		OrderType labTestOrderType = configs.isEmpty() ? null : configs.get(0).getLabTestOrderType();
		return labTestOrderType == null || labTestOrderType.equals(order.getOrderType());
	}
}
