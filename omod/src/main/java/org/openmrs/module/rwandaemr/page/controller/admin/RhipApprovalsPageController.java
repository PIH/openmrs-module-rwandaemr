package org.openmrs.module.rwandaemr.page.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.integration.IntegrationResponse;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceEligibilityProvider;
import org.openmrs.module.rwandaemr.integration.insurance.InsuranceIntegrationConfig;
import org.openmrs.module.rwandaemr.integration.insurance.MmiPatientReceptionLogRepository;
import org.openmrs.module.rwandaemr.integration.insurance.RhipApprovalRequestLogEntry;
import org.openmrs.module.rwandaemr.integration.insurance.RhipApprovalRequestLogRepository;
import org.openmrs.module.rwandaemr.icd11.Icd11Service;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.ConfigUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RhipApprovalsPageController {

	public static final String PRIVILEGE_VIEW = "Billing RHIP Approval - View";
	public static final String PRIVILEGE_REQUEST = "Billing RHIP Approval - Request";
	public static final String PRIVILEGE_CHECK_STATUS = "Billing RHIP Approval - Check Status";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String PROVIDER_LICENSE_ATTRIBUTE_TYPE_UUID = "mohbilling.rhipVoucher.providerLicenseAttributeTypeUuid";

	public String get(PageModel model,
	                  UiUtils ui,
	                  UiSessionContext sessionContext,
	                  @SpringBean("mohBillingService") BillingService billingService,
	                  @SpringBean InsuranceEligibilityProvider insuranceEligibilityProvider,
	                  @SpringBean InsuranceIntegrationConfig insuranceIntegrationConfig,
	                  @SpringBean RhipApprovalRequestLogRepository approvalRequestLogRepository,
	                  @SpringBean MmiPatientReceptionLogRepository mmiPatientReceptionLogRepository,
	                  @SpringBean Icd11Service icd11Service,
	                  @RequestParam(value = "openRequest", required = false) String openRequest,
	                  @RequestParam(value = "searchQuery", required = false) String searchQuery,
	                  @RequestParam(value = "patientId", required = false) String patientId,
	                  @RequestParam(value = "insurancePolicyId", required = false) Integer insurancePolicyId,
	                  @RequestParam(value = "productSearch", required = false) String productSearch,
	                  @RequestParam(value = "productPage", required = false) Integer productPage,
	                  @RequestParam(value = "productLimit", required = false) Integer productLimit) {
		if (!canView()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to view RHIP approvals.");
			return "redirect:" + ui.pageLink("coreapps", "systemadministration/systemAdministration");
		}

		Patient patient = findPatient(patientId);
		if (patient == null) {
			patient = findPatientBySearch(searchQuery, billingService);
		}
		List<ApprovalInsurancePolicy> approvalPolicies = getApprovalPolicies(patient, billingService, insuranceIntegrationConfig);
		ApprovalInsurancePolicy selectedPolicy = findSelectedPolicy(approvalPolicies, insurancePolicyId);
		String facilityFosaId = insuranceIntegrationConfig.getFacilityFosaId();
		IntegrationResponse productResponse = null;
		List<RhipApprovalProduct> products = Collections.emptyList();
		if (selectedPolicy != null) {
			productResponse = insuranceEligibilityProvider.getApprovalRequiredProducts(selectedPolicy.getInsuranceType(),
					facilityFosaId, productSearch, productPage, productLimit);
			products = extractProducts(productResponse);
		}

		model.addAttribute("searchQuery", searchQuery == null ? "" : searchQuery);
		model.addAttribute("openRequest", StringUtils.isNotBlank(openRequest));
		model.addAttribute("patient", patient);
		model.addAttribute("approvalPolicies", approvalPolicies);
		model.addAttribute("selectedPolicy", selectedPolicy);
		model.addAttribute("patientIdentifier", resolvePatientIdentifier(patient, selectedPolicy));
		model.addAttribute("approvalIdentifier", resolveApprovalIdentifier(selectedPolicy, mmiPatientReceptionLogRepository));
		model.addAttribute("facilityFosaId", facilityFosaId);
		model.addAttribute("productSearch", productSearch == null ? "" : productSearch);
		model.addAttribute("productPage", productPage == null || productPage <= 0 ? 1 : productPage);
		model.addAttribute("productLimit", productLimit == null || productLimit <= 0 ? 20 : productLimit);
		model.addAttribute("products", products);
		model.addAttribute("productError", productResponse == null ? null : productResponse.getErrorMessage());
		model.addAttribute("currentPractitionerLicenseNumber", getCurrentPractitionerLicenseNumber());
		model.addAttribute("patientDiagnoses", getPatientDiagnoses(patient, icd11Service));
		model.addAttribute("approvalRequests", toRequestViews(patient == null
				? approvalRequestLogRepository.getRecent(50)
				: approvalRequestLogRepository.getRecentByPatient(patient.getPatientId(), 25)));
		model.addAttribute("canRequestApproval", Context.hasPrivilege(PRIVILEGE_REQUEST));
		model.addAttribute("canCheckApprovalStatus", Context.hasPrivilege(PRIVILEGE_CHECK_STATUS));
		return null;
	}

	public String post(UiUtils ui,
	                   UiSessionContext sessionContext,
	                   @SpringBean("mohBillingService") BillingService billingService,
	                  @SpringBean InsuranceEligibilityProvider insuranceEligibilityProvider,
	                  @SpringBean InsuranceIntegrationConfig insuranceIntegrationConfig,
	                  @SpringBean RhipApprovalRequestLogRepository approvalRequestLogRepository,
	                  @SpringBean MmiPatientReceptionLogRepository mmiPatientReceptionLogRepository,
	                  HttpServletResponse httpResponse,
	                   @RequestParam(value = "action", required = false) String action,
	                   @RequestParam(value = "approvalRequestId", required = false) Integer approvalRequestId,
	                   @RequestParam(value = "approvalRequestIds", required = false) String approvalRequestIds,
	                   @RequestParam(value = "searchQuery", required = false) String searchQuery,
	                   @RequestParam(value = "patientId", required = false) String patientId,
	                   @RequestParam(value = "insurancePolicyId", required = false) Integer insurancePolicyId,
	                   @RequestParam(value = "productCode", required = false) String productCode,
	                   @RequestParam(value = "productName", required = false) String productName,
	                   @RequestParam(value = "productType", required = false) String productType,
	                   @RequestParam(value = "tariffPrice", required = false) String tariffPrice,
	                   @RequestParam(value = "requestedQuantity", required = false) String requestedQuantity,
	                   @RequestParam(value = "receptionNumber", required = false) String receptionNumber,
	                   @RequestParam(value = "diagnosisIds", required = false) String diagnosisIds,
	                   @RequestParam(value = "clinicalKnowledge", required = false) String clinicalKnowledge,
	                   @RequestParam(value = "practitionerLicenseNumber", required = false) String practitionerLicenseNumber,
	                   @RequestParam(value = "productSearch", required = false) String productSearch,
	                   @RequestParam(value = "productPage", required = false) Integer productPage,
	                   @RequestParam(value = "productLimit", required = false) Integer productLimit) {
		if ("autoCheckStatus".equalsIgnoreCase(action)) {
			return autoCheckStatus(httpResponse, insuranceEligibilityProvider, approvalRequestLogRepository, approvalRequestIds);
		}
		if ("checkStatus".equalsIgnoreCase(action)) {
			return checkStatus(ui, sessionContext, insuranceEligibilityProvider, approvalRequestLogRepository,
					approvalRequestId, searchQuery, patientId, productSearch, productPage, productLimit);
		}
		if ("retryApproval".equalsIgnoreCase(action)) {
			return retryApproval(ui, sessionContext, insuranceEligibilityProvider, approvalRequestLogRepository,
					approvalRequestId, searchQuery, patientId, productSearch, productPage, productLimit);
		}
		if (!Context.hasPrivilege(PRIVILEGE_REQUEST)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to request RHIP approvals.");
			return redirect(ui, searchQuery, patientId, insurancePolicyId, productSearch, productPage, productLimit);
		}

		Patient patient = findPatient(patientId);
		if (patient == null) {
			patient = findPatientBySearch(searchQuery, billingService);
		}
			ApprovalInsurancePolicy selectedPolicy = findSelectedPolicy(
					getApprovalPolicies(patient, billingService, insuranceIntegrationConfig), insurancePolicyId);
			InsurancePolicy policy = selectedPolicy == null ? null : selectedPolicy.getPolicy();
			String insuranceType = selectedPolicy == null ? null : selectedPolicy.getInsuranceType();
			String facilityFosaId = insuranceIntegrationConfig.getFacilityFosaId();
			String patientIdentifier = resolvePatientIdentifier(patient, policy);
			receptionNumber = isMmi(insuranceType) ? resolveApprovalIdentifier(selectedPolicy, mmiPatientReceptionLogRepository) : receptionNumber;
			practitionerLicenseNumber = getCurrentPractitionerLicenseNumber();
		BigDecimal quantity = parseDecimal(requestedQuantity);
		List<String> diagnosisCodeList = splitDiagnosisIds(diagnosisIds);
		List<String> errors = validateApprovalRequest(patient, policy, insuranceType, facilityFosaId, patientIdentifier,
				productCode, quantity, receptionNumber, diagnosisCodeList, clinicalKnowledge, practitionerLicenseNumber);
		if (!errors.isEmpty()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					StringUtils.join(errors, "; "));
			return redirect(ui, searchQuery, patientId, insurancePolicyId, productSearch, productPage, productLimit);
		}

		BigDecimal unitPrice = parseDecimal(tariffPrice);
		IntegrationResponse response = insuranceEligibilityProvider.requestApproval(insuranceType, facilityFosaId,
				patientIdentifier, receptionNumber, practitionerLicenseNumber, diagnosisCodeList, clinicalKnowledge,
				productCode, quantity, unitPrice);
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		String approvalCode = text(root == null ? null : root.path("data"), "approvalCode");
		String approvalStatus = normalizeApprovalStatus(defaultIfBlank(text(root == null ? null : root.path("data"), "status"),
				isSuccess(root, response) ? RhipApprovalRequestLogEntry.STATUS_PENDING : RhipApprovalRequestLogEntry.STATUS_FAILED));
		String message = defaultIfBlank(text(root, "message"), response == null ? null : response.getErrorMessage());

		RhipApprovalRequestLogEntry entry = new RhipApprovalRequestLogEntry();
		entry.setPatientId(patient.getPatientId());
		entry.setInsurancePolicyId(policy.getInsurancePolicyId());
		entry.setInsuranceType(insuranceType);
		entry.setInsuranceName(policy.getInsurance() == null ? null : policy.getInsurance().getName());
		entry.setInsuranceCardNo(policy.getInsuranceCardNo());
		entry.setPatientIdentifier(patientIdentifier);
		entry.setFacilityFosaId(facilityFosaId);
		entry.setProductCode(productCode);
		entry.setProductName(productName);
		entry.setProductType(productType);
		entry.setRequestedQuantity(quantity);
		entry.setRequestedUnitPrice(unitPrice);
		entry.setDiagnosisIds(StringUtils.join(diagnosisCodeList, ","));
		entry.setClinicalKnowledge(clinicalKnowledge);
		entry.setPractitionerLicenseNumber(practitionerLicenseNumber);
		entry.setApprovalCode(approvalCode);
		entry.setApprovalStatus(approvalStatus);
		entry.setMessage(message);
		entry.setRequestPayload(buildApprovalRequestPayload(insuranceType, facilityFosaId, patientIdentifier,
				receptionNumber, practitionerLicenseNumber, diagnosisCodeList, clinicalKnowledge, productCode,
				quantity, unitPrice));
		entry.setResponsePayload(toJson(root));
		entry.setResponseCode(response == null ? null : response.getResponseCode());
		User currentUser = Context.getAuthenticatedUser();
		entry.setCreator(currentUser == null ? null : currentUser.getUserId());
		entry.setDateCreated(new Date());
		approvalRequestLogRepository.save(entry);

		if (isSuccess(root, response)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
					"RHIP approval request submitted" + (StringUtils.isBlank(approvalCode) ? "." : ". Code: " + approvalCode));
		} else {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					defaultIfBlank(message, "RHIP approval request failed."));
		}
		return redirect(ui, searchQuery, patientId, insurancePolicyId, productSearch, productPage, productLimit);
	}

	private String retryApproval(UiUtils ui, UiSessionContext sessionContext, InsuranceEligibilityProvider provider,
	                             RhipApprovalRequestLogRepository repository, Integer approvalRequestId,
	                             String searchQuery, String patientId, String productSearch, Integer productPage,
	                             Integer productLimit) {
		if (!Context.hasPrivilege(PRIVILEGE_REQUEST)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to request RHIP approvals.");
			return redirect(ui, searchQuery, patientId, null, productSearch, productPage, productLimit);
		}
		RhipApprovalRequestLogEntry previous = repository.getById(approvalRequestId);
		if (previous == null) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"Approval request was not found.");
			return redirect(ui, searchQuery, patientId, null, productSearch, productPage, productLimit);
		}
		if (!RhipApprovalRequestLogEntry.STATUS_FAILED.equalsIgnoreCase(StringUtils.trimToEmpty(previous.getApprovalStatus()))) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"Only failed approval requests can be retried.");
			return redirect(ui, searchQuery, patientId, previous.getInsurancePolicyId(), productSearch, productPage, productLimit);
		}

		List<String> diagnosisCodeList = splitDiagnosisIds(previous.getDiagnosisIds());
		String receptionNumber = isMmi(previous.getInsuranceType()) ? text(parseJson(previous.getRequestPayload()), "receptionNumber") : null;
		IntegrationResponse response = provider.requestApproval(previous.getInsuranceType(), previous.getFacilityFosaId(),
				previous.getPatientIdentifier(), receptionNumber, previous.getPractitionerLicenseNumber(), diagnosisCodeList,
				previous.getClinicalKnowledge(), previous.getProductCode(), previous.getRequestedQuantity(),
				previous.getRequestedUnitPrice());
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		String approvalCode = text(root == null ? null : root.path("data"), "approvalCode");
		String approvalStatus = normalizeApprovalStatus(defaultIfBlank(text(root == null ? null : root.path("data"), "status"),
				isSuccess(root, response) ? RhipApprovalRequestLogEntry.STATUS_PENDING : RhipApprovalRequestLogEntry.STATUS_FAILED));
		String message = defaultIfBlank(text(root, "message"), response == null ? null : response.getErrorMessage());

		String requestPayload = buildApprovalRequestPayload(previous.getInsuranceType(), previous.getFacilityFosaId(),
				previous.getPatientIdentifier(), receptionNumber, previous.getPractitionerLicenseNumber(), diagnosisCodeList,
				previous.getClinicalKnowledge(), previous.getProductCode(), previous.getRequestedQuantity(),
				previous.getRequestedUnitPrice());
		User currentUser = Context.getAuthenticatedUser();
		repository.updateRetryResponse(previous.getId(), approvalStatus, approvalCode, message, requestPayload, toJson(root),
				response == null ? null : response.getResponseCode(), currentUser == null ? null : currentUser.getUserId());

		if (isSuccess(root, response)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
					"RHIP approval request retry submitted" + (StringUtils.isBlank(approvalCode) ? "." : ". Code: " + approvalCode));
		}
		else {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					defaultIfBlank(message, "RHIP approval request retry failed."));
		}
		Patient patient = previous.getPatientId() == null ? null : Context.getPatientService().getPatient(previous.getPatientId());
		return redirect(ui, searchQuery, patient == null ? patientId : patient.getUuid(), previous.getInsurancePolicyId(),
				productSearch, productPage, productLimit);
	}

	private String checkStatus(UiUtils ui, UiSessionContext sessionContext, InsuranceEligibilityProvider provider,
	                           RhipApprovalRequestLogRepository repository, Integer approvalRequestId, String searchQuery,
	                           String patientId, String productSearch, Integer productPage, Integer productLimit) {
		if (!Context.hasPrivilege(PRIVILEGE_CHECK_STATUS)) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to check RHIP approval status.");
			return redirect(ui, searchQuery, patientId, null, productSearch, productPage, productLimit);
		}
		RhipApprovalRequestLogEntry entry = repository.getById(approvalRequestId);
		if (entry == null || StringUtils.isBlank(entry.getApprovalCode())) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"Approval request or approval code was not found.");
			return redirect(ui, searchQuery, patientId, null, productSearch, productPage, productLimit);
		}
		ApprovalStatusUpdate update = updateApprovalStatus(provider, repository, entry);
		sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE,
				"RHIP approval status updated to " + update.getNewStatus() + ".");
		return redirect(ui, searchQuery, patientId, entry.getInsurancePolicyId(), productSearch, productPage, productLimit);
	}

	private String autoCheckStatus(HttpServletResponse httpResponse, InsuranceEligibilityProvider provider,
	                               RhipApprovalRequestLogRepository repository, String approvalRequestIds) {
		Map<String, Object> payload = new LinkedHashMap<String, Object>();
		List<Map<String, Object>> updates = new ArrayList<Map<String, Object>>();
		payload.put("success", false);
		payload.put("updates", updates);
		if (!Context.hasPrivilege(PRIVILEGE_CHECK_STATUS)) {
			payload.put("message", "You do not have permission to check RHIP approval status.");
			writeJson(httpResponse, payload);
			return null;
		}
		for (Integer approvalRequestId : splitIntegerIds(approvalRequestIds)) {
			RhipApprovalRequestLogEntry entry = repository.getById(approvalRequestId);
			if (entry == null || StringUtils.isBlank(entry.getApprovalCode())) {
				continue;
			}
			ApprovalStatusUpdate update = updateApprovalStatus(provider, repository, entry);
			Map<String, Object> updatePayload = new LinkedHashMap<String, Object>();
			updatePayload.put("id", entry.getId());
			updatePayload.put("oldStatus", update.getOldStatus());
			updatePayload.put("newStatus", update.getNewStatus());
			updatePayload.put("statusLabel", getApprovalStatusLabel(update.getNewStatus()));
			updatePayload.put("changed", update.isChanged());
			updatePayload.put("patientName", getPatientName(entry.getPatientId()));
			updatePayload.put("procedureName", entry.getProductName());
			updatePayload.put("message", update.getMessage());
			updates.add(updatePayload);
		}
		payload.put("success", true);
		payload.put("message", "Approval statuses checked.");
		writeJson(httpResponse, payload);
		return null;
	}

	private List<ApprovalRequestView> toRequestViews(List<RhipApprovalRequestLogEntry> entries) {
		List<ApprovalRequestView> views = new ArrayList<ApprovalRequestView>();
		Map<Integer, Patient> patients = new HashMap<Integer, Patient>();
		for (RhipApprovalRequestLogEntry entry : entries == null
				? Collections.<RhipApprovalRequestLogEntry>emptyList() : entries) {
			Patient patient = null;
			if (entry.getPatientId() != null) {
				if (!patients.containsKey(entry.getPatientId())) {
					patients.put(entry.getPatientId(), Context.getPatientService().getPatient(entry.getPatientId()));
				}
				patient = patients.get(entry.getPatientId());
			}
			views.add(new ApprovalRequestView(entry, patient));
		}
		return views;
	}

	private List<ApprovalDiagnosisView> getPatientDiagnoses(Patient patient, Icd11Service icd11Service) {
		if (patient == null || icd11Service == null) {
			return Collections.emptyList();
		}
		List<ApprovalDiagnosisView> views = new ArrayList<ApprovalDiagnosisView>();
		List<Icd11PatientDiagnosis> diagnoses = icd11Service.getDiagnoses(patient, null);
		for (Icd11PatientDiagnosis diagnosis : diagnoses == null
				? Collections.<Icd11PatientDiagnosis>emptyList() : diagnoses) {
			String code = diagnosis == null ? null : StringUtils.trimToNull(diagnosis.getIcd11Code());
			if (StringUtils.isBlank(code)) {
				continue;
			}
			String title = diagnosis == null ? null : StringUtils.trimToNull(diagnosis.getTitle());
			views.add(new ApprovalDiagnosisView(code, title));
		}
		return views;
	}

	private String getCurrentPractitionerLicenseNumber() {
		User user = Context.getAuthenticatedUser();
		if (user == null || user.getPerson() == null) {
			return "";
		}
		String attributeTypeUuid = ConfigUtil.getProperty(PROVIDER_LICENSE_ATTRIBUTE_TYPE_UUID);
		if (StringUtils.isBlank(attributeTypeUuid)) {
			return "";
		}
		ProviderAttributeType attributeType = Context.getProviderService().getProviderAttributeTypeByUuid(attributeTypeUuid);
		if (attributeType == null) {
			return "";
		}
		Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(user.getPerson(), false);
		if (providers == null) {
			return "";
		}
		for (Provider provider : providers) {
			if (provider == null) {
				continue;
			}
			for (ProviderAttribute attribute : provider.getAttributes()) {
				if (attribute != null && attributeType.equals(attribute.getAttributeType())) {
					String value = attribute.getValueReference();
					if (StringUtils.isBlank(value) && attribute.getValue() != null) {
						value = attribute.getValue().toString();
					}
					if (StringUtils.isNotBlank(value)) {
						return value.trim();
					}
				}
			}
		}
		return "";
	}

	private boolean canView() {
		return Context.hasPrivilege(PRIVILEGE_VIEW) || Context.hasPrivilege("Billing Configuration - View Billing Admin");
	}

	private Patient findPatient(String patientId) {
		if (StringUtils.isBlank(patientId)) {
			return null;
		}
		Patient patient = Context.getPatientService().getPatientByUuid(patientId.trim());
		if (patient != null) {
			return patient;
		}
		try {
			return Context.getPatientService().getPatient(Integer.valueOf(patientId.trim()));
		}
		catch (Exception e) {
			return null;
		}
	}

	private Patient findPatientBySearch(String searchQuery, BillingService billingService) {
		if (StringUtils.isBlank(searchQuery)) {
			return null;
		}
		String query = searchQuery.trim();
		Patient patient = findPatient(query);
		if (patient != null) {
			return patient;
		}
		if (billingService != null) {
			try {
				InsurancePolicy policy = billingService.getInsurancePolicyByCardNo(query);
				if (policy != null) {
					return policy.getOwner();
				}
			}
			catch (Exception ignored) {
			}
		}
		List<Patient> matches = Context.getPatientService().getPatients(query);
		return matches == null || matches.size() != 1 ? null : matches.get(0);
	}

	private List<ApprovalInsurancePolicy> getApprovalPolicies(Patient patient, BillingService billingService,
	                                                          InsuranceIntegrationConfig config) {
		if (patient == null || billingService == null) {
			return Collections.emptyList();
		}
		Map<Insurance, String> insurancesToVerify = config.getInsurancesToVerify();
		List<ApprovalInsurancePolicy> ret = new ArrayList<ApprovalInsurancePolicy>();
		List<InsurancePolicy> policies = billingService.getAllInsurancePoliciesByPatient(patient);
		for (InsurancePolicy policy : policies == null ? Collections.<InsurancePolicy>emptyList() : policies) {
				if (policy == null || policy.isRetired() || isExpired(policy)) {
					continue;
				}
			String insuranceType = resolveInsuranceType(policy.getInsurance(), insurancesToVerify);
			if (StringUtils.isBlank(insuranceType)) {
				continue;
			}
			ret.add(new ApprovalInsurancePolicy(policy, insuranceType));
		}
		return ret;
	}

	private ApprovalInsurancePolicy findSelectedPolicy(List<ApprovalInsurancePolicy> policies, Integer insurancePolicyId) {
		if (policies == null || policies.isEmpty()) {
			return null;
		}
		if (insurancePolicyId != null) {
			for (ApprovalInsurancePolicy policy : policies) {
				if (policy != null && policy.getPolicy() != null
						&& insurancePolicyId.equals(policy.getPolicy().getInsurancePolicyId())) {
					return policy;
				}
			}
		}
		return policies.get(0);
	}

	private String resolveInsuranceType(Insurance insurance, Map<Insurance, String> insurancesToVerify) {
		if (insurance == null) {
			return null;
		}
		String configuredType = insurancesToVerify == null ? null : insurancesToVerify.get(insurance);
		if (StringUtils.isNotBlank(configuredType)) {
			return configuredType.trim().toLowerCase();
		}
		if (isInsuranceText(insurance.getCategory(), "MMI") || isInsuranceText(insurance.getName(), "MMI")) {
			return "mmi";
		}
		if (isInsuranceText(insurance.getCategory(), "RAMA") || isInsuranceText(insurance.getName(), "RAMA")
				|| isInsuranceText(insurance.getName(), "RSSB")) {
			return "rama";
		}
		return null;
	}

	private boolean isInsuranceText(String value, String token) {
		return StringUtils.isNotBlank(value)
				&& (token.equalsIgnoreCase(value.trim()) || value.toUpperCase().contains(token.toUpperCase()));
	}

	private String resolvePatientIdentifier(Patient patient, ApprovalInsurancePolicy selectedPolicy) {
		return resolvePatientIdentifier(patient, selectedPolicy == null ? null : selectedPolicy.getPolicy());
	}

	private String resolvePatientIdentifier(Patient patient, InsurancePolicy policy) {
		if (policy != null && StringUtils.isNotBlank(policy.getRhipPatientId())) {
			return policy.getRhipPatientId().trim();
		}
		if (policy != null && StringUtils.isNotBlank(policy.getInsuranceCardNo())) {
			return policy.getInsuranceCardNo().trim();
		}
		PatientIdentifier identifier = patient == null ? null : patient.getPatientIdentifier();
		return identifier == null ? null : identifier.getIdentifier();
	}

	private String resolveApprovalIdentifier(ApprovalInsurancePolicy selectedPolicy,
	                                        MmiPatientReceptionLogRepository mmiPatientReceptionLogRepository) {
		if (selectedPolicy == null || selectedPolicy.getPolicy() == null) {
			return "";
		}
		InsurancePolicy policy = selectedPolicy.getPolicy();
		if (isMmi(selectedPolicy.getInsuranceType())) {
			return mmiPatientReceptionLogRepository == null ? "" : StringUtils.defaultString(
					mmiPatientReceptionLogRepository.getLatestSuccessfulReceptionNumberForInsuranceCard(policy.getInsuranceCardNo()));
		}
		return StringUtils.defaultString(resolvePatientIdentifier(policy.getOwner(), policy));
	}

	private boolean isExpired(InsurancePolicy policy) {
		if (policy == null || policy.getExpirationDate() == null) {
			return false;
		}
		return policy.getExpirationDate().before(startOfDay(new Date()));
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

	private List<RhipApprovalProduct> extractProducts(IntegrationResponse response) {
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		JsonNode productsNode = root == null ? null : root.path("data").path("products");
		if (productsNode == null || !productsNode.isArray()) {
			productsNode = root == null ? null : root.path("data").path("procedures");
		}
		if (productsNode == null || !productsNode.isArray()) {
			return Collections.emptyList();
		}
		List<RhipApprovalProduct> ret = new ArrayList<RhipApprovalProduct>();
		for (JsonNode node : productsNode) {
			RhipApprovalProduct product = new RhipApprovalProduct();
			product.setProductCode(firstText(node, "productCode", "rhicCode", "code"));
			product.setProductName(firstText(node, "productName", "procedureName", "name"));
			product.setProductType(firstText(node, "productType", "procedureType", "type"));
			product.setUnit(text(node, "unit"));
			product.setTariffPrice(firstText(node, "tariffPrice", "requestedUnitPrice", "unitPrice", "price"));
			ret.add(product);
		}
		return ret;
	}

	private List<String> validateApprovalRequest(Patient patient, InsurancePolicy policy, String insuranceType,
	                                             String facilityFosaId, String patientIdentifier, String productCode,
	                                             BigDecimal quantity, String receptionNumber, List<String> diagnosisIds,
	                                             String clinicalKnowledge, String practitionerLicenseNumber) {
		List<String> errors = new ArrayList<String>();
		if (patient == null) {
			errors.add("Patient is required");
		}
		if (policy == null) {
			errors.add("An approval-supported insurance policy is required");
		}
		if (StringUtils.isBlank(insuranceType)) {
			errors.add("Insurance type is not configured for RHIP approvals");
		}
		if (StringUtils.isBlank(facilityFosaId)) {
			errors.add("Facility FOSA ID is not configured");
		}
		if (!isMmi(insuranceType) && StringUtils.isBlank(patientIdentifier)) {
			errors.add("Patient RHIP identifier or insurance card number is required");
		}
		if (isMmi(insuranceType) && StringUtils.isBlank(receptionNumber)) {
			errors.add("MMI reception number is required");
		}
		if (StringUtils.isBlank(productCode)) {
			errors.add("Approval procedure is required");
		}
		if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
			errors.add("Requested quantity must be greater than zero");
		}
		if (!isMmi(insuranceType) && (diagnosisIds == null || diagnosisIds.isEmpty())) {
			errors.add("At least one diagnosis code is required");
		}
		if (StringUtils.isBlank(clinicalKnowledge)) {
			errors.add(isMmi(insuranceType) ? "Comment is required" : "Clinical justification is required");
		}
		if (StringUtils.isBlank(practitionerLicenseNumber)) {
			errors.add("Practitioner license number is required");
		}
		return errors;
	}

	private List<String> splitDiagnosisIds(String diagnosisIds) {
		List<String> ret = new ArrayList<String>();
		if (StringUtils.isBlank(diagnosisIds)) {
			return ret;
		}
		for (String value : diagnosisIds.split("[,;\\n\\r]+")) {
			if (StringUtils.isNotBlank(value)) {
				ret.add(value.trim());
			}
		}
		return ret;
	}

	private List<Integer> splitIntegerIds(String ids) {
		List<Integer> ret = new ArrayList<Integer>();
		if (StringUtils.isBlank(ids)) {
			return ret;
		}
		for (String value : ids.split("[,;\\s]+")) {
			try {
				if (StringUtils.isNotBlank(value)) {
					ret.add(Integer.valueOf(value.trim()));
				}
			}
			catch (Exception ignored) {
			}
		}
		return ret;
	}

	private String buildApprovalRequestPayload(String insuranceType, String facilityFosaId, String patientIdentifier,
	                                           String receptionNumber, String practitionerLicenseNumber,
	                                           List<String> diagnosisIds, String clinicalKnowledge, String productCode,
	                                           BigDecimal quantity, BigDecimal unitPrice) {
		Map<String, Object> payload = new LinkedHashMap<String, Object>();
		payload.put("insuranceType", insuranceType);
		payload.put("facilityFosaId", facilityFosaId);
		if (isMmi(insuranceType)) {
			payload.put("receptionNumber", receptionNumber);
			payload.put("licenseNumber", practitionerLicenseNumber);
			payload.put("comment", clinicalKnowledge);
		}
		else {
			payload.put("patientIdentifier", patientIdentifier);
			payload.put("practitionerLicenseNumber", practitionerLicenseNumber);
			payload.put("diagnosisIds", diagnosisIds);
			payload.put("clinicalKnowledge", clinicalKnowledge);
		}
		List<Map<String, Object>> procedures = new ArrayList<Map<String, Object>>();
		Map<String, Object> procedure = new LinkedHashMap<String, Object>();
		procedure.put("rhicCode", productCode);
		procedure.put("requestedQuantity", quantity);
		if (unitPrice != null) {
			procedure.put("requestedUnitPrice", unitPrice);
		}
		procedures.add(procedure);
		payload.put("procedures", procedures);
		return toJson(payload);
	}

	private String redirect(UiUtils ui, String searchQuery, String patientId, Integer insurancePolicyId,
	                        String productSearch, Integer productPage, Integer productLimit) {
		StringBuilder params = new StringBuilder();
		appendQueryParam(params, "searchQuery", searchQuery);
		appendQueryParam(params, "patientId", patientId);
		appendQueryParam(params, "insurancePolicyId", insurancePolicyId == null ? null : insurancePolicyId.toString());
		appendQueryParam(params, "productSearch", productSearch);
		appendQueryParam(params, "productPage", productPage == null ? null : productPage.toString());
		appendQueryParam(params, "productLimit", productLimit == null ? null : productLimit.toString());
		String pageLink = ui.pageLink("rwandaemr", "admin/rhipApprovals");
		return "redirect:" + pageLink + (params.length() == 0 ? "" : "?" + params);
	}

	private void appendQueryParam(StringBuilder builder, String name, String value) {
		if (StringUtils.isBlank(value)) {
			return;
		}
		if (builder.length() > 0) {
			builder.append("&");
		}
		try {
			builder.append(name).append("=").append(URLEncoder.encode(value, "UTF-8"));
		}
		catch (Exception e) {
			builder.append(name).append("=").append(value);
		}
	}

	private BigDecimal parseDecimal(String value) {
		try {
			return StringUtils.isBlank(value) ? null : new BigDecimal(value.trim());
		}
		catch (Exception e) {
			return null;
		}
	}

	private JsonNode asJsonNode(Object value) {
		if (value instanceof JsonNode) {
			return (JsonNode) value;
		}
		return null;
	}

	private JsonNode parseJson(String value) {
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readTree(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	private String resolveApprovalStatusPeriod(RhipApprovalRequestLogEntry entry) {
		JsonNode requestPayload = parseJson(entry == null ? null : entry.getRequestPayload());
		String configuredPeriod = text(requestPayload, "period");
		return StringUtils.isBlank(configuredPeriod) ? null : configuredPeriod;
	}

	private ApprovalStatusUpdate updateApprovalStatus(InsuranceEligibilityProvider provider,
	                                                  RhipApprovalRequestLogRepository repository,
	                                                  RhipApprovalRequestLogEntry entry) {
		String oldStatus = normalizeApprovalStatus(entry == null ? null : entry.getApprovalStatus());
		IntegrationResponse response = provider.checkApprovalStatus(entry.getInsuranceType(), entry.getApprovalCode(),
				entry.getFacilityFosaId(), resolveApprovalStatusPeriod(entry));
		JsonNode root = asJsonNode(response == null ? null : response.getResponseEntity());
		String status = resolveApprovalStatus(entry, root, response);
		String message = defaultIfBlank(text(root, "message"), response == null ? null : response.getErrorMessage());
		User currentUser = Context.getAuthenticatedUser();
		repository.updateStatus(entry.getId(), status, entry.getApprovalCode(), message, toJson(root),
				response == null ? null : response.getResponseCode(), currentUser == null ? null : currentUser.getUserId());
		return new ApprovalStatusUpdate(oldStatus, status, message);
	}

	private String resolveApprovalStatus(RhipApprovalRequestLogEntry entry, JsonNode root, IntegrationResponse response) {
		JsonNode data = root == null ? null : root.path("data");
		String status = normalizeApprovalStatus(text(data, "status"));
		if (StringUtils.isNotBlank(status)) {
			return status;
		}
		if (!isSuccess(root, response)) {
			return RhipApprovalRequestLogEntry.STATUS_FAILED;
		}
		if (isMmi(entry == null ? null : entry.getInsuranceType()) && hasPositiveAmount(data, "totalApprovalAmount")) {
			return RhipApprovalRequestLogEntry.STATUS_APPROVED;
		}
		status = normalizeApprovalStatus(entry == null ? null : entry.getApprovalStatus());
		return StringUtils.isBlank(status) ? RhipApprovalRequestLogEntry.STATUS_PENDING : status;
	}

	private boolean hasPositiveAmount(JsonNode node, String name) {
		if (node == null || node.isMissingNode() || node.path(name).isMissingNode() || node.path(name).isNull()) {
			return false;
		}
		try {
			return node.path(name).decimalValue().compareTo(BigDecimal.ZERO) > 0;
		}
		catch (Exception e) {
			return false;
		}
	}

	private boolean isSuccess(JsonNode root, IntegrationResponse response) {
		if (root != null && root.has("success")) {
			return root.path("success").asBoolean(false);
		}
		Integer code = response == null ? null : response.getResponseCode();
		return code != null && code >= 200 && code < 300 && StringUtils.isBlank(response.getErrorMessage());
	}

	private String text(JsonNode node, String name) {
		if (node == null || node.isMissingNode() || node.path(name).isMissingNode() || node.path(name).isNull()) {
			return null;
		}
		return node.path(name).asText();
	}

	private String firstText(JsonNode node, String... names) {
		if (names == null) {
			return null;
		}
		for (String name : names) {
			String value = text(node, name);
			if (StringUtils.isNotBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private boolean isMmi(String insuranceType) {
		return "mmi".equalsIgnoreCase(StringUtils.trimToEmpty(insuranceType));
	}

	private String normalizeApprovalStatus(String status) {
		if (StringUtils.isBlank(status)) {
			return status;
		}
		String normalized = status.trim().toUpperCase();
		if ("CREATED".equals(normalized) || "EDITED".equals(normalized) || "REQUESTED".equals(normalized)) {
			return RhipApprovalRequestLogEntry.STATUS_PENDING;
		}
		return normalized;
	}

	private String getApprovalStatusLabel(String status) {
		String normalized = normalizeApprovalStatus(status);
		if (RhipApprovalRequestLogEntry.STATUS_PENDING.equals(normalized)) {
			return "Requested/Pending";
		}
		return StringUtils.defaultString(normalized);
	}

	private String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.isBlank(value) ? defaultValue : value;
	}

	private String getPatientName(Integer patientId) {
		Patient patient = patientId == null ? null : Context.getPatientService().getPatient(patientId);
		return patient == null || patient.getPersonName() == null ? "" : patient.getPersonName().getFullName();
	}

	private String toJson(Object value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(value);
		}
		catch (Exception e) {
			return null;
		}
	}

	private void writeJson(HttpServletResponse response, Object payload) {
		if (response == null) {
			return;
		}
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		try {
			OBJECT_MAPPER.writeValue(response.getWriter(), payload);
		}
		catch (Exception e) {
		}
	}

	public static class ApprovalInsurancePolicy {
		private final InsurancePolicy policy;
		private final String insuranceType;

		public ApprovalInsurancePolicy(InsurancePolicy policy, String insuranceType) {
			this.policy = policy;
			this.insuranceType = insuranceType;
		}

		public InsurancePolicy getPolicy() {
			return policy;
		}

		public String getInsuranceType() {
			return insuranceType;
		}

		public String getLabel() {
			String insuranceName = policy == null || policy.getInsurance() == null ? "" : policy.getInsurance().getName();
			String cardNo = policy == null ? "" : policy.getInsuranceCardNo();
			return StringUtils.defaultString(insuranceName) + " / " + StringUtils.defaultString(cardNo);
		}
	}

	public static class ApprovalRequestView {
		private final RhipApprovalRequestLogEntry request;
		private final Patient patient;

		public ApprovalRequestView(RhipApprovalRequestLogEntry request, Patient patient) {
			this.request = request;
			this.patient = patient;
		}

		public RhipApprovalRequestLogEntry getRequest() {
			return request;
		}

		public Patient getPatient() {
			return patient;
		}

		public String getPatientName() {
			return patient == null ? "" : patient.getPersonName().getFullName();
		}

		public String getStatusClass() {
			String status = request == null ? "" : StringUtils.trimToEmpty(request.getApprovalStatus()).toUpperCase();
			return StringUtils.isBlank(status) ? RhipApprovalRequestLogEntry.STATUS_PENDING : status;
		}

		public String getStatusLabel() {
			String status = getStatusClass();
			if (RhipApprovalRequestLogEntry.STATUS_PENDING.equals(status)) {
				return "Requested/Pending";
			}
			return status;
		}
	}

	private static class ApprovalStatusUpdate {
		private final String oldStatus;
		private final String newStatus;
		private final String message;

		private ApprovalStatusUpdate(String oldStatus, String newStatus, String message) {
			this.oldStatus = StringUtils.defaultIfBlank(oldStatus, RhipApprovalRequestLogEntry.STATUS_PENDING);
			this.newStatus = StringUtils.defaultIfBlank(newStatus, RhipApprovalRequestLogEntry.STATUS_PENDING);
			this.message = message;
		}

		public String getOldStatus() {
			return oldStatus;
		}

		public String getNewStatus() {
			return newStatus;
		}

		public String getMessage() {
			return message;
		}

		public boolean isChanged() {
			return !StringUtils.equalsIgnoreCase(oldStatus, newStatus);
		}
	}

	public static class ApprovalDiagnosisView {
		private final String code;
		private final String title;

		public ApprovalDiagnosisView(String code, String title) {
			this.code = code;
			this.title = title;
		}

		public String getCode() {
			return code;
		}

		public String getTitle() {
			return title;
		}

		public String getLabel() {
			return StringUtils.isBlank(title) ? code : code + " - " + title;
		}
	}

	public static class RhipApprovalProduct {
		private String productCode;
		private String productName;
		private String productType;
		private String unit;
		private String tariffPrice;

		public String getProductCode() {
			return productCode;
		}

		public void setProductCode(String productCode) {
			this.productCode = productCode;
		}

		public String getProductName() {
			return productName;
		}

		public void setProductName(String productName) {
			this.productName = productName;
		}

		public String getProductType() {
			return productType;
		}

		public void setProductType(String productType) {
			this.productType = productType;
		}

		public String getUnit() {
			return unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

		public String getTariffPrice() {
			return tariffPrice;
		}

		public void setTariffPrice(String tariffPrice) {
			this.tariffPrice = tariffPrice;
		}
	}
}
