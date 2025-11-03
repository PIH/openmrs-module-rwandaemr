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
import org.openmrs.annotation.Handler;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohappointment.model.Appointment;
import org.openmrs.module.mohappointment.model.Services;
import org.openmrs.module.mohappointment.utils.AppointmentUtil;
import org.openmrs.util.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * The goal of this interceptor is to listen for all data being saved via Hibernate, and to trigger events
 * that can participate at various points of the transaction lifecycle.  In particular, this enables watching for
 * changes to OpenmrsObjects that are being saved to the database, and modifying or extending the data to be saved
 * within the same transaction.
 */
@Component
@Handler(supports = { TestOrder.class })
public class CreateLabAppointmentEventHandler implements EventHandler<TestOrder> {

	private static final Logger log = LoggerFactory.getLogger(CreateLabAppointmentEventHandler.class);

	@Autowired
	ConceptService conceptService;

	@Override
	public void handleCreatedEntity(TestOrder entity) {
		log.debug("createLabAppointmentEventHandler " + entity.getUuid());
		if (entity.getAction() == Order.Action.DISCONTINUE) {
			log.debug("Not creating lab appointment.  Order is a discontinue order");
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

		Patient patient = entity.getPatient();
		Date currentDate = new Date();
		User currentUser = Context.getAuthenticatedUser();
		boolean alreadyHasAppointment = AppointmentUtil.alreadyHasAppointmentThere(patient, currentDate, labService);
		if (alreadyHasAppointment) {
			log.debug("Not creating lab appointment.  Patient {} already has a lab appointment not yet attended on {}", patient, currentDate);
			return;
		}

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
