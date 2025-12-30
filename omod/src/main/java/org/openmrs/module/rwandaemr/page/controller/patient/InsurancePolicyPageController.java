package org.openmrs.module.rwandaemr.page.controller.patient;

import lombok.Data;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.mohbilling.businesslogic.InsurancePolicyUtil;
import org.openmrs.module.mohbilling.businesslogic.InsuranceUtil;
import org.openmrs.module.mohbilling.model.Beneficiary;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceIntegrationConfig;
import org.openmrs.ui.framework.annotation.BindParams;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.MethodParam;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class InsurancePolicyPageController {

    public InsurancePolicy getPolicy(@RequestParam(value = "patientId") Patient patient,
                                     @RequestParam(value = "policyId", required = false) Integer policyId) {
        BillingService billingService = Context.getService(BillingService.class);
        InsurancePolicy policy;
        if (policyId != null) {
            policy = billingService.getInsurancePolicy(policyId);
        }
        else {
            policy = new InsurancePolicy();
            policy.setOwner(patient);
        }
        return policy;
    }

    public String get(PageModel model,
                      @MethodParam("getPolicy") @BindParams InsurancePolicy policy,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @SpringBean("insuranceIntegrationConfig") InsuranceIntegrationConfig insuranceIntegrationConfig,
                      @RequestParam(value = "patientId") Patient patient,
                      @RequestParam(value = "edit", defaultValue = "false", required = false) Boolean edit,
                      @RequestParam(value = "returnUrl", required = false) String returnUrl) throws IOException {

        if (!Context.hasPrivilege("Create Insurance Policy") && policy.getInsurancePolicyId() == null) {
            return "redirect:/index.htm";
        }
        if (!Context.hasPrivilege("Edit Insurance Policy") && BooleanUtils.isTrue(edit)) {
            return "redirect:/index.htm";
        }

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("editMode", BooleanUtils.isTrue(edit));
        model.addAttribute("patient", patientDomainWrapper);
        model.addAttribute("policy", policy);
        model.addAttribute("policyModel", new InsurancePolicyModel(policy));
        model.addAttribute("insurances", InsuranceUtil.getInsurances(true));
        model.addAttribute("insurancesToVerify", insuranceIntegrationConfig.getInsurancesToVerify());
        model.addAttribute("thirdParties", InsurancePolicyUtil.getAllThirdParties());
        model.addAttribute("owners", getEligiblePolicyOwnersForPatient(patient));
        model.addAttribute("returnUrl", getReturnUrl(returnUrl, patient, policy));

        return "patient/insurancePolicy";
    }

    public String post(@InjectBeans PatientDomainWrapper patientDomainWrapper,
                       @MethodParam("getPolicy") InsurancePolicy policy,
                       @BindParams InsurancePolicyModel policyModel,
                       BindingResult errors,
                       @RequestParam(value = "patientId") Patient patient,
                       @RequestParam(value = "returnUrl", required = false) String returnUrl,
                       @SpringBean("messageSourceService") MessageSourceService mss,
                       PageModel model,
                       HttpServletRequest request) {

        patientDomainWrapper.setPatient(patient);

        Date now = new Date();
        User currentUser = Context.getAuthenticatedUser();

        try {
            if (!Context.hasPrivilege("Create Insurance Policy") && policy.getInsurancePolicyId() == null) {
                throw new APIException(mss.getMessage("require.unauthorized"));
            }
            if (!Context.hasPrivilege("Edit Insurance Policy") && policy.getInsurancePolicyId() != null) {
                throw new APIException(mss.getMessage("require.unauthorized"));
            }

            String insurancePrefix = "rwandaemr.insurance.";
            String beneficiaryPrefix = insurancePrefix + "beneficiary.";
            rejectIfEmpty(errors, policyModel.getInsuranceId(), "insuranceId", insurancePrefix + "name");
            rejectIfEmpty(errors, policyModel.getOwner(), "owner", insurancePrefix + "owner");
            rejectIfEmpty(errors, policyModel.getInsuranceCardNo(), "insuranceCardNo", insurancePrefix + "insuranceCardNo");
            rejectIfEmpty(errors, policyModel.getCoverageStartDate(), "coverageStartDate", insurancePrefix + "coverageStartDate");
            rejectIfEmpty(errors, policyModel.getExpirationDate(), "expirationDate", insurancePrefix + "expirationDate");

            // If insurance category is not PRIVATE or NONE, then ownerCode is required.  See RWA-979
            Insurance insurance = policyModel.getInsuranceId() == null ? null : InsuranceUtil.getInsurance(policyModel.getInsuranceId());
            if (insurance != null && insurance.getCategory() != null) {
                if (!insurance.getCategory().equals("NONE") && !insurance.getCategory().equals("PRIVATE")) {
                    rejectIfEmpty(errors, policyModel.getOwnerCode(), "ownerCode", beneficiaryPrefix + "ownerCode");
                }
            }

            // TODO: Review this, but 1.x billing module code does not allow any duplicate card numbers, even across insurance types
            if (StringUtils.isNotBlank(policyModel.getInsuranceCardNo())) {
                InsurancePolicy existingPolicy = Context.getService(BillingService.class).getInsurancePolicyByCardNo(policyModel.getInsuranceCardNo());
                if (existingPolicy != null && !existingPolicy.getInsurancePolicyId().equals(policy.getInsurancePolicyId())) {
                    errors.rejectValue("insuranceCardNo", "rwandaemr.insurance.error.duplicateCardNumber");
                }
            }

            if (errors.hasErrors()) {
                String message = "";
                for (ObjectError error : errors.getAllErrors()) {
                    Object[] arguments = error.getArguments();
                    String errorMessage = mss.getMessage(Objects.requireNonNull(error.getCode()));
                    if (arguments != null) {
                        for (int i = 0; i < arguments.length; i++) {
                            String argument = (String) arguments[i];
                            errorMessage = errorMessage.replaceAll("\\{" + i + "}", mss.getMessage(argument));
                        }
                    }
                    message = message.concat(errorMessage).concat("<br>");
                }
                throw new APIException(message);
            }

            policy.setInsurance(insurance);
            policy.setOwner(policyModel.getOwner());
            policy.setInsuranceCardNo(policyModel.getInsuranceCardNo());
            policy.setCoverageStartDate(policyModel.getCoverageStartDate());
            policy.setExpirationDate(policyModel.getExpirationDate());
            policy.setThirdParty(policyModel.getThirdPartyId() == null ? null : InsurancePolicyUtil.getThirdParty(policyModel.getThirdPartyId()));
            policy.setCreatedDate(now);
            policy.setCreator(currentUser);
            policy.setRetired(false); // TODO: Support retiring and un-retiring?

            Beneficiary beneficiary = null;
            if (policy.getBeneficiaries() != null) {
                for (Beneficiary b : policy.getBeneficiaries()) {
                    beneficiary = b;
                }
            }
            if (beneficiary == null) {
                beneficiary = new Beneficiary();
                beneficiary.setPatient(policy.getOwner());
                beneficiary.setInsurancePolicy(policy);
                beneficiary.setCreatedDate(now);
                beneficiary.setCreator(currentUser);
                beneficiary.setRetired(false);  // TODO: Support retiring and un-retiring?
            }

            // TODO: There is some odd logic about how these are set in 1.x.  Review that or this for correctness.
            beneficiary.setPolicyIdNumber(policy.getInsuranceCardNo());
            beneficiary.setOwnerName(policyModel.getOwnerName());
            beneficiary.setOwnerCode(policyModel.getOwnerCode());
            beneficiary.setLevel(policyModel.getLevel() == null ? 0 : policyModel.getLevel());
            beneficiary.setCompany(policyModel.getCompany());
            policy.addBeneficiary(beneficiary);

            Context.getService(BillingService.class).saveInsurancePolicy(policy);
        }
        catch (Exception e) {
            request.getSession().setAttribute("emr.errorMessage", e.getMessage());
            model.addAttribute("patient", patientDomainWrapper);
            model.addAttribute("policy", policy);
            model.addAttribute("policyModel", policyModel);
            model.addAttribute("insurances", InsuranceUtil.getInsurances(true));
            model.addAttribute("thirdParties", InsurancePolicyUtil.getAllThirdParties());
            model.addAttribute("owners", getEligiblePolicyOwnersForPatient(patient));
            model.addAttribute("returnUrl", getReturnUrl(returnUrl, patient, policy));
            model.addAttribute("editMode", true);
            return "patient/insurancePolicy";
        }

        request.getSession().setAttribute("emr.infoMessage", mss.getMessage("rwandaemr.insurancePolicy.saved"));
        request.getSession().setAttribute("emr.toastMessage", "true");
        return "redirect:" + getReturnUrl(returnUrl, patient, policy);
    }


    private void rejectIfEmpty(Errors errors, Object value, String field, String fieldName) {
        if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
            errors.rejectValue(field, "error.required", new Object[] { fieldName }, "Error");
        }
    }

    public String getReturnUrl(String returnUrl, Patient patient, InsurancePolicy policy) {
        if (StringUtils.isBlank(returnUrl)) {
            returnUrl = "/registrationapp/registrationSummary.page?patientId=" + patient.getId() + "&appId=rwandaemr.registerPatient";
        }
        returnUrl = returnUrl.replace("{{patientId}}", patient.getId().toString());
        String policyId = (policy == null || policy.getInsurancePolicyId() == null ? "" : policy.getInsurancePolicyId().toString());
        returnUrl = returnUrl.replace("{{policyId}}", policyId);
        return returnUrl;
    }

    public List<Patient> getEligiblePolicyOwnersForPatient(Patient patient) {
        List<Patient> ret = new ArrayList<>();
        ret.add(patient);
        // TODO: Should we support owners other than the patient?  Maybe from relationships?
        return ret;
    }

    @Data
    public static class InsurancePolicyModel {

        private Integer policyId;
        private Patient owner;
        private Integer insuranceId;
        private String insuranceCardNo;
        private Date coverageStartDate;
        private Date expirationDate;
        private Integer thirdPartyId;
        private String ownerName;
        private String ownerCode;
        private Integer level;
        private String company;

        public InsurancePolicyModel() {}

        public InsurancePolicyModel(InsurancePolicy policy) {
            this.policyId = policy.getInsurancePolicyId();
            this.owner = policy.getOwner();
            this.insuranceId = policy.getInsurance() == null ? null : policy.getInsurance().getInsuranceId();
            this.insuranceCardNo = policy.getInsuranceCardNo();
            this.coverageStartDate = policy.getCoverageStartDate();
            this.expirationDate = policy.getExpirationDate();
            this.thirdPartyId = policy.getThirdParty() == null ? null : policy.getThirdParty().getThirdPartyId();

            // TODO: This is how things are done in the 1.x code.  Only the owner is supported as a beneficiary.
            if (policy.getBeneficiaries() != null) {
                for (Beneficiary beneficiary : policy.getBeneficiaries()) {
                    this.ownerName = beneficiary.getOwnerName();
                    this.ownerCode = beneficiary.getOwnerCode();
                    this.level = beneficiary.getLevel();
                    this.company = beneficiary.getCompany();
                }
            }
        }
    }
}
