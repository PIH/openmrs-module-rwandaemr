package org.openmrs.module.rwandaemr.labnotification;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.PersonAttribute;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class LabNotificationService {

	protected final Log log = LogFactory.getLog(getClass());

	private final OrderService orderService;
	private final RwandaEmrService rwandaEmrService;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final LabNotificationConfig config;
	private final IntouchSmsClient smsClient;
	private final LabNotificationLogRepository logRepository;

	public LabNotificationService(@Autowired OrderService orderService,
	                              @Autowired RwandaEmrService rwandaEmrService,
	                              @Autowired RwandaEmrConfig rwandaEmrConfig,
	                              @Autowired LabNotificationConfig config,
	                              @Autowired IntouchSmsClient smsClient,
	                              @Autowired LabNotificationLogRepository logRepository) {
		this.orderService = orderService;
		this.rwandaEmrService = rwandaEmrService;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.config = config;
		this.smsClient = smsClient;
		this.logRepository = logRepository;
	}

	public LabNotificationResult notifyPatient(String orderUuid, String phoneNumberOverride, String messageOverride,
	                                           boolean force) {
		if (!config.isEnabled()) {
			throw new IllegalStateException("Lab notification SMS integration is not enabled");
		}
		if (!config.hasRequiredSettings()) {
			throw new IllegalStateException("Lab notification SMS integration is not fully configured");
		}
		if (StringUtils.isBlank(orderUuid)) {
			throw new IllegalArgumentException("orderUuid is required");
		}
		Order order = orderService.getOrderByUuid(orderUuid.trim());
		if (order == null) {
			throw new IllegalArgumentException("Order was not found: " + orderUuid);
		}
		if (!(order instanceof TestOrder)) {
			throw new IllegalArgumentException("Order is not a lab test order: " + orderUuid);
		}
		if (!isResultReady(order)) {
			throw new IllegalStateException("Lab result is not marked ready for notification");
		}
		if (!force && logRepository.hasActiveNotificationForOrder(order.getOrderId())) {
			throw new IllegalStateException("A lab notification has already been sent for this order");
		}

		Patient patient = order.getPatient();
		if (patient == null) {
			throw new IllegalStateException("Order has no patient");
		}
		String phoneNumber = normalizePhone(StringUtils.defaultIfBlank(phoneNumberOverride, getPatientPhoneNumber(patient)));
		String message = StringUtils.defaultIfBlank(messageOverride, buildDefaultMessage(order, patient));
		IntouchSmsResponse smsResponse = smsClient.send(phoneNumber, message);
		LabNotificationLogEntry logEntry = buildLogEntry(order, patient, phoneNumber, message, smsResponse);
		logRepository.saveLogEntry(logEntry);
		return buildResult(order, patient, phoneNumber, smsResponse);
	}

	public boolean recordDeliveryReport(String providerMessageId, String messageStatus, String level) {
		String status = StringUtils.defaultIfBlank(StringUtils.trimToNull(messageStatus), "UNKNOWN");
		boolean updated = logRepository.updateDeliveryReport(StringUtils.trimToNull(providerMessageId), status,
				StringUtils.trimToNull(level));
		if (!updated) {
			log.warn("Received lab notification delivery report for unknown message id: " + providerMessageId);
		}
		return updated;
	}

	private boolean isResultReady(Order order) {
		if (Order.FulfillerStatus.COMPLETED.equals(order.getFulfillerStatus())) {
			return true;
		}
		List<Obs> resultObs = rwandaEmrService.getObsByOrder(order);
		return resultObs != null && !resultObs.isEmpty();
	}

	private String getPatientPhoneNumber(Patient patient) {
		if (patient == null || rwandaEmrConfig.getTelephoneNumber() == null) {
			return null;
		}
		PersonAttribute attribute = patient.getAttribute(rwandaEmrConfig.getTelephoneNumber());
		return attribute == null ? null : attribute.getValue();
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

	private String buildDefaultMessage(Order order, Patient patient) {
		String message = config.getMessageTemplate();
		return message.replace("{facility}", StringUtils.defaultIfBlank(getFacilityName(order), "the health facility"))
				.replace("{patientName}", patient.getPersonName() == null ? "" : patient.getPersonName().getFullName())
				.replace("{order}", getOrderDisplay(order));
	}

	private String getFacilityName(Order order) {
		Location location = order.getEncounter() == null ? null : order.getEncounter().getLocation();
		if (location == null && Context.getUserContext() != null) {
			location = Context.getUserContext().getLocation();
		}
		return location == null ? null : location.getName();
	}

	private String getOrderDisplay(Order order) {
		if (order instanceof TestOrder && ((TestOrder) order).getConcept() != null) {
			return ((TestOrder) order).getConcept().getDisplayString();
		}
		return "";
	}

	private LabNotificationLogEntry buildLogEntry(Order order, Patient patient, String phoneNumber, String message,
	                                              IntouchSmsResponse smsResponse) {
		LabNotificationLogEntry entry = new LabNotificationLogEntry();
		entry.setPatientId(patient.getPatientId());
		entry.setOrderId(order.getOrderId());
		entry.setOrderUuid(order.getUuid());
		entry.setPhoneNumber(phoneNumber);
		entry.setMessageContent(message);
		entry.setProviderMessageId(smsResponse.getProviderMessageId());
		entry.setRequestPayload(smsResponse.getRequestPayload());
		entry.setResponseCode(smsResponse.getResponseCode());
		entry.setResponseBody(smsResponse.getResponseBody());
		entry.setStatus(smsResponse.isSuccess() ? "QUEUED" : "FAILED");
		entry.setErrorMessage(smsResponse.getErrorMessage());
		entry.setCreator(Context.getAuthenticatedUser() == null ? null : Context.getAuthenticatedUser().getUserId());
		entry.setDateCreated(new Date());
		return entry;
	}

	private LabNotificationResult buildResult(Order order, Patient patient, String phoneNumber,
	                                          IntouchSmsResponse smsResponse) {
		LabNotificationResult result = new LabNotificationResult();
		result.setSuccess(smsResponse.isSuccess());
		result.setStatus(smsResponse.isSuccess() ? "QUEUED" : "FAILED");
		result.setMessage(smsResponse.isSuccess() ? "Lab notification SMS queued" : smsResponse.getErrorMessage());
		result.setProviderMessageId(smsResponse.getProviderMessageId());
		result.setOrderUuid(order.getUuid());
		result.setPatientUuid(patient.getUuid());
		result.setPhoneNumber(phoneNumber);
		result.setResponseCode(smsResponse.getResponseCode());
		result.setResponseBody(smsResponse.getResponseBody());
		return result;
	}
}
