/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.event;

import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * This handler watches for any new Obs that are saved, and if an Obs is associated with an unfulfilled Test Order,
 * then this marks that order as fulfilled
 */
@Component
public class MarkOrderAsFulfilledEventHandler implements OpenmrsObjectEventHandler {

	private static final Logger log = LoggerFactory.getLogger(MarkOrderAsFulfilledEventHandler.class);

	final OrderService orderService;

	@Autowired
	public MarkOrderAsFulfilledEventHandler(OrderService orderService) {
		this.orderService = orderService;
	}

	@Override
	public void beforeTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events) {
		log.trace("beforeTransactionCompletion.  transactionDepth = {}; events = {}", transactionDepth, events);
		if (transactionDepth == 1 && events != null) {
			for (OpenmrsObjectEvent event : events) {
				try {
					if (event.getOperation() == OpenmrsObjectEvent.Operation.CREATE && event.getOpenmrsObject() instanceof Obs) {
						log.trace("Saving new obs: {}", event.getOpenmrsObject());
						Order order = ((Obs) event.getOpenmrsObject()).getOrder();
						if (order instanceof TestOrder && order.getOrderId() != null && order.getFulfillerStatus() == null) {
							order = orderService.getOrder(order.getOrderId());
							log.debug("Marking order associated with obs as fulfilled: {}", order);
							orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.COMPLETED, "Marked as fulfilled due to associated obs");
						}
					}
				}
				catch (Exception e) {
					log.warn("Error marking order as fulfilled: {}", e.getMessage());
				}
			}
		}
	}
}
