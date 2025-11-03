/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.event;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.User;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohappointment.model.Appointment;
import org.openmrs.module.mohappointment.model.Services;
import org.openmrs.module.mohappointment.utils.AppointmentUtil;
import org.openmrs.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This handler watches for any new Test Orders that are created, and if the patient does not already have
 * an appointment with the Laboratory Service, it creates a new appointment for the patient
 */
@Component
public class CreateLabAppointmentEventHandler implements OpenmrsObjectEventHandler {

	private static final Logger log = LoggerFactory.getLogger(CreateLabAppointmentEventHandler.class);

	final ConceptService conceptService;

	final OrderService orderService;

	@Autowired
	public CreateLabAppointmentEventHandler(ConceptService conceptService, OrderService orderService) {
		this.conceptService = conceptService;
		this.orderService = orderService;
	}

	@Override
	public void beforeTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events) {
		log.trace("beforeTransactionCompletion.  transactionDepth = {}; events = {}", transactionDepth, events);
		if (transactionDepth == 1) {
			Map<Patient, List<Order>> testOrders = new HashMap<>();
			if (events != null) {
				for (OpenmrsObjectEvent event : events) {
					if (event.getOpenmrsObject() instanceof TestOrder) {
						TestOrder order = (TestOrder) event.getOpenmrsObject();
						if (order.getAction() == Order.Action.NEW) {
							testOrders.computeIfAbsent(order.getPatient(), k -> new ArrayList<>()).add(order);
						}
					}
				}
			}
			if (testOrders.isEmpty()) {
				log.trace("No new orders found");
				return;
			}

			String labServiceLookup = ConfigUtil.getGlobalProperty("laboratorymanagement.appointmentInLaboratoryService");
			if (StringUtils.isBlank(labServiceLookup)) {
				log.error("Not creating lab appointment.  Missing global property value: laboratorymanagement.appointmentInLaboratoryService");
				return;
			}
			Concept labServiceConcept = conceptService.getConceptByReference(labServiceLookup);
			if (labServiceConcept == null) {
				log.error("Not creating lab appointment.  No concept found from global property service: " + labServiceLookup);
				return;
			}
			Services labService = AppointmentUtil.getServiceByConcept(labServiceConcept);
			if (labService == null) {
				log.error("Not creating lab appointment.  No lab service found for concept " + labServiceConcept);
				return;
			}

			Date currentDate = new Date();
			User currentUser = Context.getAuthenticatedUser();

			for (Patient patient : testOrders.keySet()) {
				log.debug("Found {} new test orders for patient {}", testOrders.size(), patient);
				boolean alreadyHasAppointment = AppointmentUtil.alreadyHasAppointmentThere(patient, currentDate, labService);
				if (alreadyHasAppointment) {
					log.debug("Not creating lab appointment.  Patient {} already has a lab appointment not yet attended on {}", patient, currentDate);
				} else {
					log.debug("Creating a new lab appointment for {} on {}", patient, currentDate);
					Appointment waitingAppointment = new Appointment();
					waitingAppointment.setPatient(patient);
					waitingAppointment.setService(labService);
					waitingAppointment.setLocation(Context.getLocationService().getDefaultLocation());
					waitingAppointment.setAppointmentDate(currentDate);
					waitingAppointment.setAttended(false);
					waitingAppointment.setVoided(false);
					waitingAppointment.setCreatedDate(currentDate);
					waitingAppointment.setCreator(currentUser);
					waitingAppointment.setProvider(currentUser.getPerson());
					waitingAppointment.setNote("This is a waiting patient to the Laboratory");
					AppointmentUtil.saveWaitingAppointment(waitingAppointment);
					log.debug("Lab appointment created for {} on {}", patient, currentDate);
				}
			}
		}
	}
}
