/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr;

import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Location;
import org.openmrs.LocationTag;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientProgram;
import org.openmrs.PatientState;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.Visit;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.api.db.hibernate.ImmutableOrderInterceptor;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.util.PrivilegeConstants;
import org.openmrs.validator.ValidateUtil;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Service methods
 */
@Transactional
public class RwandaEmrServiceImpl extends BaseOpenmrsService implements RwandaEmrService {

	protected Log log = LogFactory.getLog(getClass());

	@Setter
	private DbSessionFactory sessionFactory;

	@Setter
	private EncounterService encounterService;

	@Transactional(readOnly = true)
	@Authorized(PrivilegeConstants.GET_OBS)
	@SuppressWarnings("unchecked")
	public List<Obs> getObsByOrder(Order order) {
		String query = "select o from Obs o where o.order = :order and o.voided = false";
		return sessionFactory.getCurrentSession().createQuery(query).setParameter("order", order).list();
	}

	@Transactional
	@Authorized(PrivilegeConstants.ADD_ENCOUNTERS)
	public void saveEncounters(List<Encounter> encounters) {
		for (Encounter encounter : encounters) {
			encounterService.saveEncounter(encounter);
		}
	}

	@Transactional
	@Authorized(PrivilegeConstants.EDIT_PATIENTS)
	public List<String> triggerSyncForPatient(Patient patient) {
		List<String> messages = new ArrayList<String>();
		Date now = new Date();
		addMessage(messages, "Triggered Sync for patient: " + patient.getId());

		Boolean originalDisableValidationValue = ValidateUtil.getDisableValidation();
		ImmutableOrderInterceptor orderInterceptor = Context.getRegisteredComponents(ImmutableOrderInterceptor.class).get(0);
		try {
			ValidateUtil.disableValidationForThread();
			orderInterceptor.addMutablePropertiesForThread("dateCreated");
			// Update patient
			patient.setDateChanged(now);

			// Update patient names
			for (PersonName name : patient.getNames()) {
				name.setDateChanged(now);
			}

			// Update patient addresses
			for (PersonAddress address : patient.getAddresses()) {
				address.setDateChanged(now);
			}

			// Update patient attributes
			for (PersonAttribute attribute : patient.getAttributes()) {
				attribute.setDateChanged(now);
			}

			// Update patient identifiers
			for (PatientIdentifier identifier : patient.getIdentifiers()) {
				identifier.setDateChanged(now);
			}

			addMessage(messages, "Saving Patient: " + patient.getId());
			Context.getPatientService().savePatient(patient);

			List<Relationship> relationships = Context.getPersonService().getRelationshipsByPerson(patient);
			addMessage(messages, "Found " + relationships.size() + " relationships to save");
			for (Relationship relationship : relationships) {
				relationship.setDateChanged(now);
				addMessage(messages, "Saving Relationship: " + relationship.getId());
				Context.getPersonService().saveRelationship(relationship);
			}

			List<Encounter> encounters = Context.getEncounterService().getEncountersByPatient(patient);
			addMessage(messages, "Found " + encounters.size() + " encounters to save");
			for (Encounter encounter : encounters) {
				encounter.setDateChanged(now);
				if (encounter.getVisit() != null) {
					encounter.getVisit().setDateChanged(now);
				}
				for (Order order : encounter.getOrders()) {
					order.setDateCreated(oneSecondLater(order.getDateCreated()));
				}
				for (Obs obs : encounter.getObs()) {
					obs.setDateChanged(now);
				}
				for (EncounterProvider encounterProvider : encounter.getEncounterProviders()) {
					encounterProvider.setDateChanged(now);
					if (encounterProvider.getEncounterRole() != null) {
						encounterProvider.getEncounterRole().setDateChanged(now);
					}
				}
				addMessage(messages, "Saving Encounter: " + encounter.getId());
				Context.getEncounterService().saveEncounter(encounter);
			}

			List<Visit> visits = Context.getVisitService().getVisitsByPatient(patient);
			addMessage(messages, "Found " + visits.size() + " visits to save");
			for (Visit visit : visits) {
				visit.setDateChanged(now);
				addMessage(messages, "Saving Visit: " + visit.getId());
				Context.getVisitService().saveVisit(visit);
			}

			List<Obs> topLevelObs = new ArrayList<Obs>();
			for (Obs obs : Context.getObsService().getObservationsByPerson(patient)) {
				if (obs.getEncounter() == null) {
					topLevelObs.add(obs);
				}
			}
			addMessage(messages, "Found " + topLevelObs.size() + " top level obs to save");
			for (Obs obs : topLevelObs) {
				obs.setDateCreated(oneSecondLater(obs.getDateCreated()));
				addMessage(messages, "Saving Obs: " + obs.getId());
				Context.getObsService().saveObs(obs, "Resaving patient and all data for sync");
			}

			List<PatientProgram> patientPrograms = Context.getProgramWorkflowService().getPatientPrograms(patient, null, null, null, null, null, true);
			addMessage(messages, "Found " + patientPrograms.size() + " patient programs to save");
			for (PatientProgram patientProgram : patientPrograms) {
				patientProgram.setDateChanged(now);
				for (PatientState patientState : patientProgram.getStates()) {
					patientState.setDateChanged(now);
				}
				addMessage(messages, "Saving Patient Program: " + patientProgram.getId());
				Context.getProgramWorkflowService().savePatientProgram(patientProgram);
			}
		}
		catch (Exception e) {
			addMessage(messages, "Error: " + e.getMessage());
			throw new RuntimeException(e);
		}
		finally {
			ValidateUtil.resumeValidationForThread();
			orderInterceptor.removeMutablePropertiesForThread();
		}

		addMessage(messages, "Trigger Sync Completed: " + patient.getId());

		return messages;
	}

	@Override
	@Transactional
	@Authorized(PrivilegeConstants.MANAGE_LOCATIONS)
	public void updateVisitAndLoginLocations(List<Location> visitLocations, List<Location> loginLocations) {
		LocationService locationService = Context.getLocationService();
		LocationTag visitLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_VISITS);
		LocationTag loginLocationTag = locationService.getLocationTagByName(EmrApiConstants.LOCATION_TAG_SUPPORTS_LOGIN);
		for (Location l : locationService.getAllLocations(true)) {
			boolean locationChanged = false;
			boolean isVisitLocation = l.getTags() != null && l.getTags().contains(visitLocationTag);
			boolean isLoginLocation = l.getTags() != null && l.getTags().contains(loginLocationTag);
			boolean shouldBeVisitLocation = visitLocations.contains(l);
			boolean shouldBeLoginLocation = loginLocations.contains(l);
			if (isVisitLocation && !shouldBeVisitLocation) {
				l.removeTag(visitLocationTag);
				locationChanged = true;
			}
			if (isLoginLocation && !shouldBeLoginLocation) {
				l.removeTag(loginLocationTag);
				locationChanged = true;
			}
			if (!isVisitLocation && shouldBeVisitLocation) {
				l.addTag(visitLocationTag);
				locationChanged = true;
			}
			if (!isLoginLocation && shouldBeLoginLocation) {
				l.addTag(loginLocationTag);
				locationChanged = true;
			}
			if (locationChanged) {
				locationService.saveLocation(l);
			}
		}
	}

	protected void addMessage(List<String> messages, String message) {
		messages.add(message);
		log.info(message);
	}

	private Date oneSecondLater(Date date) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.SECOND, 1);
		return cal.getTime();
	}
}
