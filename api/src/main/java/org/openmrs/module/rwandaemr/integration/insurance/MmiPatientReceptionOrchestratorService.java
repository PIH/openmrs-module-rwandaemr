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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.mohbilling.model.Beneficiary;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MmiPatientReceptionOrchestratorService {

	protected Log log = LogFactory.getLog(getClass());

	private static final String GP_INSURANCE_TYPE_CONCEPT = "registration.insuranceTypeConcept";
	private static final String GP_INSURANCE_NUMBER_CONCEPT = "registration.insuranceNumberConcept";
	private static final String GP_MMI_PATIENT_TYPE_DEFAULT = "rwandaemr.insuranceReception.mmiPatientTypeDefault";
	private static final String GP_MMI_PRESCRIPTION_REQUIRED = "rwandaemr.insuranceReception.mmiPrescriptionRequired";
	private static final String GP_MMI_OTP_CODE = "rwandaemr.insuranceReception.mmiOtpCode";

	private final InsurancePatientReceptionProvider patientReceptionProvider;
	private final MmiPatientReceptionLogRepository logRepository;
	private final IntegrationConfig integrationConfig;
	private final InsuranceIntegrationConfig insuranceIntegrationConfig;

	public MmiPatientReceptionOrchestratorService(
			@Autowired InsurancePatientReceptionProvider patientReceptionProvider,
			@Autowired MmiPatientReceptionLogRepository logRepository,
			@Autowired IntegrationConfig integrationConfig,
			@Autowired InsuranceIntegrationConfig insuranceIntegrationConfig) {
		this.patientReceptionProvider = patientReceptionProvider;
		this.logRepository = logRepository;
		this.integrationConfig = integrationConfig;
		this.insuranceIntegrationConfig = insuranceIntegrationConfig;
	}

	@Transactional
	public void createMmiReceptionForRegistration(FormEntrySession session) {
		try {
			if (session == null || session.getContext() == null) {
				return;
			}
			FormEntryContext.Mode mode = session.getContext().getMode();
			if (!(mode == FormEntryContext.Mode.ENTER || mode == FormEntryContext.Mode.EDIT)) {
				return;
			}
			Encounter encounter = session.getEncounter();
			if (encounter == null) {
				log.warn("Skipping MMI patient reception because encounter is null");
				return;
			}
			Visit visit = encounter.getVisit();
			if (visit == null) {
				log.warn("Skipping MMI patient reception because visit is null for encounter " + encounter.getUuid());
				return;
			}
			if (logRepository.hasSuccessfulReceptionForVisit(visit.getVisitId())) {
				log.debug("MMI patient reception already exists for visit " + visit.getVisitId());
				return;
			}

			Concept insuranceTypeConcept = getConceptFromGp(GP_INSURANCE_TYPE_CONCEPT);
			Concept insuranceNumberConcept = getConceptFromGp(GP_INSURANCE_NUMBER_CONCEPT);
			if (insuranceTypeConcept == null || insuranceNumberConcept == null) {
				log.warn("Skipping MMI patient reception: registration insurance concepts are not configured");
				return;
			}

			Obs insuranceTypeObs = findTopLevelObs(encounter, insuranceTypeConcept);
			Obs insuranceNumberObs = findTopLevelObs(encounter, insuranceNumberConcept);
			if (insuranceTypeObs == null || insuranceTypeObs.getValueCoded() == null || insuranceNumberObs == null ||
					StringUtils.isBlank(insuranceNumberObs.getValueText())) {
				return;
			}

			String insuranceCardNo = insuranceNumberObs.getValueText().trim();
			InsurancePolicy policy = findInsurancePolicy(encounter.getPatient(), insuranceTypeObs.getValueCoded(), insuranceCardNo);
			String insuranceName = policy != null && policy.getInsurance() != null ? policy.getInsurance().getName() :
					insuranceTypeObs.getValueCoded().getDisplayString();

			if (!isMmiInsurance(insuranceName)) {
				return;
			}

			String patientIdentifier = insuranceCardNo;
			String facilityFosaId = insuranceIntegrationConfig.getPatientReceptionFacilityFosaIdOverride();
			if (StringUtils.isBlank(facilityFosaId)) {
				facilityFosaId = integrationConfig.getFosaId(encounter.getLocation());
			}
			String patientType = resolvePatientType(insuranceCardNo);
			Boolean prescriptionRequired = resolvePrescriptionRequired();
			String otpCode = StringUtils.trimToNull(MmiPatientReceptionContextHolder.getSubmittedOtpCode());
			if (otpCode == null) {
				otpCode = StringUtils.trimToNull(Context.getAdministrationService().getGlobalProperty(GP_MMI_OTP_CODE));
			}

			Map<String, Object> payload = new HashMap<>();
			payload.put("insuranceType", "MMI");
			payload.put("patientIdentifier", patientIdentifier);
			payload.put("facilityFosaId", facilityFosaId);
			payload.put("patientType", patientType);
			payload.put("prescriptionRequired", prescriptionRequired);
			if (StringUtils.isNotBlank(otpCode)) {
				payload.put("otpCode", otpCode);
			}
			ObjectMapper mapper = new ObjectMapper();
			String requestPayload = mapper.writeValueAsString(payload);

			IntegrationResponse response = patientReceptionProvider.createPatientReception(
					"MMI", patientIdentifier, facilityFosaId, patientType, otpCode, prescriptionRequired
			);
			InsurancePatientReceptionResponse responseEntity = response != null &&
					response.getResponseEntity() instanceof InsurancePatientReceptionResponse ?
					(InsurancePatientReceptionResponse) response.getResponseEntity() : null;

			boolean success = response != null && response.isEndpointAccessible() && response.getResponseCode() != null &&
					response.getResponseCode() >= 200 && response.getResponseCode() < 300 &&
					responseEntity != null && responseEntity.isSuccess() &&
					responseEntity.getData() != null && StringUtils.isNotBlank(responseEntity.getData().getReceptionNumber());

			String responsePayload = responseEntity != null ? mapper.writeValueAsString(responseEntity) : null;
			String errorMessage = resolveErrorMessage(response, responseEntity);

			MmiPatientReceptionLogEntry entry = new MmiPatientReceptionLogEntry();
			entry.setPatientId(encounter.getPatient() != null ? encounter.getPatient().getPatientId() : null);
			entry.setVisitId(visit.getVisitId());
			entry.setEncounterId(encounter.getEncounterId());
			entry.setInsurancePolicyId(policy != null ? policy.getInsurancePolicyId() : null);
			entry.setInsuranceName(insuranceName);
			entry.setInsuranceCardNo(insuranceCardNo);
			entry.setPatientIdentifier(patientIdentifier);
			entry.setFacilityFosaId(facilityFosaId);
			entry.setPatientType(patientType);
			entry.setPrescriptionRequired(prescriptionRequired);
			entry.setRequestPayload(requestPayload);
			entry.setResponsePayload(responsePayload);
			entry.setResponseCode(response != null ? response.getResponseCode() : null);
			entry.setStatus(success ? "SUCCESS" : "FAILED");
			entry.setReceptionNumber(responseEntity != null && responseEntity.getData() != null ? responseEntity.getData().getReceptionNumber() : null);
			entry.setBpCode(responseEntity != null && responseEntity.getData() != null ? responseEntity.getData().getBpCode() : null);
			entry.setReceptionStatus(responseEntity != null && responseEntity.getData() != null ? responseEntity.getData().getStatus() : null);
			entry.setErrorMessage(errorMessage);
			entry.setCreator(Context.getAuthenticatedUser() != null ? Context.getAuthenticatedUser().getUserId() : null);
			entry.setDateCreated(new Date());
			logRepository.saveLogEntry(entry);

			if (!success) {
				log.warn("MMI patient reception failed for visit " + visit.getVisitId() + ": " + errorMessage);
			}
		}
		catch (Exception e) {
			log.error("Unexpected error while creating MMI patient reception", e);
		}
	}

	private Concept getConceptFromGp(String gpName) {
		String gpVal = Context.getAdministrationService().getGlobalProperty(gpName);
		if (StringUtils.isBlank(gpVal)) {
			return null;
		}
		return Context.getConceptService().getConceptByUuid(gpVal) != null ?
				Context.getConceptService().getConceptByUuid(gpVal) : Context.getConceptService().getConcept(gpVal);
	}

	private Obs findTopLevelObs(Encounter encounter, Concept concept) {
		if (encounter == null || concept == null) {
			return null;
		}
		for (Obs obs : encounter.getObsAtTopLevel(false)) {
			if (concept.equals(obs.getConcept())) {
				return obs;
			}
		}
		return null;
	}

	private InsurancePolicy findInsurancePolicy(Patient patient, Concept insuranceConcept, String insuranceCardNo) {
		if (patient == null || insuranceConcept == null || StringUtils.isBlank(insuranceCardNo)) {
			return null;
		}
		List<InsurancePolicy> policies = Context.getService(BillingService.class).getAllInsurancePoliciesByPatient(patient);
		for (InsurancePolicy policy : policies) {
			if (policy.getInsurance() != null && policy.getInsurance().getConcept() != null &&
					policy.getInsurance().getConcept().equals(insuranceConcept) &&
					insuranceCardNo.equals(policy.getInsuranceCardNo())) {
				return policy;
			}
		}
		return null;
	}

	private boolean isMmiInsurance(String insuranceName) {
		return StringUtils.isNotBlank(insuranceName) && insuranceName.toUpperCase().contains("MMI");
	}

	private String resolvePatientType(String insuranceCardNo) {
		Beneficiary beneficiary = Context.getService(BillingService.class).getBeneficiaryByPolicyNumber(insuranceCardNo);
		if (beneficiary != null && beneficiary.getLevel() > 0) {
			return String.valueOf(beneficiary.getLevel());
		}
		String configured = StringUtils.trimToNull(Context.getAdministrationService().getGlobalProperty(GP_MMI_PATIENT_TYPE_DEFAULT));
		return configured == null ? "1" : configured;
	}

	private Boolean resolvePrescriptionRequired() {
		String configured = StringUtils.trimToNull(Context.getAdministrationService().getGlobalProperty(GP_MMI_PRESCRIPTION_REQUIRED));
		return configured == null || Boolean.parseBoolean(configured);
	}

	private String resolveErrorMessage(IntegrationResponse response, InsurancePatientReceptionResponse responseEntity) {
		if (response == null) {
			return "No response returned from patient reception integration";
		}
		if (!response.isEnabled()) {
			return "MMI patient reception integration is not enabled. Configure " +
					InsuranceIntegrationConfig.PATIENT_RECEPTION_URL + " global property.";
		}
		if (StringUtils.isNotBlank(response.getErrorMessage())) {
			return response.getErrorMessage();
		}
		if (responseEntity != null) {
			if (StringUtils.isNotBlank(responseEntity.getFirstErrorMessage())) {
				return responseEntity.getFirstErrorMessage();
			}
			if (StringUtils.isNotBlank(responseEntity.getError())) {
				return responseEntity.getError();
			}
			if (!responseEntity.isSuccess() && StringUtils.isNotBlank(responseEntity.getMessage())) {
				return responseEntity.getMessage();
			}
		}
		if (response.getResponseCode() != null && (response.getResponseCode() < 200 || response.getResponseCode() > 299)) {
			return "HTTP " + response.getResponseCode() + " returned from patient reception endpoint";
		}
		return null;
	}
}
