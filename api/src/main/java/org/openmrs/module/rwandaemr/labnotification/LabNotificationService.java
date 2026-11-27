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
import org.openmrs.module.rbcmessaging.api.RBCMessagingService;
import org.openmrs.module.rbcmessaging.sms.SmsResult;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.sms.SmsMessageFormatter;
import org.openmrs.module.pihapps.orders.LabOrderConfig;
import org.openmrs.module.rwandaemr.labs.LabResultValidation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LabNotificationService {

	public static final String REQUIRE_RESULT_VALIDATION = LabOrderConfig.GP_REQUIRE_RESULT_VALIDATION;

	private static final int MAX_PROVIDER_MESSAGE_ID_LENGTH = 100;

	protected final Log log = LogFactory.getLog(getClass());

	private final OrderService orderService;
	private final RwandaEmrService rwandaEmrService;
	private final RwandaEmrConfig rwandaEmrConfig;
	private final LabNotificationConfig config;
	private final IntouchSmsClient smsClient;
	private final LabNotificationLogRepository logRepository;
	private final SmsMessageFormatter smsMessageFormatter;
	private RBCMessagingService rbcMessagingService;

	public LabNotificationService(@Autowired OrderService orderService,
	                              @Autowired RwandaEmrService rwandaEmrService,
	                              @Autowired RwandaEmrConfig rwandaEmrConfig,
	                              @Autowired LabNotificationConfig config,
	                              @Autowired IntouchSmsClient smsClient,
	                              @Autowired LabNotificationLogRepository logRepository,
	                              @Autowired SmsMessageFormatter smsMessageFormatter) {
		this.orderService = orderService;
		this.rwandaEmrService = rwandaEmrService;
		this.rwandaEmrConfig = rwandaEmrConfig;
		this.config = config;
		this.smsClient = smsClient;
		this.logRepository = logRepository;
		this.smsMessageFormatter = smsMessageFormatter;
	}

	/**
	 * Notifies the patient of a single lab order's results.
	 * Same as {@link #notifyPatient(String, List, String, String, boolean)} with one order.
	 */
	public LabNotificationResult notifyPatient(String orderUuid, String phoneNumberOverride, String messageOverride,
	                                           boolean force) {
		checkConfigured();
		if (StringUtils.isBlank(orderUuid)) {
			throw new IllegalArgumentException("orderUuid is required");
		}
		return notifyPatient(null, Collections.singletonList(orderUuid), phoneNumberOverride, messageOverride, force);
	}

	/**
	 * Sends one SMS to the patient for all the given lab orders that are ready and not yet notified.
	 * Orders that are not ready, or were already notified (unless force is true), are skipped and reported back.
	 * One log entry is written per notified order, all sharing the same provider message id.
	 * @param patientUuid optional; if given, every order must belong to this patient
	 * @throws IllegalArgumentException if an order is missing, not a lab order, or belongs to another patient
	 * @throws IllegalStateException if the integration is not configured, or no order can be notified
	 */
	public LabNotificationResult notifyPatient(String patientUuid, List<String> orderUuids, String phoneNumberOverride,
	                                           String messageOverride, boolean force) {
		checkConfigured();
		if (orderUuids == null || orderUuids.isEmpty()) {
			throw new IllegalArgumentException("orderUuids is required");
		}
		String expectedPatientUuid = StringUtils.trimToNull(patientUuid);
		Patient patient = null;
		List<Order> ordersToNotify = new ArrayList<>();
		Map<String, String> skippedOrders = new LinkedHashMap<>();
		for (String orderUuid : new LinkedHashSet<>(orderUuids)) {
			if (StringUtils.isBlank(orderUuid)) {
				continue;
			}
			Order order = getTestOrder(orderUuid.trim());
			Patient orderPatient = order.getPatient();
			if (orderPatient == null) {
				throw new IllegalStateException("Order has no patient: " + order.getUuid());
			}
			if (expectedPatientUuid != null && !expectedPatientUuid.equals(orderPatient.getUuid())) {
				throw new IllegalArgumentException("Order " + order.getUuid() + " does not belong to patient " + expectedPatientUuid);
			}
			if (patient == null) {
				patient = orderPatient;
			}
			else if (!patient.getUuid().equals(orderPatient.getUuid())) {
				throw new IllegalArgumentException("All orders must belong to the same patient");
			}
			if (!isResultReady(order)) {
				skippedOrders.put(order.getUuid(), "Lab result is not marked ready for notification");
			}
			else if (!force && logRepository.hasActiveNotificationForOrder(order.getOrderId())) {
				skippedOrders.put(order.getUuid(), "A lab notification has already been sent for this order");
			}
			else {
				ordersToNotify.add(order);
			}
		}
		if (patient == null) {
			throw new IllegalArgumentException("orderUuids is required");
		}
		if (ordersToNotify.isEmpty()) {
			throw new IllegalStateException(StringUtils.join(new LinkedHashSet<>(skippedOrders.values()), "; "));
		}

		Order firstOrder = ordersToNotify.get(0);
		String phoneNumber = normalizePhone(StringUtils.defaultIfBlank(phoneNumberOverride, getPatientPhoneNumber(patient)));
		String message = StringUtils.defaultIfBlank(messageOverride, buildDefaultMessage(ordersToNotify, patient));
		message = smsMessageFormatter.appendHospitalName(message, getOrderLocation(firstOrder));
		IntouchSmsResponse smsResponse = sendSms(phoneNumber, message);
		for (Order order : ordersToNotify) {
			logRepository.saveLogEntry(buildLogEntry(order, patient, phoneNumber, message, smsResponse));
		}

		LabNotificationResult result = buildResult(firstOrder, patient, phoneNumber, smsResponse);
		for (Order order : ordersToNotify) {
			result.getNotifiedOrderUuids().add(order.getUuid());
		}
		result.setSkippedOrders(skippedOrders);
		if (smsResponse.isSuccess() && !skippedOrders.isEmpty()) {
			result.setMessage("Lab notification SMS queued for " + ordersToNotify.size() + " order(s); "
					+ skippedOrders.size() + " order(s) skipped");
		}
		return result;
	}

	/**
	 * Sends through the Intouch client, or through the rbcmessaging module (the default), depending on
	 * {@link LabNotificationConfig#getProvider()}. Both are returned in the same form so they are logged the same way.
	 */
	IntouchSmsResponse sendSms(String phoneNumber, String message) {
		if (config.isIntouchProvider()) {
			return smsClient.send(phoneNumber, message);
		}
		IntouchSmsResponse response = new IntouchSmsResponse();
		response.setRequestPayload("rbcmessaging: to=" + phoneNumber);
		try {
			SmsResult result = getRbcMessagingService().sendSms(phoneNumber, message);
			String providerResponse = result == null ? null : result.getProviderMessageId();
			response.setSuccess(true);
			response.setResponseBody((result == null ? "" : result.getProvider() + ": ") + StringUtils.defaultString(providerResponse));
			// FDI Biz returns its whole response body here, which may not fit in the provider_message_id column
			if (providerResponse != null && providerResponse.length() <= MAX_PROVIDER_MESSAGE_ID_LENGTH) {
				response.setProviderMessageId(providerResponse);
			}
		}
		catch (RuntimeException e) {
			log.warn("Unable to send lab notification SMS through rbcmessaging", e);
			response.setSuccess(false);
			response.setErrorMessage(e.getMessage());
		}
		return response;
	}

	private RBCMessagingService getRbcMessagingService() {
		return rbcMessagingService != null ? rbcMessagingService : Context.getService(RBCMessagingService.class);
	}

	/** For tests; otherwise the service is looked up from the Context */
	void setRbcMessagingService(RBCMessagingService rbcMessagingService) {
		this.rbcMessagingService = rbcMessagingService;
	}

	private void checkConfigured() {
		if (!config.isEnabled()) {
			throw new IllegalStateException("Lab notification SMS integration is not enabled");
		}
		if (!config.hasRequiredSettings()) {
			throw new IllegalStateException("Lab notification SMS integration is not fully configured");
		}
	}

	private Order getTestOrder(String orderUuid) {
		Order order = orderService.getOrderByUuid(orderUuid);
		if (order == null) {
			throw new IllegalArgumentException("Order was not found: " + orderUuid);
		}
		if (!(order instanceof TestOrder)) {
			throw new IllegalArgumentException("Order is not a lab test order: " + orderUuid);
		}
		return order;
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

	boolean isResultReady(Order order) {
		if (Order.FulfillerStatus.COMPLETED.equals(order.getFulfillerStatus())) {
			return true;
		}
		if (LabResultValidation.isOn()) {
			return false;  // Results are not released until validated, which completes the order
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

	private String buildDefaultMessage(List<Order> orders, Patient patient) {
		Set<String> orderNames = new LinkedHashSet<>();
		for (Order order : orders) {
			String name = getOrderDisplay(order);
			if (StringUtils.isNotBlank(name)) {
				orderNames.add(name);
			}
		}
		String orderDisplay = StringUtils.join(orderNames, ", ");
		String message = config.getMessageTemplate();
		return message.replace("{facility}", StringUtils.defaultIfBlank(getFacilityName(orders.get(0)), "the health facility"))
				.replace("{patientName}", patient.getPersonName() == null ? "" : patient.getPersonName().getFullName())
				.replace("{orders}", orderDisplay)
				.replace("{order}", orderDisplay);
	}

	private String getFacilityName(Order order) {
		Location location = getOrderLocation(order);
		if (location == null && Context.getUserContext() != null) {
			location = Context.getUserContext().getLocation();
		}
		return location == null ? null : location.getName();
	}

	private Location getOrderLocation(Order order) {
		return order.getEncounter() == null ? null : order.getEncounter().getLocation();
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
