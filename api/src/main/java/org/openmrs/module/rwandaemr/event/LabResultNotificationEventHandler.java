package org.openmrs.module.rwandaemr.event;

import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationConfig;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sends a patient notification after a laboratory order with results is completed. */
@Component
public class LabResultNotificationEventHandler implements OpenmrsObjectEventHandler {

	private static final Logger log = LoggerFactory.getLogger(LabResultNotificationEventHandler.class);

	private final LabNotificationConfig config;
	private final LabNotificationService notificationService;

	@Autowired
	public LabResultNotificationEventHandler(LabNotificationConfig config,
	                                         LabNotificationService notificationService) {
		this.config = config;
		this.notificationService = notificationService;
	}

	@Override
	public void afterTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events, int status) {
		if (transactionDepth != 1 || status != Status.STATUS_COMMITTED || !config.isEnabled() || events == null) {
			return;
		}
		Set<String> notifiedOrders = new HashSet<String>();
		for (OpenmrsObjectEvent event : events) {
			if (event.getOperation() != OpenmrsObjectEvent.Operation.UPDATE ||
					!(event.getOpenmrsObject() instanceof TestOrder)) {
				continue;
			}
			Order order = (Order) event.getOpenmrsObject();
			if (!Order.FulfillerStatus.COMPLETED.equals(order.getFulfillerStatus()) ||
					order.getUuid() == null || !notifiedOrders.add(order.getUuid())) {
				continue;
			}
			try {
				notificationService.notifyPatient(order.getUuid(), null, null, false);
			}
			catch (IllegalStateException e) {
				// Includes an order that was already notified; result completion must remain successful.
				log.info("Lab result SMS was not sent for order {}: {}", order.getUuid(), e.getMessage());
			}
			catch (RuntimeException e) {
				log.warn("Unable to notify patient that lab results are available for order " + order.getUuid(), e);
			}
		}
	}
}
