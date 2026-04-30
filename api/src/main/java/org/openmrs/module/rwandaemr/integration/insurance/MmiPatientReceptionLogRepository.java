/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration.insurance;

import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MmiPatientReceptionLogRepository {

	private final DbSessionFactory dbSessionFactory;

	public MmiPatientReceptionLogRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	@Transactional(readOnly = true)
	public boolean hasSuccessfulReceptionForVisit(Integer visitId) {
		if (visitId == null) {
			return false;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select count(1) from mmi_patient_reception_log where visit_id = :visitId and status = :status"
		);
		query.setParameter("visitId", visitId);
		query.setParameter("status", "SUCCESS");
		Number count = (Number) query.uniqueResult();
		return count != null && count.intValue() > 0;
	}

	@Transactional
	public void saveLogEntry(MmiPatientReceptionLogEntry entry) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"insert into mmi_patient_reception_log (" +
						"patient_id, visit_id, encounter_id, insurance_policy_id, insurance_name, insurance_card_no, " +
						"patient_identifier, facility_fosa_id, patient_type, prescription_required, request_payload, response_payload, " +
						"response_code, status, reception_number, bp_code, reception_status, error_message, creator, date_created" +
						") values (" +
						":patientId, :visitId, :encounterId, :insurancePolicyId, :insuranceName, :insuranceCardNo, " +
						":patientIdentifier, :facilityFosaId, :patientType, :prescriptionRequired, :requestPayload, :responsePayload, " +
						":responseCode, :status, :receptionNumber, :bpCode, :receptionStatus, :errorMessage, :creator, :dateCreated" +
						")"
		);
		query.setParameter("patientId", entry.getPatientId());
		query.setParameter("visitId", entry.getVisitId());
		query.setParameter("encounterId", entry.getEncounterId());
		query.setParameter("insurancePolicyId", entry.getInsurancePolicyId());
		query.setParameter("insuranceName", entry.getInsuranceName());
		query.setParameter("insuranceCardNo", entry.getInsuranceCardNo());
		query.setParameter("patientIdentifier", entry.getPatientIdentifier());
		query.setParameter("facilityFosaId", entry.getFacilityFosaId());
		query.setParameter("patientType", entry.getPatientType());
		query.setParameter("prescriptionRequired", Boolean.TRUE.equals(entry.getPrescriptionRequired()));
		query.setParameter("requestPayload", entry.getRequestPayload());
		query.setParameter("responsePayload", entry.getResponsePayload());
		query.setParameter("responseCode", entry.getResponseCode());
		query.setParameter("status", entry.getStatus());
		query.setParameter("receptionNumber", entry.getReceptionNumber());
		query.setParameter("bpCode", entry.getBpCode());
		query.setParameter("receptionStatus", entry.getReceptionStatus());
		query.setParameter("errorMessage", entry.getErrorMessage());
		query.setParameter("creator", entry.getCreator());
		query.setParameter("dateCreated", entry.getDateCreated());
		query.executeUpdate();
	}
}
