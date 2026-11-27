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

import java.util.Calendar;
import java.util.Date;

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
				"select count(1) from mmi_patient_reception_log where visit_id = :visitId and status = :status " +
						"and reception_number is not null and reception_number <> ''"
		);
		query.setParameter("visitId", visitId);
		query.setParameter("status", "SUCCESS");
		Number count = (Number) query.uniqueResult();
		return count != null && count.intValue() > 0;
	}

	@Transactional(readOnly = true)
	public boolean hasSuccessfulReceptionForInsuranceCardOnDate(String insuranceCardNo, Date date) {
		return getSuccessfulReceptionNumberForInsuranceCardOnDate(insuranceCardNo, date) != null;
	}

	@Transactional(readOnly = true)
	public String getLatestSuccessfulReceptionNumberForInsuranceCard(String insuranceCardNo) {
		if (insuranceCardNo == null || insuranceCardNo.trim().isEmpty()) {
			return null;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select reception_number from mmi_patient_reception_log " +
						"where status = :status " +
						"and reception_number is not null and reception_number <> '' " +
						"and (insurance_card_no = :insuranceCardNo or patient_identifier = :insuranceCardNo) " +
						"order by date_created desc"
		);
		query.setParameter("status", "SUCCESS");
		query.setParameter("insuranceCardNo", insuranceCardNo.trim());
		query.setMaxResults(1);
		Object value = query.uniqueResult();
		return value == null ? null : value.toString();
	}

	@Transactional(readOnly = true)
	public String getSuccessfulReceptionNumberForInsuranceCardOnDate(String insuranceCardNo, Date date) {
		if (insuranceCardNo == null || insuranceCardNo.trim().isEmpty() || date == null) {
			return null;
		}
		Date startOfDay = startOfDay(date);
		Date startOfNextDay = addDays(startOfDay, 1);
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select reception_number from mmi_patient_reception_log " +
						"where status = :status " +
						"and reception_number is not null and reception_number <> '' " +
						"and date_created >= :startOfDay and date_created < :startOfNextDay " +
						"and (insurance_card_no = :insuranceCardNo or patient_identifier = :insuranceCardNo) " +
						"order by date_created desc"
		);
		query.setParameter("status", "SUCCESS");
		query.setParameter("insuranceCardNo", insuranceCardNo.trim());
		query.setParameter("startOfDay", startOfDay);
		query.setParameter("startOfNextDay", startOfNextDay);
		query.setMaxResults(1);
		Object value = query.uniqueResult();
		return value == null ? null : value.toString();
	}

	/**
	 * @return the latest successful reception log entry for this MMI reception number, or null if this EMR never
	 * opened it. Only patientId, visitId, insuranceCardNo, facilityFosaId, patientType, receptionNumber and dateCreated
	 * are filled in.
	 */
	@Transactional(readOnly = true)
	public MmiPatientReceptionLogEntry getSuccessfulReception(String receptionNumber) {
		if (receptionNumber == null || receptionNumber.trim().isEmpty()) {
			return null;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select patient_id, visit_id, insurance_card_no, facility_fosa_id, patient_type, reception_number, date_created " +
						"from mmi_patient_reception_log " +
						"where status = :status and reception_number = :receptionNumber " +
						"order by date_created desc"
		);
		query.setParameter("status", "SUCCESS");
		query.setParameter("receptionNumber", receptionNumber.trim());
		query.setMaxResults(1);
		Object[] row = (Object[]) query.uniqueResult();
		if (row == null) {
			return null;
		}
		MmiPatientReceptionLogEntry entry = new MmiPatientReceptionLogEntry();
		entry.setPatientId(row[0] == null ? null : ((Number) row[0]).intValue());
		entry.setVisitId(row[1] == null ? null : ((Number) row[1]).intValue());
		entry.setInsuranceCardNo(row[2] == null ? null : row[2].toString());
		entry.setFacilityFosaId(row[3] == null ? null : row[3].toString());
		entry.setPatientType(row[4] == null ? null : row[4].toString());
		entry.setReceptionNumber(row[5] == null ? null : row[5].toString());
		entry.setDateCreated(row[6] instanceof Date ? (Date) row[6] : null);
		return entry;
	}

	/**
	 * Records the patient type MMI now uses for this reception, after /patient-reception/change-type succeeded
	 */
	@Transactional
	public int updatePatientType(String receptionNumber, String patientType) {
		if (receptionNumber == null || receptionNumber.trim().isEmpty()) {
			return 0;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"update mmi_patient_reception_log set patient_type = :patientType " +
						"where status = :status and reception_number = :receptionNumber"
		);
		query.setParameter("patientType", patientType);
		query.setParameter("status", "SUCCESS");
		query.setParameter("receptionNumber", receptionNumber.trim());
		return query.executeUpdate();
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

	private Date startOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}

	private Date addDays(Date date, int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.DATE, days);
		return calendar.getTime();
	}
}
