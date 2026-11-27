package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.businesslogic.ConsommationUtil;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.PatientServiceBill;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerification;
import org.openmrs.module.rwandaemr.billing.BillJourneyScope;
import org.openmrs.module.rwandaemr.billing.verification.BillVerificationService;
import org.openmrs.module.rwandaemr.billing.verification.BillItemVerificationStatus;
import org.openmrs.module.rwandaemr.billing.verification.LabTestProgress;
import org.openmrs.module.rwandaemr.billing.verification.LabTestProgressLookup;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An encounter of the patient journey of a global bill, read-only, for the RHIP voucher submission page: what was
 * recorded, what was ordered, and the bill items made from those orders.  Whoever can see the bill can see its
 * encounters, including an insurer's verifier who cannot otherwise see patient records.
 */
@Controller
public class BillVerificationEncounterRestController {

	private final Log log = LogFactory.getLog(getClass());

	private static final List<String> VIEW_PRIVILEGES = Arrays.asList(
			"Billing RHIP Voucher - View History",
			"Billing Configuration - View Billing Admin",
			BillVerificationService.PRIVILEGE_FACILITY,
			BillVerificationService.PRIVILEGE_INSURER);

	private static final String[] READ_PRIVILEGES = {
			PrivilegeConstants.GET_ENCOUNTERS, PrivilegeConstants.GET_OBS, PrivilegeConstants.GET_ORDERS,
			PrivilegeConstants.GET_CONCEPTS, PrivilegeConstants.GET_PATIENTS, PrivilegeConstants.GET_PROVIDERS,
			PrivilegeConstants.GET_LOCATIONS, PrivilegeConstants.GET_FORMS, PrivilegeConstants.GET_ENCOUNTER_TYPES,
			PrivilegeConstants.GET_USERS };

	@Autowired
	BillVerificationService verificationService;

	@Autowired
	LabTestProgressLookup labTestProgressLookup;

	@RequestMapping(value = "/rest/v1/rwandaemr/billVerification/encounter", method = RequestMethod.GET)
	@ResponseBody
	public Object getEncounter(@RequestParam("globalBillId") Integer globalBillId,
							   @RequestParam("encounterId") Integer encounterId) {
		if (!hasAny(VIEW_PRIVILEGES)) {
			throw new APIAuthenticationException("You do not have permission to view the patient journey.");
		}
		GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
		if (globalBill == null) {
			throw new IllegalArgumentException("Global bill " + globalBillId + " not found");
		}
		if (!verificationService.canSeeBillsOf(globalBill.getInsurance() == null ? null : globalBill.getInsurance().getInsuranceId())) {
			throw new APIAuthenticationException("You can't see the bills of this insurance.");
		}
		try {
			for (String privilege : READ_PRIVILEGES) {
				Context.addProxyPrivilege(privilege);
			}
			Encounter encounter = Context.getEncounterService().getEncounter(encounterId);
			if (encounter == null || !isInJourney(encounter, globalBill)) {
				throw new IllegalArgumentException("This encounter is not part of the patient journey of the bill.");
			}
			return toEncounter(encounter, globalBill);
		}
		finally {
			for (String privilege : READ_PRIVILEGES) {
				Context.removeProxyPrivilege(privilege);
			}
		}
	}

	/**
	 * @return true if the encounter is of the patient of the bill, and belongs to the visit of the bill: the same
	 * encounters as the patient journey of the page
	 */
	private boolean isInJourney(Encounter encounter, GlobalBill globalBill) {
		Patient patient = globalBill.getAdmission() == null || globalBill.getAdmission().getInsurancePolicy() == null ? null
				: globalBill.getAdmission().getInsurancePolicy().getOwner();
		if (patient == null || encounter.getPatient() == null || !patient.getPatientId().equals(encounter.getPatient().getPatientId())) {
			return false;
		}
		return new BillJourneyScope(globalBill).contains(encounter);
	}

	private SimpleObject toEncounter(Encounter encounter, GlobalBill globalBill) {
		Locale locale = Context.getLocale();
		SimpleObject ret = new SimpleObject();
		ret.put("encounterId", encounter.getEncounterId());
		ret.put("encounterType", encounter.getEncounterType() == null ? "" : encounter.getEncounterType().getName());
		ret.put("form", encounter.getForm() == null ? "" : encounter.getForm().getName());
		ret.put("location", encounter.getLocation() == null ? "" : encounter.getLocation().getName());
		ret.put("encounterDatetime", encounter.getEncounterDatetime());
		ret.put("creator", encounter.getCreator() == null ? "" : encounter.getCreator().getUsername());
		ret.put("voided", Boolean.TRUE.equals(encounter.getVoided()));
		List<String> providers = new ArrayList<String>();
		for (EncounterProvider encounterProvider : encounter.getActiveEncounterProviders()) {
			if (encounterProvider.getProvider() != null && StringUtils.isNotBlank(encounterProvider.getProvider().getName())) {
				providers.add(encounterProvider.getProvider().getName());
			}
		}
		ret.put("providers", providers);

		// Lab tests: the tests ordered here, and the tests whose results were entered here
		Map<Integer, Order> labOrders = new LinkedHashMap<Integer, Order>();
		List<SimpleObject> orders = new ArrayList<SimpleObject>();
		Set<Integer> orderIds = new HashSet<Integer>();
		for (Order order : encounter.getOrders()) {
			if (Boolean.TRUE.equals(order.getVoided())) {
				continue;
			}
			orderIds.add(order.getOrderId());
			if (labTestProgressLookup.isLabTest(order)) {
				labOrders.put(order.getOrderId(), order);
			}
			else {
				orders.add(toOrder(order, locale));
			}
		}
		for (Obs o : encounter.getAllObs(false)) {
			Order order = o.getOrder();
			if (order != null && !labOrders.containsKey(order.getOrderId()) && labTestProgressLookup.isLabTest(order)) {
				labOrders.put(order.getOrderId(), order);
				orderIds.add(order.getOrderId());
			}
		}
		boolean labEncounter = !labOrders.isEmpty();
		ret.put("labEncounter", labEncounter);

		// The results of lab tests are not shown to whoever verifies the bill, so a lab encounter sends no observations
		List<SimpleObject> obs = new ArrayList<SimpleObject>();
		if (!labEncounter) {
			for (Obs o : encounter.getObsAtTopLevel(false)) {
				obs.add(toObs(o, locale));
			}
		}
		ret.put("obs", obs);
		ret.put("orders", orders);
		ret.put("labTests", toLabTests(labTestProgressLookup.getProgress(labOrders.values())));
		ret.put("billItems", getBillItems(globalBill, orderIds));
		ret.put("verified", verificationService.appliesToBill(globalBill.getGlobalBillId()));
		return ret;
	}

	private List<SimpleObject> toLabTests(List<LabTestProgress> tests) {
		List<SimpleObject> ret = new ArrayList<SimpleObject>();
		for (LabTestProgress test : tests) {
			SimpleObject o = new SimpleObject();
			o.put("testName", test.getTestName());
			o.put("orderNumber", StringUtils.defaultString(test.getOrderNumber()));
			o.put("urgency", StringUtils.defaultString(test.getUrgency()));
			o.put("orderedAt", test.getOrderedAt());
			o.put("orderedBy", StringUtils.defaultString(test.getOrderedBy()));
			o.put("specimenRecordedAt", test.getSpecimenRecordedAt());
			o.put("specimenRecordedBy", StringUtils.defaultString(test.getSpecimenRecordedBy()));
			o.put("resultsEnteredAt", test.getResultsEnteredAt());
			o.put("resultsEnteredBy", StringUtils.defaultString(test.getResultsEnteredBy()));
			o.put("status", test.getStatus().name());
			o.put("statusLabel", test.getStatus().getLabel());
			o.put("notPerformedReason", StringUtils.defaultString(test.getNotPerformedReason()));
			ret.add(o);
		}
		return ret;
	}

	private SimpleObject toObs(Obs obs, Locale locale) {
		SimpleObject ret = new SimpleObject();
		Concept concept = obs.getConcept();
		ret.put("concept", conceptName(concept, locale));
		if (obs.isObsGrouping()) {
			List<SimpleObject> members = new ArrayList<SimpleObject>();
			for (Obs member : obs.getGroupMembers(false)) {
				members.add(toObs(member, locale));
			}
			ret.put("members", members);
			return ret;
		}
		String value = obs.getValueCoded() != null ? conceptName(obs.getValueCoded(), locale) : obs.getValueAsString(locale);
		ret.put("value", value);
		if (StringUtils.isNotBlank(obs.getComment())) {
			ret.put("comment", obs.getComment());
		}
		if (concept instanceof ConceptNumeric) {
			ConceptNumeric numeric = (ConceptNumeric) concept;
			ret.put("units", StringUtils.defaultString(numeric.getUnits()));
			ret.put("range", range(numeric.getLowNormal(), numeric.getHiNormal()));
			if (obs.getValueNumeric() != null) {
				Double v = obs.getValueNumeric();
				boolean high = numeric.getHiNormal() != null && v > numeric.getHiNormal();
				boolean low = numeric.getLowNormal() != null && v < numeric.getLowNormal();
				ret.put("flag", high ? "HIGH" : low ? "LOW" : "");
			}
		}
		return ret;
	}

	private SimpleObject toOrder(Order order, Locale locale) {
		SimpleObject ret = new SimpleObject();
		ret.put("orderType", order.getOrderType() == null ? "" : order.getOrderType().getName());
		ret.put("dateActivated", order.getDateActivated());
		ret.put("action", order.getAction() == null ? "" : order.getAction().name());
		ret.put("fulfillerStatus", order.getFulfillerStatus() == null ? "" : order.getFulfillerStatus().name());
		ret.put("active", order.isActive());
		ret.put("dateStopped", order.getDateStopped());
		if (order instanceof DrugOrder) {
			DrugOrder drugOrder = (DrugOrder) order;
			ret.put("name", drugOrder.getDrug() != null ? drugOrder.getDrug().getName() : conceptName(order.getConcept(), locale));
			ret.put("dose", join(number(drugOrder.getDose()), drugOrder.getDoseUnits() == null ? null : conceptName(drugOrder.getDoseUnits(), locale)));
			ret.put("route", drugOrder.getRoute() == null ? "" : conceptName(drugOrder.getRoute(), locale));
			ret.put("frequency", drugOrder.getFrequency() == null ? "" : conceptName(drugOrder.getFrequency().getConcept(), locale));
			ret.put("duration", join(drugOrder.getDuration() == null ? null : drugOrder.getDuration().toString(),
					drugOrder.getDurationUnits() == null ? null : conceptName(drugOrder.getDurationUnits(), locale)));
			ret.put("quantity", join(number(drugOrder.getQuantity()),
					drugOrder.getQuantityUnits() == null ? null : conceptName(drugOrder.getQuantityUnits(), locale)));
			ret.put("instructions", StringUtils.defaultString(StringUtils.defaultIfBlank(drugOrder.getDosingInstructions(), drugOrder.getInstructions())));
		}
		else {
			ret.put("name", conceptName(order.getConcept(), locale));
			ret.put("instructions", StringUtils.defaultString(order.getInstructions()));
		}
		return ret;
	}

	/**
	 * @return the items of the bill made from the given orders, with their verification status
	 */
	private List<SimpleObject> getBillItems(GlobalBill globalBill, Set<Integer> orderIds) {
		List<SimpleObject> ret = new ArrayList<SimpleObject>();
		if (orderIds.isEmpty()) {
			return ret;
		}
		Map<Integer, BillItemVerification> verifications = verificationService.getItems(Collections.singletonList(globalBill.getGlobalBillId()));
		List<Consommation> consommations = ConsommationUtil.getConsommationsByGlobalBill(globalBill);
		for (Consommation consommation : consommations == null ? Collections.<Consommation>emptyList() : consommations) {
			if (consommation == null || Boolean.TRUE.equals(consommation.getVoided()) || consommation.getBillItems() == null) {
				continue;
			}
			for (PatientServiceBill item : consommation.getBillItems()) {
				if (item == null || Boolean.TRUE.equals(item.getVoided()) || !orderIds.contains(item.getOrderId())) {
					continue;
				}
				BillItemVerification verification = verifications.get(item.getPatientServiceBillId());
				BillItemVerificationStatus status = verification == null ? BillItemVerificationStatus.PENDING : verification.getStatus();
				SimpleObject o = new SimpleObject();
				o.put("patientServiceBillId", item.getPatientServiceBillId());
				o.put("name", itemName(item));
				o.put("quantity", item.getQuantity());
				o.put("unitPrice", item.getUnitPrice());
				o.put("amount", item.getAmount());
				o.put("status", status.name());
				o.put("statusLabel", status.getLabel());
				o.put("reason", verification == null ? "" : StringUtils.defaultString(verification.getReasonName()));
				ret.add(o);
			}
		}
		return ret;
	}

	static String itemName(PatientServiceBill item) {
		if (item.getService() != null && item.getService().getFacilityServicePrice() != null
				&& StringUtils.isNotBlank(item.getService().getFacilityServicePrice().getName())) {
			return item.getService().getFacilityServicePrice().getName();
		}
		if (StringUtils.isNotBlank(item.getServiceOther())) {
			return item.getServiceOther();
		}
		if (StringUtils.isNotBlank(item.getServiceOtherDescription())) {
			return item.getServiceOtherDescription();
		}
		return item.getHopService() == null ? "" : item.getHopService().getName();
	}

	private String conceptName(Concept concept, Locale locale) {
		if (concept == null) {
			return "";
		}
		return concept.getName(locale) == null ? concept.getDisplayString() : concept.getName(locale).getName();
	}

	private String range(Double low, Double high) {
		if (low == null && high == null) {
			return "";
		}
		if (low == null) {
			return "≤ " + number(high);
		}
		if (high == null) {
			return "≥ " + number(low);
		}
		return number(low) + " – " + number(high);
	}

	private String number(Double value) {
		if (value == null) {
			return null;
		}
		return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
	}

	private String join(String first, String second) {
		return StringUtils.trimToEmpty(StringUtils.defaultString(first) + " " + StringUtils.defaultString(second));
	}

	private boolean hasAny(List<String> privileges) {
		for (String privilege : privileges) {
			if (Context.hasPrivilege(privilege)) {
				return true;
			}
		}
		return false;
	}

	@ExceptionHandler(APIAuthenticationException.class)
	@ResponseBody
	public ResponseEntity<?> handleAuthenticationException(APIAuthenticationException e) {
		log.warn("Encounter of the patient journey refused: " + e.getMessage());
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
	}

	@ExceptionHandler({ APIException.class, IllegalArgumentException.class })
	@ResponseBody
	public ResponseEntity<?> handleBadRequest(RuntimeException e) {
		log.warn("Encounter of the patient journey not shown: " + e.getMessage());
		return ResponseEntity.badRequest().body(error(e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	@ResponseBody
	public ResponseEntity<?> handleError(Exception e) {
		log.error("Unable to show an encounter of the patient journey", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(error("The encounter could not be loaded: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName())));
	}

	private Map<String, Object> error(String message) {
		Map<String, Object> ret = new LinkedHashMap<String, Object>();
		ret.put("success", false);
		ret.put("message", message);
		return ret;
	}
}
