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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.integration.insurance.RhipApprovalRequiredProducts;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.mohbilling.utils.RhicCode;
import org.openmrs.module.rwandaemr.icd11.Icd11Service;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where clinicians choose procedures (clinical forms, lab and radiology orders): finds the ones the patient's insurer
 * must approve and requests that approval (RHIP Unified Insurance Integration Guide: MMI, section 7).
 * <p>
 * A procedure is found the way it is billed: from its concept to the billing item of that concept, then to the item's
 * RHIC/RLTC code, which needs approval when it is on MMI's approval-required list.
 */
public final class InsuranceApprovalCheck {

	private static final Log log = LogFactory.getLog(InsuranceApprovalCheck.class);

	private final Patient patient;

	private final InsurancePolicy policy;

	private final String insuranceType;

	private final String receptionNumber;

	private final String facilityFosaId;

	private Map<String, String> approvalRequiredProducts;

	private boolean approvalRequiredProductsLoaded;

	/**
	 * The insurance type whose approval flow is supported so far
	 */
	static final String SUPPORTED_INSURANCE_TYPE = "mmi";

	private InsuranceApprovalCheck(Patient patient, InsurancePolicy policy, String insuranceType, String receptionNumber,
	                               String facilityFosaId) {
		this.patient = patient;
		this.policy = policy;
		this.insuranceType = insuranceType;
		this.receptionNumber = receptionNumber;
		this.facilityFosaId = facilityFosaId;
	}

	/**
	 * @return the check for a patient whose active insurance is one whose procedures need approval
	 * (mohbilling.approvalRequired.insurances), or null when the patient has none. Only MMI's approval flow is
	 * supported so far, so other insurances are not checked yet.
	 */
	public static InsuranceApprovalCheck forPatient(Patient patient) {
		if (patient == null || patient.getPatientId() == null) {
			return null;
		}
		RhipApprovalRequiredProducts products = Context.getRegisteredComponent("rhipApprovalRequiredProducts",
				RhipApprovalRequiredProducts.class);
		InsuranceIntegrationConfig config = Context.getRegisteredComponents(InsuranceIntegrationConfig.class).get(0);
		Date startOfToday = startOfDay(new Date());
		List<InsurancePolicy> policies = Context.getService(BillingService.class).getAllInsurancePoliciesByPatient(patient);
		for (InsurancePolicy policy : policies == null ? Collections.<InsurancePolicy>emptyList() : policies) {
			if (policy == null || policy.isRetired() || policy.getInsurance() == null
					|| (policy.getExpirationDate() != null && policy.getExpirationDate().before(startOfToday))) {
				continue;
			}
			String insuranceType = products == null ? null : products.getInsuranceTypeIfChecked(policy.getInsurance());
			if (!SUPPORTED_INSURANCE_TYPE.equals(insuranceType)) {
				continue;
			}
			MmiPatientReceptionLogRepository receptions = Context.getRegisteredComponents(MmiPatientReceptionLogRepository.class).get(0);
			String receptionNumber = StringUtils.trimToNull(
					receptions.getLatestSuccessfulReceptionNumberForInsuranceCard(policy.getInsuranceCardNo()));
			return new InsuranceApprovalCheck(patient, policy, insuranceType, receptionNumber, config.getFacilityFosaId());
		}
		return null;
	}

	/**
	 * @return the insurance's name, for messages such as "These procedures need MMI's approval"
	 */
	public String getInsuranceName() {
		return policy.getInsurance().getName();
	}

	public String getInsuranceType() {
		return insuranceType;
	}

	/**
	 * @return true when the patient has an MMI reception, without which approval cannot be requested
	 */
	public boolean canRequest() {
		return receptionNumber != null;
	}

	public String getReceptionNumber() {
		return receptionNumber;
	}

	/**
	 * @return MMI's approval-required procedures (by code, with their names), or null when unknown
	 */
	public Map<String, String> getApprovalRequiredProducts() {
		// Asked once per check: a form has many fields to check
		if (!approvalRequiredProductsLoaded) {
			approvalRequiredProductsLoaded = true;
			if (StringUtils.isNotBlank(facilityFosaId)) {
				RhipApprovalRequiredProducts products = Context.getRegisteredComponent("rhipApprovalRequiredProducts",
						RhipApprovalRequiredProducts.class);
				approvalRequiredProducts = products == null ? null : products.getProducts(insuranceType, facilityFosaId);
			}
		}
		return approvalRequiredProducts;
	}

	/**
	 * @return the procedure billed for this concept when MMI must approve it, else null
	 */
	public Procedure procedureFor(Concept concept) {
		Map<String, String> required = getApprovalRequiredProducts();
		return required == null ? null : Procedure.of(concept, required);
	}

	/**
	 * @return every concept whose billing item MMI must approve, by concept id, e.g. to warn as soon as one is chosen
	 */
	public Map<Integer, Procedure> getApprovalRequiredConcepts() {
		Map<Integer, Procedure> ret = new LinkedHashMap<Integer, Procedure>();
		Map<String, String> required = getApprovalRequiredProducts();
		if (required == null || required.isEmpty()) {
			return ret;
		}
		for (Map.Entry<Integer, List<BillingItem>> entry : billingItemsByConcept().entrySet()) {
			Concept concept = new Concept(entry.getKey());
			Procedure procedure = Procedure.of(concept, required);
			if (procedure != null) {
				ret.put(entry.getKey(), procedure);
			}
		}
		return ret;
	}

	/**
	 * Requests MMI's approval for procedures, in one request for the patient's reception
	 *
	 * @param procedures the procedures with their quantity
	 * @param note an optional note for MMI's approver
	 * @param encounter the encounter they were chosen in, whose clinician and ICD-11 diagnoses go with the request;
	 * may be null
	 * @param orderer the clinician who chose them, used when the encounter has none with a licence; may be null
	 */
	public RhipApprovalRequestService.Result request(Map<Procedure, BigDecimal> procedures, String note,
	                                                 Encounter encounter, Provider orderer) {
		if (!canRequest()) {
			return null;
		}
		String license = practitionerLicense(encounter, orderer);
		String diagnosisIds = diagnosisIds(encounter);
		User user = Context.getAuthenticatedUser();
		List<RhipApprovalRequestLogEntry> lines = new ArrayList<RhipApprovalRequestLogEntry>();
		for (Map.Entry<Procedure, BigDecimal> entry : procedures.entrySet()) {
			Procedure procedure = entry.getKey();
			RhipApprovalRequestLogEntry line = new RhipApprovalRequestLogEntry();
			line.setPatientId(patient.getPatientId());
			line.setInsurancePolicyId(policy.getInsurancePolicyId());
			line.setInsuranceType(insuranceType);
			line.setInsuranceName(policy.getInsurance().getName());
			line.setInsuranceCardNo(policy.getInsuranceCardNo());
			line.setPatientIdentifier(StringUtils.defaultIfBlank(policy.getRhipPatientId(), policy.getInsuranceCardNo()));
			line.setFacilityFosaId(facilityFosaId);
			line.setVisitReferenceNumber(receptionNumber);
			line.setProductCode(procedure.getCode());
			line.setProductName(procedure.getName());
			line.setProductType(procedure.getCategory());
			line.setRequestedQuantity(entry.getValue() == null ? BigDecimal.ONE : entry.getValue());
			line.setDiagnosisIds(diagnosisIds);
			line.setClinicalKnowledge(StringUtils.trimToNull(note));
			line.setPractitionerLicenseNumber(license);
			line.setCreator(user == null ? null : user.getUserId());
			line.setDateCreated(new Date());
			lines.add(line);
		}
		if (lines.isEmpty()) {
			return null;
		}
		RhipApprovalRequestService service = Context.getRegisteredComponents(RhipApprovalRequestService.class).get(0);
		return service.submitAll(lines);
	}

	/**
	 * Adds up quantities by procedure, keeping only the procedures whose code was asked for
	 */
	public static void add(Map<Procedure, BigDecimal> procedures, Procedure procedure, BigDecimal quantity,
	                       Set<String> requestedCodes) {
		if (procedure == null || (requestedCodes != null && !requestedCodes.contains(procedure.getCode()))) {
			return;
		}
		for (Map.Entry<Procedure, BigDecimal> entry : procedures.entrySet()) {
			if (entry.getKey().getCode().equals(procedure.getCode())) {
				entry.setValue(entry.getValue().add(quantity));
				return;
			}
		}
		procedures.put(procedure, quantity);
	}

	public static Map<Procedure, BigDecimal> newProcedureMap() {
		return new LinkedHashMap<Procedure, BigDecimal>();
	}

	private String practitionerLicense(Encounter encounter, Provider orderer) {
		if (encounter != null && encounter.getActiveEncounterProviders() != null) {
			for (EncounterProvider encounterProvider : encounter.getActiveEncounterProviders()) {
				String license = ProviderLicense.forProvider(encounterProvider.getProvider());
				if (license != null) {
					return license;
				}
			}
		}
		String license = orderer == null ? null : ProviderLicense.forProvider(orderer);
		return license != null ? license : ProviderLicense.forUser(Context.getAuthenticatedUser());
	}

	private String diagnosisIds(Encounter encounter) {
		if (encounter == null) {
			return null;
		}
		try {
			List<String> codes = new ArrayList<String>();
			List<Icd11PatientDiagnosis> diagnoses = Context.getService(Icd11Service.class).getDiagnoses(patient, encounter);
			for (Icd11PatientDiagnosis diagnosis : diagnoses == null ? Collections.<Icd11PatientDiagnosis>emptyList() : diagnoses) {
				String code = diagnosis == null ? null : StringUtils.trimToNull(diagnosis.getIcd11Code());
				if (code != null && !codes.contains(code)) {
					codes.add(code);
				}
			}
			return codes.isEmpty() ? null : StringUtils.join(codes, ",");
		}
		catch (Exception e) {
			return null;
		}
	}

	private static final long INDEX_MILLIS = 10L * 60 * 1000;

	private static volatile Map<Integer, List<BillingItem>> billingItemsByConcept;

	private static volatile long billingItemsLoaded;

	/**
	 * @return the active billing items by concept, read in one query and kept for {@link #INDEX_MILLIS}, so a form or
	 * an order page with many procedures is checked quickly
	 */
	static Map<Integer, List<BillingItem>> billingItemsByConcept() {
		long now = System.currentTimeMillis();
		Map<Integer, List<BillingItem>> index = billingItemsByConcept;
		if (index != null && now - billingItemsLoaded < INDEX_MILLIS) {
			return index;
		}
		List<List<Object>> rows = query("select concept_id, rhic_code, name, category, hide_item "
				+ "from moh_bill_facility_service_price where retired = 0 and concept_id is not null");
		if (rows == null) {
			// Before the rhic_code column exists: the code is only in the name
			rows = query("select concept_id, null, name, category, hide_item "
					+ "from moh_bill_facility_service_price where retired = 0 and concept_id is not null");
		}
		Map<Integer, List<BillingItem>> ret = new java.util.HashMap<Integer, List<BillingItem>>();
		for (List<Object> row : rows == null ? Collections.<List<Object>>emptyList() : rows) {
			if (row == null || row.size() < 5 || !(row.get(0) instanceof Number)) {
				continue;
			}
			String name = row.get(2) == null ? null : row.get(2).toString();
			String code = row.get(1) == null ? null : RhicCode.normalize(row.get(1).toString());
			if (code == null) {
				code = RhicCode.fromName(name);
			}
			BillingItem item = new BillingItem(code, StringUtils.substringBefore(StringUtils.defaultString(name), "|").trim(),
					row.get(3) == null ? null : row.get(3).toString(), isTrue(row.get(4)));
			Integer conceptId = ((Number) row.get(0)).intValue();
			List<BillingItem> items = ret.get(conceptId);
			if (items == null) {
				items = new ArrayList<BillingItem>();
				ret.put(conceptId, items);
			}
			items.add(item);
		}
		billingItemsByConcept = ret;
		billingItemsLoaded = now;
		return ret;
	}

	private static final String SQL_PRIVILEGE = "SQL Level Access";

	/**
	 * Runs a read-only query for any user: clinicians do not have the SQL privilege
	 */
	private static List<List<Object>> query(String sql) {
		boolean privileged = false;
		try {
			Context.addProxyPrivilege(SQL_PRIVILEGE);
			privileged = true;
			return Context.getAdministrationService().executeSQL(sql, true);
		}
		catch (Exception e) {
			log.debug("Unable to read the billing items: " + sql, e);
			return null;
		}
		finally {
			if (privileged) {
				Context.removeProxyPrivilege(SQL_PRIVILEGE);
			}
		}
	}

	private static boolean isTrue(Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue() != 0;
		}
		return value != null && ("true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString()));
	}

	private static class BillingItem {

		private final String code;

		private final String name;

		private final String category;

		private final boolean hidden;

		private BillingItem(String code, String name, String category, boolean hidden) {
			this.code = code;
			this.name = name;
			this.category = category;
			this.hidden = hidden;
		}
	}

	private static Date startOfDay(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTime();
	}

	/**
	 * A billing item that needs MMI approval
	 */
	public static class Procedure {

		private final String code;

		private final String name;

		private final String category;

		private Procedure(String code, String name, String category) {
			this.code = code;
			this.name = name;
			this.category = category;
		}

		static Procedure of(Concept concept, Map<String, String> required) {
			if (concept == null || concept.getConceptId() == null || required == null) {
				return null;
			}
			List<BillingItem> items = billingItemsByConcept().get(concept.getConceptId());
			if (items == null || items.isEmpty()) {
				return null;
			}
			if (items.size() > 1) {
				// Several active billing items share this concept, so which one is billed is not known: skip only it
				log.debug("Concept " + concept.getConceptId() + " is linked to " + items.size()
						+ " active billing items; it is not checked for MMI approval");
				return null;
			}
			BillingItem item = items.get(0);
			if (item.hidden || item.code == null || !required.containsKey(item.code)) {
				return null;
			}
			return new Procedure(item.code, StringUtils.defaultIfBlank(required.get(item.code), item.name), item.category);
		}

		public String getCode() {
			return code;
		}

		public String getName() {
			return name;
		}

		public String getCategory() {
			return category;
		}
	}
}
