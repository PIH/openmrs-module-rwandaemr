/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.event;

import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches for new lab test orders and adds the patient to the Laboratory service point queue,
 * unless the patient is already actively queued there.
 * Only orders of the lab test order type (pihapps.labs.labOrderType, see {@link LabOrderConfig}) count:
 * radiology, pathology and other test orders do not send the patient to the Laboratory.
 * This replaces the former behavior of creating a mohappointment Laboratory appointment.
 */
@Component
public class CreateLabQueueEntryEventHandler implements OpenmrsObjectEventHandler {

	private static final Logger log = LoggerFactory.getLogger(CreateLabQueueEntryEventHandler.class);

	static final String REASON = "Laboratory test ordered";

	@Override
	public void beforeTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events) {
		log.trace("beforeTransactionCompletion.  transactionDepth = {}; events = {}", transactionDepth, events);
		if (transactionDepth != 1 || events == null) {
			return;
		}
		Map<Patient, TestOrder> firstOrderByPatient = getNewTestOrdersByPatient(events);
		if (firstOrderByPatient.isEmpty()) {
			return;
		}
		for (Map.Entry<Patient, TestOrder> entry : firstOrderByPatient.entrySet()) {
			try {
				getQueueService().addPatientToLaboratoryQueue(entry.getKey(), entry.getValue().getEncounter(), REASON);
			}
			catch (Exception e) {
				// Never block saving the order because of the queue
				log.error("Unable to add patient {} to the laboratory queue", entry.getKey(), e);
			}
		}
	}

	/**
	 * @return the first new lab test order of each patient; none if the lab test order type is not known
	 */
	Map<Patient, TestOrder> getNewTestOrdersByPatient(List<OpenmrsObjectEvent> events) {
		Map<Patient, TestOrder> ret = new LinkedHashMap<>();
		OrderType labTestOrderType = null;
		boolean labTestOrderTypeLoaded = false;
		for (OpenmrsObjectEvent event : events) {
			if (event.getOperation() == OpenmrsObjectEvent.Operation.CREATE && event.getOpenmrsObject() instanceof TestOrder) {
				TestOrder order = (TestOrder) event.getOpenmrsObject();
				if (order.getAction() == Order.Action.NEW && order.getPatient() != null) {
					if (!labTestOrderTypeLoaded) {
						labTestOrderType = getLabTestOrderType();
						labTestOrderTypeLoaded = true;
						if (labTestOrderType == null) {
							log.warn("Not adding patients to the laboratory queue: the lab test order type is not configured "
									+ "(global property pihapps.labs.labOrderType)");
							return ret;
						}
					}
					if (labTestOrderType.equals(order.getOrderType())) {
						ret.putIfAbsent(order.getPatient(), order);
					}
				}
			}
		}
		return ret;
	}

	/**
	 * @return the order type of lab tests, configured for the pihapps lab pages, or null if it cannot be found
	 */
	OrderType getLabTestOrderType() {
		try {
			List<LabOrderConfig> configs = Context.getRegisteredComponents(LabOrderConfig.class);
			return configs.isEmpty() ? null : configs.get(0).getLabTestOrderType();
		}
		catch (Exception e) {
			log.warn("Unable to find the lab test order type", e);
			return null;
		}
	}

	QueueService getQueueService() {
		return Context.getService(QueueService.class);
	}
}
