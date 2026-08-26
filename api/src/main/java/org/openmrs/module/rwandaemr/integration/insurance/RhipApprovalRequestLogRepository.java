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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class RhipApprovalRequestLogRepository {

	private final DbSessionFactory dbSessionFactory;

	public RhipApprovalRequestLogRepository(@Autowired DbSessionFactory dbSessionFactory) {
		this.dbSessionFactory = dbSessionFactory;
	}

	@Transactional
	public void save(RhipApprovalRequestLogEntry entry) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"insert into rhip_approval_request_log (" +
						"patient_id, insurance_policy_id, insurance_type, insurance_name, insurance_card_no, " +
						"patient_identifier, facility_fosa_id, product_code, product_name, product_type, " +
						"requested_quantity, requested_unit_price, diagnosis_ids, clinical_knowledge, " +
						"practitioner_license_number, approval_code, approval_status, message, request_payload, " +
						"response_payload, response_code, creator, date_created, changed_by, date_changed" +
						") values (" +
						":patientId, :insurancePolicyId, :insuranceType, :insuranceName, :insuranceCardNo, " +
						":patientIdentifier, :facilityFosaId, :productCode, :productName, :productType, " +
						":requestedQuantity, :requestedUnitPrice, :diagnosisIds, :clinicalKnowledge, " +
						":practitionerLicenseNumber, :approvalCode, :approvalStatus, :message, :requestPayload, " +
						":responsePayload, :responseCode, :creator, :dateCreated, :changedBy, :dateChanged" +
						")"
		);
		bindEntry(query, entry);
		query.executeUpdate();
	}

	@Transactional
	public void updateStatus(Integer id, String approvalStatus, String approvalCode, String message,
	                         String responsePayload, Integer responseCode, Integer changedBy) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"update rhip_approval_request_log set approval_status = :approvalStatus, " +
						"approval_code = :approvalCode, message = :message, response_payload = :responsePayload, " +
						"response_code = :responseCode, changed_by = :changedBy, date_changed = :dateChanged " +
						"where rhip_approval_request_log_id = :id"
		);
		query.setParameter("id", id);
		query.setParameter("approvalStatus", approvalStatus);
		query.setParameter("approvalCode", approvalCode);
		query.setParameter("message", message);
		query.setParameter("responsePayload", responsePayload);
		query.setParameter("responseCode", responseCode);
		query.setParameter("changedBy", changedBy);
		query.setParameter("dateChanged", new Date());
		query.executeUpdate();
	}

	@Transactional
	public void updateRetryResponse(Integer id, String approvalStatus, String approvalCode, String message,
	                                String requestPayload, String responsePayload, Integer responseCode,
	                                Integer changedBy) {
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"update rhip_approval_request_log set approval_status = :approvalStatus, " +
						"approval_code = :approvalCode, message = :message, request_payload = :requestPayload, " +
						"response_payload = :responsePayload, response_code = :responseCode, changed_by = :changedBy, " +
						"date_changed = :dateChanged where rhip_approval_request_log_id = :id"
		);
		query.setParameter("id", id);
		query.setParameter("approvalStatus", approvalStatus);
		query.setParameter("approvalCode", approvalCode);
		query.setParameter("message", message);
		query.setParameter("requestPayload", requestPayload);
		query.setParameter("responsePayload", responsePayload);
		query.setParameter("responseCode", responseCode);
		query.setParameter("changedBy", changedBy);
		query.setParameter("dateChanged", new Date());
		query.executeUpdate();
	}

	@Transactional(readOnly = true)
	public RhipApprovalRequestLogEntry getById(Integer id) {
		if (id == null) {
			return null;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery("select * from rhip_approval_request_log where rhip_approval_request_log_id = :id");
		query.setParameter("id", id);
		Object row = query.uniqueResult();
		return row == null ? null : toEntry((Object[]) row);
	}

	@Transactional(readOnly = true)
	public List<RhipApprovalRequestLogEntry> getRecentByPatient(Integer patientId, int limit) {
		List<RhipApprovalRequestLogEntry> ret = new ArrayList<RhipApprovalRequestLogEntry>();
		if (patientId == null) {
			return ret;
		}
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select * from rhip_approval_request_log where patient_id = :patientId " +
						"order by date_created desc, rhip_approval_request_log_id desc"
		);
		query.setParameter("patientId", patientId);
		query.setMaxResults(limit <= 0 ? 20 : limit);
		for (Object row : query.list()) {
			ret.add(toEntry((Object[]) row));
		}
		return ret;
	}

	@Transactional(readOnly = true)
	public List<RhipApprovalRequestLogEntry> getRecent(int limit) {
		List<RhipApprovalRequestLogEntry> ret = new ArrayList<RhipApprovalRequestLogEntry>();
		Session session = dbSessionFactory.getHibernateSessionFactory().getCurrentSession();
		NativeQuery<?> query = session.createSQLQuery(
				"select * from rhip_approval_request_log order by date_created desc, rhip_approval_request_log_id desc"
		);
		query.setMaxResults(limit <= 0 ? 50 : limit);
		for (Object row : query.list()) {
			ret.add(toEntry((Object[]) row));
		}
		return ret;
	}

	private void bindEntry(NativeQuery<?> query, RhipApprovalRequestLogEntry entry) {
		query.setParameter("patientId", entry.getPatientId());
		query.setParameter("insurancePolicyId", entry.getInsurancePolicyId());
		query.setParameter("insuranceType", entry.getInsuranceType());
		query.setParameter("insuranceName", entry.getInsuranceName());
		query.setParameter("insuranceCardNo", entry.getInsuranceCardNo());
		query.setParameter("patientIdentifier", entry.getPatientIdentifier());
		query.setParameter("facilityFosaId", entry.getFacilityFosaId());
		query.setParameter("productCode", entry.getProductCode());
		query.setParameter("productName", entry.getProductName());
		query.setParameter("productType", entry.getProductType());
		query.setParameter("requestedQuantity", entry.getRequestedQuantity());
		query.setParameter("requestedUnitPrice", entry.getRequestedUnitPrice());
		query.setParameter("diagnosisIds", entry.getDiagnosisIds());
		query.setParameter("clinicalKnowledge", entry.getClinicalKnowledge());
		query.setParameter("practitionerLicenseNumber", entry.getPractitionerLicenseNumber());
		query.setParameter("approvalCode", entry.getApprovalCode());
		query.setParameter("approvalStatus", entry.getApprovalStatus());
		query.setParameter("message", entry.getMessage());
		query.setParameter("requestPayload", entry.getRequestPayload());
		query.setParameter("responsePayload", entry.getResponsePayload());
		query.setParameter("responseCode", entry.getResponseCode());
		query.setParameter("creator", entry.getCreator());
		query.setParameter("dateCreated", entry.getDateCreated());
		query.setParameter("changedBy", entry.getChangedBy());
		query.setParameter("dateChanged", entry.getDateChanged());
	}

	private RhipApprovalRequestLogEntry toEntry(Object[] row) {
		RhipApprovalRequestLogEntry entry = new RhipApprovalRequestLogEntry();
		entry.setId(intValue(row[0]));
		entry.setPatientId(intValue(row[1]));
		entry.setInsurancePolicyId(intValue(row[2]));
		entry.setInsuranceType(stringValue(row[3]));
		entry.setInsuranceName(stringValue(row[4]));
		entry.setInsuranceCardNo(stringValue(row[5]));
		entry.setPatientIdentifier(stringValue(row[6]));
		entry.setFacilityFosaId(stringValue(row[7]));
		entry.setProductCode(stringValue(row[8]));
		entry.setProductName(stringValue(row[9]));
		entry.setProductType(stringValue(row[10]));
		entry.setRequestedQuantity(decimalValue(row[11]));
		entry.setRequestedUnitPrice(decimalValue(row[12]));
		entry.setDiagnosisIds(stringValue(row[13]));
		entry.setClinicalKnowledge(stringValue(row[14]));
		entry.setPractitionerLicenseNumber(stringValue(row[15]));
		entry.setApprovalCode(stringValue(row[16]));
		entry.setApprovalStatus(stringValue(row[17]));
		entry.setMessage(stringValue(row[18]));
		entry.setRequestPayload(stringValue(row[19]));
		entry.setResponsePayload(stringValue(row[20]));
		entry.setResponseCode(intValue(row[21]));
		entry.setCreator(intValue(row[22]));
		entry.setDateCreated((Date) row[23]);
		entry.setChangedBy(intValue(row[24]));
		entry.setDateChanged((Date) row[25]);
		return entry;
	}

	private String stringValue(Object value) {
		return value == null ? null : value.toString();
	}

	private Integer intValue(Object value) {
		return value instanceof Number ? ((Number) value).intValue() : null;
	}

	private BigDecimal decimalValue(Object value) {
		if (value instanceof BigDecimal) {
			return (BigDecimal) value;
		}
		return value == null ? null : new BigDecimal(value.toString());
	}
}
