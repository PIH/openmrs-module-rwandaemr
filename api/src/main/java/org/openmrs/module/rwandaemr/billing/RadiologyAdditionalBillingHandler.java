package org.openmrs.module.rwandaemr.billing;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.annotation.Handler;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.automation.MohBillingHandler;
import org.openmrs.module.mohbilling.businesslogic.InsuranceBillUtil;
import org.openmrs.module.mohbilling.businesslogic.PatientBillUtil;
import org.openmrs.module.mohbilling.businesslogic.ThirdPartyBillUtil;
import org.openmrs.module.mohbilling.model.BillableService;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.Department;
import org.openmrs.module.mohbilling.model.FacilityServicePrice;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.model.PatientServiceBill;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.mohbilling.utils.Utils;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bills the items that go with some radiology tests, in addition to the test itself, which the billing module bills:
 * <ul>
 *     <li>a CD/DVD for each CT study, on the patient's private insurance policy, if the patient has one</li>
 *     <li>extra facility services for each CTA test, on the same insurance as the test</li>
 * </ul>
 * The items are billed when the orders are saved, in the same transaction, the way the billing module bills orders.
 * Each part is only done when its facility services are configured.  Cancelling an order does not remove the items;
 * that is done by hand, as for the test itself.
 */
@Handler(supports = Order.class)
@Component
public class RadiologyAdditionalBillingHandler implements MohBillingHandler<Order> {

	/**
	 * The facility service price id of the CD/DVD billed for each CT study
	 */
	public static final String GP_CT_MEDIA_SERVICE = "rwandaemr.radiology.billing.ctMediaFacilityServicePriceId";

	/**
	 * The facility service price ids, separated by commas, billed once each for each CTA test
	 */
	public static final String GP_CTA_SERVICES = "rwandaemr.radiology.billing.ctaFacilityServicePriceIds";

	/**
	 * The name of the patient's private insurance, which is also the insurance created automatically for patients
	 */
	public static final String GP_PRIVATE_INSURANCE = "rwandaemr.autoCreateInsuranceType";

	/**
	 * The billing module's setting of the department orders of each order type are billed on
	 */
	public static final String GP_ORDER_TYPE_DEPARTMENTS = "billing.orderTypeDepartments";

	/**
	 * CTA tests are the CT tests whose name starts with CTA
	 */
	static final Pattern CTA_NAME = Pattern.compile("^CTA\\b", Pattern.CASE_INSENSITIVE);

	private final Log log = LogFactory.getLog(getClass());

	private final ThreadLocal<OrdersInTransaction> threadLocalData = new ThreadLocal<OrdersInTransaction>();

	private final RadiologyConfig radiologyConfig;
	private final ConceptService conceptService;

	public RadiologyAdditionalBillingHandler(@Autowired RadiologyConfig radiologyConfig,
											 @Autowired ConceptService conceptService) {
		this.radiologyConfig = radiologyConfig;
		this.conceptService = conceptService;
	}

	/**
	 * Orders are collected during the whole transaction, including the nested transactions started while saving orders
	 */
	@Override
	public void afterTransactionBegin() {
		if (threadLocalData.get() == null) {
			threadLocalData.set(new OrdersInTransaction());
		}
		threadLocalData.get().transactions++;
	}

	@Override
	public void handleCreatedEntity(Order order) {
		OrdersInTransaction data = threadLocalData.get();
		if (data != null && order instanceof TestOrder && order.getAction() != Order.Action.DISCONTINUE) {
			data.orders.add(order);
		}
	}

	/**
	 * When the outermost transaction is about to complete, all the orders saved in it are known, so the items are billed
	 */
	@Override
	public void beforeTransactionCompletion() {
		OrdersInTransaction data = threadLocalData.get();
		if (data == null || data.orders.isEmpty() || data.transactions != 1) {
			return;
		}
		List<ItemsToBill> bills;
		try {
			bills = getItemsToBill(new ArrayList<Order>(data.orders));
		}
		catch (RuntimeException e) {
			// Working out what to bill only reads data, so a problem there is logged and the orders are saved anyway
			log.error("Unable to work out the additional radiology items to bill", e);
			return;
		}
		data.orders.clear();
		for (ItemsToBill bill : bills) {
			saveBill(bill);
		}
	}

	@Override
	public void afterTransactionCompletion(int status) {
		OrdersInTransaction data = threadLocalData.get();
		if (data != null) {
			data.transactions--;
			if (data.transactions <= 0) {
				threadLocalData.remove();
			}
		}
	}

	/**
	 * @return the additional items to bill for the given new orders, grouped into one bill per insurance policy
	 */
	List<ItemsToBill> getItemsToBill(List<Order> orders) {
		List<ItemsToBill> ret = new ArrayList<ItemsToBill>();
		Integer ctMediaService = getCtMediaService();
		List<Integer> ctaServices = getCtaServices();
		if (ctMediaService == null && ctaServices.isEmpty()) {
			return ret;
		}
		Map<Concept, Modality> orderables = radiologyConfig.getOrderables(radiologyConfig.getModalityConceptSets());
		List<Order> ctOrders = new ArrayList<Order>();
		for (Order order : orders) {
			if (orderables.get(order.getConcept()) == Modality.CT) {
				ctOrders.add(order);
			}
		}
		if (ctOrders.isEmpty()) {
			return ret;
		}
		Patient patient = ctOrders.get(0).getPatient();
		for (Order order : ctOrders) {
			if (!patient.equals(order.getPatient())) {
				log.warn("CT orders for several patients saved together; not billing their additional items");
				return ret;
			}
		}
		BillingService billingService = getBillingService();

		if (ctMediaService != null) {
			int studies = countCtStudies(ctOrders, radiologyConfig.isCombineOrdersByModality());
			InsurancePolicy privatePolicy = getPrivatePolicy(patient, billingService);
			if (privatePolicy == null) {
				log.info("Patient " + patient.getPatientId() + " has no private insurance policy; not billing the CD/DVD for "
						+ studies + " CT study(ies)");
			}
			else {
				addBill(ret, privatePolicy, ctOrders.get(0), ctMediaService, studies, billingService, "CD/DVD");
			}
		}

		if (!ctaServices.isEmpty()) {
			int ctaTests = 0;
			for (Order order : ctOrders) {
				if (isCta(order.getConcept())) {
					ctaTests++;
				}
			}
			if (ctaTests > 0) {
				InsurancePolicy policy = getCurrentPolicy(patient, billingService);
				if (policy == null) {
					log.info("Patient " + patient.getPatientId() + " has no current insurance policy; not billing the CTA items");
				}
				else {
					for (Integer service : ctaServices) {
						addBill(ret, policy, ctOrders.get(0), service, ctaTests, billingService, "CTA");
					}
				}
			}
		}
		return ret;
	}

	/**
	 * Tests of the same modality ordered in the same encounter are sent to PACS as one study, when combining is on
	 */
	static int countCtStudies(List<Order> ctOrders, boolean combineByModality) {
		if (!combineByModality) {
			return ctOrders.size();
		}
		Set<Object> studies = new LinkedHashSet<Object>();
		for (Order order : ctOrders) {
			Encounter encounter = order.getEncounter();
			studies.add(encounter == null ? order : encounter);
		}
		return studies.size();
	}

	/**
	 * @return true if any name of the test starts with CTA
	 */
	static boolean isCta(Concept concept) {
		if (concept == null) {
			return false;
		}
		for (ConceptName name : concept.getNames()) {
			if (!name.getVoided() && name.getName() != null && CTA_NAME.matcher(name.getName().trim()).find()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds the item to the bill for its policy, if the policy has an open global bill and the item is priced for its
	 * insurance; otherwise the item is not billed and the reason is logged
	 */
	private void addBill(List<ItemsToBill> bills, InsurancePolicy policy, Order order, Integer facilityServicePriceId,
						 int quantity, BillingService billingService, String what) {
		String cardNo = policy.getInsuranceCardNo();
		FacilityServicePrice facilityService = billingService.getFacilityServicePrice(facilityServicePriceId);
		if (facilityService == null || facilityService.isHidden()) {
			log.warn("Facility service " + facilityServicePriceId + " for the " + what + " item is not found or hidden; not billing it");
			return;
		}
		BillableService billableService = billingService.getBillableServiceByConcept(facilityService, policy.getInsurance());
		if (billableService == null || billableService.getMaximaToPay() == null) {
			log.warn("Facility service " + facilityService.getName() + " for the " + what + " item has no price on "
					+ policy.getInsurance().getName() + " insurance; not billing it");
			return;
		}
		ItemsToBill bill = null;
		for (ItemsToBill b : bills) {
			if (b.policy == policy || (b.policy.getInsurancePolicyId() != null
					&& b.policy.getInsurancePolicyId().equals(policy.getInsurancePolicyId()))) {
				bill = b;
			}
		}
		if (bill == null) {
			GlobalBill globalBill = billingService.getOpenGlobalBillByInsuranceCardNo(cardNo);
			if (globalBill == null) {
				log.info("No open global bill on insurance card " + cardNo + "; not billing the " + what + " item");
				return;
			}
			Department department = getDepartment(order, billingService);
			if (department == null) {
				log.warn("No department found to bill the " + what + " item on");
				return;
			}
			bill = new ItemsToBill(policy, globalBill, department, order.getPatient());
			bills.add(bill);
		}
		PatientServiceBill item = new PatientServiceBill();
		item.setService(billableService);
		item.setUnitPrice(billableService.getMaximaToPay());
		item.setQuantity(new BigDecimal(quantity));
		item.setHopService(billingService.getHopService(facilityService.getCategory()));
		item.setItemType(1);
		bill.items.add(item);
		log.info("Billing " + quantity + " x " + facilityService.getName() + " (" + what + ") on insurance card " + cardNo);
	}

	/**
	 * Saves the items as one consommation on the policy's global bill, the way the billing module bills orders
	 */
	private void saveBill(ItemsToBill bill) {
		BillingService billingService = getBillingService();
		Date now = new Date();
		BigDecimal amount = BigDecimal.ZERO;
		for (PatientServiceBill item : bill.items) {
			amount = amount.add(item.getQuantity().multiply(item.getUnitPrice()));
		}
		Consommation consommation = new Consommation();
		consommation.setDepartment(bill.department);
		consommation.setBeneficiary(billingService.getBeneficiaryByPolicyNumber(bill.policy.getInsuranceCardNo()));
		consommation.setGlobalBill(bill.globalBill);
		consommation.setCreatedDate(now);
		consommation.setCreator(Context.getAuthenticatedUser());
		consommation.setPatientBill(PatientBillUtil.createPatientBill(amount, bill.policy));
		consommation.setThirdPartyBill(ThirdPartyBillUtil.createThirdPartyBill(bill.policy, amount));
		consommation.setInsuranceBill(InsuranceBillUtil.createInsuranceBill(bill.policy.getInsurance(), amount));
		for (PatientServiceBill item : bill.items) {
			item.setServiceDate(now);
			item.setCreatedDate(now);
			item.setCreator(Context.getAuthenticatedUser());
			item.setConsommation(consommation);
			consommation.addBillItem(item);
		}
		billingService.saveConsommation(consommation);
		GlobalBill globalBill = bill.globalBill;
		BigDecimal globalAmount = globalBill.getGlobalAmount() == null ? BigDecimal.ZERO : globalBill.getGlobalAmount();
		globalBill.setGlobalAmount(globalAmount.add(amount));
		billingService.saveGlobalBill(globalBill);
	}

	/**
	 * @return the patient's active policy on the private insurance, or null if there is none
	 */
	InsurancePolicy getPrivatePolicy(Patient patient, BillingService billingService) {
		String insuranceName = getPrivateInsuranceName();
		if (StringUtils.isBlank(insuranceName)) {
			log.warn(GP_PRIVATE_INSURANCE + " is not set, so the private insurance is not known; not billing the CD/DVD");
			return null;
		}
		Date now = new Date();
		for (InsurancePolicy policy : billingService.getAllInsurancePoliciesByPatient(patient)) {
			if (policy.isRetired() || policy.getInsurance() == null
					|| !insuranceName.trim().equalsIgnoreCase(StringUtils.trim(policy.getInsurance().getName()))) {
				continue;
			}
			boolean started = policy.getCoverageStartDate() == null || !policy.getCoverageStartDate().after(now);
			boolean expired = policy.getExpirationDate() != null && policy.getExpirationDate().before(now);
			if (started && !expired && StringUtils.isNotBlank(policy.getInsuranceCardNo())) {
				return policy;
			}
		}
		return null;
	}

	/**
	 * @return the policy the billing module bills the patient's orders on: that of the latest insurance number recorded
	 */
	InsurancePolicy getCurrentPolicy(Patient patient, BillingService billingService) {
		String conceptRef = ConfigUtil.getGlobalProperty("registration.insuranceNumberConcept");
		Concept insuranceNumberConcept = StringUtils.isBlank(conceptRef) ? null : getConcept(conceptRef);
		if (insuranceNumberConcept == null) {
			return null;
		}
		List<Obs> latest = Utils.getLastNObservations(1, patient, insuranceNumberConcept, false);
		String cardNo = latest.isEmpty() ? null : latest.get(0).getValueText();
		return StringUtils.isBlank(cardNo) ? null : billingService.getInsurancePolicyByCardNo(cardNo);
	}

	/**
	 * The items are billed on the same department as the test: the one the billing module's order type setting maps
	 * the order's type to, or otherwise the Laboratory department, as the billing module does for test orders
	 */
	Department getDepartment(Order order, BillingService billingService) {
		String departmentName = getDepartmentNameForOrderType(order.getOrderType(), getOrderTypeDepartments());
		Department laboratory = null;
		for (Department department : billingService.getAllDepartements()) {
			String name = StringUtils.trimToEmpty(department.getName());
			if (departmentName != null && name.equalsIgnoreCase(departmentName)) {
				return department;
			}
			if (laboratory == null && ("LABORATOIRE".equalsIgnoreCase(name) || "LABORATORY".equalsIgnoreCase(name))) {
				laboratory = department;
			}
		}
		return laboratory;
	}

	/**
	 * @return the department the configuration maps the order type to, read the same way as the billing module reads
	 * its {@link #GP_ORDER_TYPE_DEPARTMENTS} setting, or null if it maps it to none
	 */
	static String getDepartmentNameForOrderType(OrderType orderType, String configuration) {
		if (orderType == null || StringUtils.isBlank(configuration)) {
			return null;
		}
		for (String mapping : configuration.split(",")) {
			int separator = mapping.indexOf('=');
			if (separator <= 0) {
				continue;
			}
			String orderTypeRef = mapping.substring(0, separator).trim();
			String departmentName = mapping.substring(separator + 1).trim();
			boolean matches = orderTypeRef.equalsIgnoreCase(orderType.getUuid())
					|| (orderType.getOrderTypeId() != null && orderTypeRef.equals(orderType.getOrderTypeId().toString()))
					|| orderTypeRef.equalsIgnoreCase(StringUtils.trimToEmpty(orderType.getName()));
			if (matches && StringUtils.isNotBlank(departmentName)) {
				return departmentName;
			}
		}
		return null;
	}

	String getOrderTypeDepartments() {
		return ConfigUtil.getGlobalProperty(GP_ORDER_TYPE_DEPARTMENTS);
	}

	String getPrivateInsuranceName() {
		return ConfigUtil.getProperty(GP_PRIVATE_INSURANCE);
	}

	Integer getCtMediaService() {
		List<Integer> ids = parseIds(ConfigUtil.getGlobalProperty(GP_CT_MEDIA_SERVICE));
		return ids.isEmpty() ? null : ids.get(0);
	}

	List<Integer> getCtaServices() {
		return parseIds(ConfigUtil.getGlobalProperty(GP_CTA_SERVICES));
	}

	List<Integer> parseIds(String value) {
		List<Integer> ret = new ArrayList<Integer>();
		if (StringUtils.isNotBlank(value)) {
			for (String id : value.split(",")) {
				if (StringUtils.isNotBlank(id)) {
					try {
						ret.add(Integer.valueOf(id.trim()));
					}
					catch (NumberFormatException e) {
						log.warn("Invalid facility service price id '" + id + "' in radiology billing settings");
					}
				}
			}
		}
		return ret;
	}

	private Concept getConcept(String ref) {
		Concept concept = conceptService.getConceptByUuid(ref.trim());
		return concept != null ? concept : conceptService.getConcept(ref.trim());
	}

	BillingService getBillingService() {
		return Context.getService(BillingService.class);
	}

	/**
	 * The orders saved during a transaction, and how many transactions are open, including nested ones
	 */
	static class OrdersInTransaction {
		int transactions = 0;
		final Set<Order> orders = new LinkedHashSet<Order>();
	}

	/**
	 * Items to bill together on one insurance policy
	 */
	static class ItemsToBill {

		final InsurancePolicy policy;
		final GlobalBill globalBill;
		final Department department;
		final Patient patient;
		final List<PatientServiceBill> items = new ArrayList<PatientServiceBill>();

		ItemsToBill(InsurancePolicy policy, GlobalBill globalBill, Department department, Patient patient) {
			this.policy = policy;
			this.globalBill = globalBill;
			this.department = department;
			this.patient = patient;
		}
	}
}
