package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.module.mohbilling.model.BillableService;
import org.openmrs.module.mohbilling.model.Department;
import org.openmrs.module.mohbilling.model.FacilityServicePrice;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.mohbilling.model.Insurance;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.model.PatientServiceBill;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.module.rwandaemr.billing.RadiologyAdditionalBillingHandler.ItemsToBill;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RadiologyAdditionalBillingHandlerTest {

	private static final int CD = 4000;
	private static final int CTA_A = 3688;
	private static final int CTA_B = 3689;

	private RadiologyConfig radiologyConfig;
	private BillingService billingService;
	private TestHandler handler;
	private Patient patient;
	private Encounter encounter;
	private Concept ctHead;
	private Concept ctaChest;
	private Concept xrayChest;
	private InsurancePolicy rssbPolicy;
	private InsurancePolicy privatePolicy;
	private GlobalBill rssbGlobalBill;
	private GlobalBill privateGlobalBill;
	private Department laboratory;
	private int nextOrderId = 1;

	@BeforeEach
	public void setUp() {
		radiologyConfig = mock(RadiologyConfig.class);
		billingService = mock(BillingService.class);
		handler = new TestHandler(radiologyConfig);

		patient = new Patient(10);
		encounter = new Encounter(500);
		ctHead = concept(1, "CT head without contrast");
		ctaChest = concept(2, "CTA chest");
		xrayChest = concept(3, "X-ray of the chest");
		Map<Concept, Modality> orderables = new HashMap<Concept, Modality>();
		orderables.put(ctHead, Modality.CT);
		orderables.put(ctaChest, Modality.CT);
		orderables.put(xrayChest, Modality.DX);
		when(radiologyConfig.getOrderables(any())).thenReturn(orderables);
		when(radiologyConfig.isCombineOrdersByModality()).thenReturn(true);

		Insurance rssb = insurance("RSSB");
		Insurance privateInsurance = insurance("PRIVATE");
		rssbPolicy = policy(1, rssb, "RSSB-123");
		privatePolicy = policy(2, privateInsurance, "3220000001");
		when(billingService.getAllInsurancePoliciesByPatient(patient)).thenReturn(Arrays.asList(rssbPolicy, privatePolicy));
		handler.currentPolicy = rssbPolicy;

		rssbGlobalBill = new GlobalBill();
		privateGlobalBill = new GlobalBill();
		when(billingService.getOpenGlobalBillByInsuranceCardNo("RSSB-123")).thenReturn(rssbGlobalBill);
		when(billingService.getOpenGlobalBillByInsuranceCardNo("3220000001")).thenReturn(privateGlobalBill);

		laboratory = mock(Department.class);
		when(laboratory.getName()).thenReturn("LABORATOIRE");
		when(billingService.getAllDepartements()).thenReturn(Collections.singletonList(laboratory));

		facilityService(CD, "CD/DVD", rssb, privateInsurance);
		facilityService(CTA_A, "Contrast", rssb, privateInsurance);
		facilityService(CTA_B, "Injector set", rssb, privateInsurance);
	}

	@Test
	public void shouldBillOneCdPerCtStudyOnThePrivatePolicyAndTheCtaItemsOnTheTestsInsurance() {
		List<ItemsToBill> bills = handler.getItemsToBill(Arrays.asList(order(ctHead), order(ctaChest)));

		assertThat(bills.size(), equalTo(2));
		ItemsToBill cd = bills.get(0);
		assertThat(cd.policy, sameInstance(privatePolicy));
		assertThat(cd.globalBill, sameInstance(privateGlobalBill));
		assertItems(cd, CD, 1);

		ItemsToBill cta = bills.get(1);
		assertThat(cta.policy, sameInstance(rssbPolicy));
		assertThat(cta.globalBill, sameInstance(rssbGlobalBill));
		assertItems(cta, CTA_A, 1, CTA_B, 1);
	}

	@Test
	public void shouldBillTheCtaItemsForEachCtaTest() {
		List<ItemsToBill> bills = handler.getItemsToBill(Arrays.asList(order(ctaChest), order(ctaChest)));

		assertItems(bills.get(1), CTA_A, 2, CTA_B, 2);
	}

	@Test
	public void shouldBillOneCdPerCtTestWhenTestsAreNotCombinedIntoStudies() {
		when(radiologyConfig.isCombineOrdersByModality()).thenReturn(false);

		List<ItemsToBill> bills = handler.getItemsToBill(Arrays.asList(order(ctHead), order(ctHead)));

		assertThat(bills.size(), equalTo(1));
		assertItems(bills.get(0), CD, 2);
	}

	@Test
	public void shouldNotBillTheCdWhenThePatientHasNoPrivatePolicy() {
		when(billingService.getAllInsurancePoliciesByPatient(patient)).thenReturn(Collections.singletonList(rssbPolicy));

		List<ItemsToBill> bills = handler.getItemsToBill(Arrays.asList(order(ctHead), order(ctaChest)));

		assertThat(bills.size(), equalTo(1));
		assertThat(bills.get(0).policy, sameInstance(rssbPolicy));
		assertItems(bills.get(0), CTA_A, 1, CTA_B, 1);
	}

	@Test
	public void shouldNotBillTheCdWhenThePrivatePolicyHasExpiredOrHasNoOpenGlobalBill() {
		when(privatePolicy.getExpirationDate()).thenReturn(new Date(System.currentTimeMillis() - 1000));
		assertThat(handler.getItemsToBill(Collections.<Order>singletonList(order(ctHead))).isEmpty(), is(true));

		when(privatePolicy.getExpirationDate()).thenReturn(null);
		when(billingService.getOpenGlobalBillByInsuranceCardNo("3220000001")).thenReturn(null);
		assertThat(handler.getItemsToBill(Collections.<Order>singletonList(order(ctHead))).isEmpty(), is(true));
	}

	@Test
	public void shouldBillNothingForOtherModalitiesOrWhenNotConfigured() {
		assertThat(handler.getItemsToBill(Collections.<Order>singletonList(order(xrayChest))).isEmpty(), is(true));

		handler.ctMediaService = null;
		handler.ctaServices = Collections.emptyList();
		assertThat(handler.getItemsToBill(Arrays.asList(order(ctHead), order(ctaChest))).isEmpty(), is(true));
	}

	@Test
	public void shouldBillOnTheDepartmentTheBillingModuleUsesForTheOrderType() {
		Department radiology = mock(Department.class);
		when(radiology.getName()).thenReturn("RADIOLOGIE");
		List<Department> departments = Arrays.asList(laboratory, radiology);
		when(billingService.getAllDepartements()).thenReturn(departments);
		OrderType radiologyOrderType = new OrderType(17);
		radiologyOrderType.setUuid("5a3a8d2e-97c3-4797-a6a8-5417e6e699ec");
		radiologyOrderType.setName("Radiology Order");
		TestOrder ct = order(ctHead);
		ct.setOrderType(radiologyOrderType);

		handler.orderTypeDepartments = "5a3a8d2e-97c3-4797-a6a8-5417e6e699ec=RADIOLOGIE";
		assertThat(handler.getItemsToBill(Collections.<Order>singletonList(ct)).get(0).department, sameInstance(radiology));

		// Not configured, or configured to a department that does not exist: the Laboratory, as before
		handler.orderTypeDepartments = null;
		assertThat(handler.getDepartment(ct, billingService), sameInstance(laboratory));
		handler.orderTypeDepartments = "Radiology Order=IMAGERIE";
		assertThat(handler.getDepartment(ct, billingService), sameInstance(laboratory));
	}

	@Test
	public void shouldRecogniseCtaTestsByTheStartOfTheirName() {
		assertThat(RadiologyAdditionalBillingHandler.isCta(concept(20, "CTA chest")), is(true));
		assertThat(RadiologyAdditionalBillingHandler.isCta(concept(21, "cta-abdomen and pelvis")), is(true));
		assertThat(RadiologyAdditionalBillingHandler.isCta(concept(22, "CT abdomen")), is(false));
		assertThat(RadiologyAdditionalBillingHandler.isCta(concept(23, "CTAB something")), is(false));
		assertThat(RadiologyAdditionalBillingHandler.isCta(concept(24, "Head CTA")), is(false));
	}

	private void assertItems(ItemsToBill bill, int... serviceAndQuantity) {
		assertThat(bill.items.size(), equalTo(serviceAndQuantity.length / 2));
		for (int i = 0; i < serviceAndQuantity.length; i += 2) {
			PatientServiceBill item = bill.items.get(i / 2);
			assertThat(item.getService().getServiceId(), equalTo(serviceAndQuantity[i]));
			assertThat(item.getQuantity().intValue(), equalTo(serviceAndQuantity[i + 1]));
		}
	}

	private TestOrder order(Concept concept) {
		TestOrder order = new TestOrder();
		order.setOrderId(nextOrderId++);
		order.setPatient(patient);
		order.setEncounter(encounter);
		order.setConcept(concept);
		return order;
	}

	private Concept concept(int id, String name) {
		Concept concept = new Concept(id);
		concept.setFullySpecifiedName(new ConceptName(name, Locale.ENGLISH));
		return concept;
	}

	private Insurance insurance(String name) {
		Insurance insurance = mock(Insurance.class);
		when(insurance.getName()).thenReturn(name);
		return insurance;
	}

	private InsurancePolicy policy(int id, Insurance insurance, String cardNo) {
		InsurancePolicy policy = mock(InsurancePolicy.class);
		when(policy.getInsurancePolicyId()).thenReturn(id);
		when(policy.getInsurance()).thenReturn(insurance);
		when(policy.getInsuranceCardNo()).thenReturn(cardNo);
		when(policy.getCoverageStartDate()).thenReturn(new Date(System.currentTimeMillis() - 100000));
		return policy;
	}

	private void facilityService(int id, String name, Insurance... insurances) {
		FacilityServicePrice price = mock(FacilityServicePrice.class);
		when(price.getName()).thenReturn(name);
		when(billingService.getFacilityServicePrice(id)).thenReturn(price);
		for (Insurance insurance : insurances) {
			BillableService billable = mock(BillableService.class);
			when(billable.getServiceId()).thenReturn(id);
			when(billable.getMaximaToPay()).thenReturn(new BigDecimal("5000"));
			when(billingService.getBillableServiceByConcept(price, insurance)).thenReturn(billable);
		}
	}

	private class TestHandler extends RadiologyAdditionalBillingHandler {

		Integer ctMediaService = CD;
		List<Integer> ctaServices = Arrays.asList(CTA_A, CTA_B);
		InsurancePolicy currentPolicy;
		String orderTypeDepartments;

		TestHandler(RadiologyConfig radiologyConfig) {
			super(radiologyConfig, null);
		}

		@Override
		BillingService getBillingService() {
			return billingService;
		}

		@Override
		String getPrivateInsuranceName() {
			return "Private";
		}

		@Override
		Integer getCtMediaService() {
			return ctMediaService;
		}

		@Override
		List<Integer> getCtaServices() {
			return ctaServices;
		}

		@Override
		String getOrderTypeDepartments() {
			return orderTypeDepartments;
		}

		@Override
		InsurancePolicy getCurrentPolicy(Patient patient, BillingService billingService) {
			return currentPolicy;
		}
	}
}
