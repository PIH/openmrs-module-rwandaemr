package org.openmrs.module.rwandaemr.paymentclearance;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.businesslogic.ConsommationUtil;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.model.PatientBill;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.labnotification.IntouchSmsClient;
import org.openmrs.module.rwandaemr.labnotification.IntouchSmsResponse;
import org.openmrs.module.rwandaemr.labnotification.LabNotificationConfig;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component("paymentClearanceService")
public class PaymentClearanceService {

	public static final String MESSAGE_TEMPLATE_PROPERTY = "rwandaemr.paymentClearance.messageTemplate";
	public static final String DEFAULT_MESSAGE_TEMPLATE =
			"{patientName}, Turemeza ko serivisi zose wahawe mubitaro zishyuwe. Itariki: {timestamp}";

	private final LabNotificationConfig smsConfig;
	private final IntouchSmsClient smsClient;
	private final PaymentClearanceLogRepository logRepository;

	public PaymentClearanceService(@Autowired LabNotificationConfig smsConfig,
	                               @Autowired IntouchSmsClient smsClient,
	                               @Autowired PaymentClearanceLogRepository logRepository) {
		this.smsConfig = smsConfig;
		this.smsClient = smsClient;
		this.logRepository = logRepository;
	}

	public PaymentClearanceResult sendClearanceSms(GlobalBill globalBill) {
		if (globalBill == null || globalBill.getGlobalBillId() == null) {
			throw new IllegalArgumentException("Global bill is required");
		}
		if (!smsConfig.hasRequiredSettings()) {
			throw new IllegalStateException("SMS gateway settings are not fully configured");
		}
		List<Consommation> consommations = getConsommations(globalBill);
		if (!allPatientBillsPaid(consommations)) {
			throw new IllegalStateException("Clearance SMS can only be sent after all patient bills are paid");
		}
		Patient patient = getPatient(globalBill, consommations);
		if (patient == null) {
			throw new IllegalStateException("Global bill has no patient");
		}
		String phoneNumber = normalizePhone(getPatientPhoneNumber(patient));
		Date now = new Date();
		String message = buildMessage(patient, now);
		IntouchSmsResponse smsResponse = smsClient.send(phoneNumber, message);
		logRepository.saveLogEntry(buildLogEntry(globalBill, patient, phoneNumber, message, smsResponse, now));
		return buildResult(phoneNumber, smsResponse);
	}

	private boolean allPatientBillsPaid(List<Consommation> consommations) {
		boolean hasPatientBill = false;
		for (Consommation consommation : consommations) {
			PatientBill patientBill = consommation == null ? null : consommation.getPatientBill();
			if (patientBill != null) {
				hasPatientBill = true;
				if (!patientBill.isPaid()) {
					return false;
				}
			}
		}
		return hasPatientBill;
	}

	private List<Consommation> getConsommations(GlobalBill globalBill) {
		List<Consommation> consommations = ConsommationUtil.getConsommationsByGlobalBill(globalBill);
		return consommations == null ? Collections.<Consommation>emptyList() : consommations;
	}

	private Patient getPatient(GlobalBill globalBill, List<Consommation> consommations) {
		InsurancePolicy policy = globalBill.getAdmission() == null ? null : globalBill.getAdmission().getInsurancePolicy();
		if (policy != null && policy.getOwner() != null) {
			return policy.getOwner();
		}
		for (Consommation consommation : consommations) {
			if (consommation != null && consommation.getBeneficiary() != null &&
					consommation.getBeneficiary().getPatient() != null) {
				return consommation.getBeneficiary().getPatient();
			}
		}
		return null;
	}

	private String getPatientPhoneNumber(Patient patient) {
		RwandaEmrConfig rwandaEmrConfig = getRwandaEmrConfig();
		if (patient == null || rwandaEmrConfig == null || rwandaEmrConfig.getTelephoneNumber() == null) {
			return null;
		}
		PersonAttribute attribute = patient.getAttribute(rwandaEmrConfig.getTelephoneNumber());
		return attribute == null ? null : attribute.getValue();
	}

	private RwandaEmrConfig getRwandaEmrConfig() {
		List<RwandaEmrConfig> components = Context.getRegisteredComponents(RwandaEmrConfig.class);
		return components == null || components.isEmpty() ? null : components.get(0);
	}

	private String normalizePhone(String phoneNumber) {
		if (StringUtils.isBlank(phoneNumber)) {
			throw new IllegalArgumentException("Patient phone number is required");
		}
		String digits = phoneNumber.replaceAll("[^0-9]", "");
		if (digits.startsWith("250") && digits.length() == 12) {
			return digits;
		}
		if (digits.startsWith("0") && digits.length() == 10) {
			return "250" + digits.substring(1);
		}
		if (digits.startsWith("7") && digits.length() == 9) {
			return "250" + digits;
		}
		throw new IllegalArgumentException("Patient phone number must be a valid Rwanda mobile number");
	}

	private String buildMessage(Patient patient, Date timestamp) {
		String template = StringUtils.defaultIfBlank(ConfigUtil.getProperty(MESSAGE_TEMPLATE_PROPERTY), DEFAULT_MESSAGE_TEMPLATE);
		String patientName = patient.getPersonName() == null ? "" : patient.getPersonName().getFullName();
		String formattedTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
		return template.replace("{patientName}", patientName).replace("{timestamp}", formattedTimestamp);
	}

	private PaymentClearanceLogEntry buildLogEntry(GlobalBill globalBill, Patient patient, String phoneNumber,
	                                               String message, IntouchSmsResponse smsResponse, Date now) {
		PaymentClearanceLogEntry entry = new PaymentClearanceLogEntry();
		entry.setGlobalBillId(globalBill.getGlobalBillId());
		entry.setPatientId(patient.getPatientId());
		PatientIdentifier identifier = patient.getPatientIdentifier();
		entry.setPatientIdentifier(identifier == null ? null : identifier.getIdentifier());
		entry.setPhoneNumber(phoneNumber);
		entry.setMessageContent(message);
		entry.setProviderMessageId(smsResponse.getProviderMessageId());
		entry.setRequestPayload(smsResponse.getRequestPayload());
		entry.setResponseCode(smsResponse.getResponseCode());
		entry.setResponseBody(smsResponse.getResponseBody());
		entry.setStatus(smsResponse.isSuccess() ? "QUEUED" : "FAILED");
		entry.setErrorMessage(smsResponse.getErrorMessage());
		entry.setCreator(Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId());
		entry.setDateCreated(now);
		return entry;
	}

	private PaymentClearanceResult buildResult(String phoneNumber, IntouchSmsResponse smsResponse) {
		PaymentClearanceResult result = new PaymentClearanceResult();
		result.setSuccess(smsResponse.isSuccess());
		result.setStatus(smsResponse.isSuccess() ? "QUEUED" : "FAILED");
		result.setMessage(smsResponse.isSuccess() ? "Payment clearance SMS queued" : smsResponse.getErrorMessage());
		result.setProviderMessageId(smsResponse.getProviderMessageId());
		result.setPhoneNumber(phoneNumber);
		return result;
	}
}
