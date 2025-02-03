package org.openmrs.module.rwandaemr.event;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.mohbilling.businesslogic.AdmissionUtil;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.model.Admission;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.Beneficiary;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.MapMessage;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Listener that can be registered with Patient creation (or update) events and which will create a
 * default private insurance for that patient if they do not already have one
 */
@Component
public class CreateInsurancePatientListener extends PatientEventListener {

	private final PatientService patientService;
	private final RwandaEmrConfig rwandaEmrConfig;

	public CreateInsurancePatientListener(@Autowired PatientService patientService,
										  @Autowired RwandaEmrConfig rwandaEmrConfig) {
		this.patientService = patientService;
		this.rwandaEmrConfig = rwandaEmrConfig;
	}

	@Override
	public void handlePatient(String patientUuid, MapMessage mapMessage) {
		// This functionality is enabled or disabled by setting or unsetting this global property value
		String insuranceName = ConfigUtil.getProperty("rwandaemr.autoCreateInsuranceType");
		if (StringUtils.isNotBlank(insuranceName)) {

			Patient patient = patientService.getPatientByUuid(patientUuid);
			if (patient == null) {
				throw new IllegalArgumentException("unable to retrieve patient with uuid: " + patientUuid);
			}

			Insurance insurance = null;
			for (Insurance i : getBillingService().getAllInsurances()) {
				if (i.getName().equalsIgnoreCase(insuranceName)) {
					insurance = i;
				}
			}
			if (insurance == null) {
				throw new IllegalStateException("Could not find insurance with name " + insuranceName);
			}

			PatientIdentifierType identifierType = rwandaEmrConfig.getPrimaryCareIdentifierType();
			if (identifierType == null) {
				throw new IllegalStateException("Could not find primary care identifier type");
			}
			List<String> primaryCareIdentifiers = new ArrayList<>();
			for (PatientIdentifier pi : patient.getPatientIdentifiers(identifierType)) {
				primaryCareIdentifiers.add(pi.getIdentifier());
			}

			if (primaryCareIdentifiers.isEmpty()) {
				throw new IllegalStateException("Could not find a primary care identifier for patient " + patient.getUuid());
			}

			List<InsurancePolicy> policies = getBillingService().getAllInsurancePoliciesByPatient(patient);
			boolean foundExistingPolicy = false;
			for (InsurancePolicy policy : policies) {
				if (insurance.equals(policy.getInsurance()) && primaryCareIdentifiers.contains(policy.getInsuranceCardNo())) {
					foundExistingPolicy = true;
				}
			}
			if (!foundExistingPolicy) {
				InsurancePolicy policy = new InsurancePolicy();
				policy.setOwner(patient);
				policy.setInsurance(insurance);
				policy.setInsuranceCardNo(primaryCareIdentifiers.get(0));
				policy.setCoverageStartDate(patient.getDateCreated());
				policy.setExpirationDate(DateUtils.addYears(policy.getCoverageStartDate(), 20));
				policy.setCreatedDate(patient.getDateCreated());
				policy.setCreator(patient.getCreator());
				policy.setRetired(false);

				Beneficiary beneficiary = new Beneficiary();
				beneficiary.setPatient(policy.getOwner());
				beneficiary.setInsurancePolicy(policy);
				beneficiary.setCreatedDate(policy.getCreatedDate());
				beneficiary.setCreator(policy.getCreator());
				beneficiary.setRetired(policy.isRetired());
				beneficiary.setPolicyIdNumber(policy.getInsuranceCardNo());
				policy.addBeneficiary(beneficiary);

				getBillingService().saveInsurancePolicy(policy);
				log.debug("Created new insurance policy for patient " + patient.getUuid());

				// Create Admission and Global Bill
				Admission admission = new Admission();
				admission.setAdmissionDate(new Date());
				admission.setInsurancePolicy(policy);
				admission.setIsAdmitted(true);
				admission.setCreator(Context.getAuthenticatedUser());
				admission.setCreatedDate(new Date());
				admission.setDiseaseType("Default Disease Type");
				admission.setAdmissionType(1); // Example type for simplicity
				Admission savedAdmission = AdmissionUtil.savePatientAdmission(admission);

				GlobalBill globalBill = new GlobalBill();
				globalBill.setAdmission(savedAdmission);
				globalBill.setBillIdentifier(policy.getInsuranceCardNo() + savedAdmission.getAdmissionId());
				globalBill.setCreatedDate(new Date());
				globalBill.setCreator(Context.getAuthenticatedUser());
				globalBill.setInsurance(policy.getInsurance());

				GlobalBillUtil.saveGlobalBill(globalBill);
				log.debug("Created new admission and global bill for patient " + patient.getUuid());
			}
		} else {
			if (log.isTraceEnabled()) {
				log.trace("CreateInsurancePatientListener is not enabled, as GP rwandaemr.autoCreateInsuranceType is not set");
			}
		}
	}


	@Override
	public void handleException(Exception e) {
		log.warn("Unable to create insurance for patient: " + e.getMessage());
		log.debug(e);
	}

	BillingService getBillingService() {
		return Context.getService(BillingService.class);
	}
}
