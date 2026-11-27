package org.openmrs.module.rwandaemr.billing;

import org.junit.jupiter.api.Test;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.HopService;
import org.openmrs.module.mohbilling.model.PatientServiceBill;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class BillItemGroupsTest {

	private static int nextItemId = 1;

	private static HopService service(int id, String name) {
		HopService ret = new HopService();
		ret.setServiceId(id);
		ret.setName(name);
		return ret;
	}

	private static PatientServiceBill item(HopService service, String price, boolean voided) {
		PatientServiceBill ret = new PatientServiceBill();
		ret.setPatientServiceBillId(nextItemId++);
		ret.setHopService(service);
		ret.setUnitPrice(new BigDecimal(price));
		ret.setQuantity(BigDecimal.ONE);
		ret.setVoided(voided);
		return ret;
	}

	private static Consommation consommation(boolean voided, PatientServiceBill... items) {
		Consommation ret = new Consommation();
		Set<PatientServiceBill> set = new LinkedHashSet<PatientServiceBill>(Arrays.asList(items));
		ret.setBillItems(set);
		ret.setVoided(voided);
		return ret;
	}

	private static List<String> names(List<BillItemGroups.Group> groups) {
		String[] ret = new String[groups.size()];
		for (int i = 0; i < groups.size(); i++) {
			ret[i] = groups.get(i).getName();
		}
		return Arrays.asList(ret);
	}

	@Test
	public void shouldGroupItemsInTheOrderOfTheCategories() {
		HopService pharmacy = service(1, "PHARMACY");
		HopService lab = service(2, "LABORATORY");
		HopService xray = service(3, "RADIOLOGY");
		HopService unknown = service(9, "UNLISTED");
		List<BillItemGroups.Category> categories = Arrays.asList(
				new BillItemGroups.Category("LABORATORY", Collections.singletonList(2)),
				new BillItemGroups.Category("PHARMACY", Collections.singletonList(1)),
				new BillItemGroups.Category("IMAGING", Arrays.asList(3, 4)));

		List<BillItemGroups.Group> groups = BillItemGroups.build(Arrays.asList(
				consommation(false, item(pharmacy, "100", false), item(lab, "50", false), item(unknown, "5", false)),
				consommation(false, item(lab, "25", false), item(xray, "300", false), item(lab, "999", true)),
				consommation(true, item(pharmacy, "777", false))), categories);

		assertThat(names(groups), contains("LABORATORY", "PHARMACY", "IMAGING", BillItemGroups.OTHER));
		assertThat(groups.get(0).getItems(), hasSize(2));
		assertThat(groups.get(0).getTotal().compareTo(new BigDecimal("75")), equalTo(0));
		assertThat(groups.get(1).getTotal().compareTo(new BigDecimal("100")), equalTo(0));
		assertThat(groups.get(2).getTotal().compareTo(new BigDecimal("300")), equalTo(0));
		assertThat(groups.get(3).getItems(), hasSize(1));
	}

	@Test
	public void shouldPutAnItemInTheFirstCategoryOnly() {
		HopService lab = service(2, "LABORATORY");
		List<BillItemGroups.Category> categories = Arrays.asList(
				new BillItemGroups.Category("LABORATORY", Collections.singletonList(2)),
				new BillItemGroups.Category("AUTRES", Arrays.asList(2, 5)));

		List<BillItemGroups.Group> groups = BillItemGroups.build(
				Collections.singletonList(consommation(false, item(lab, "10", false))), categories);

		assertThat(names(groups), contains("LABORATORY"));
	}

	@Test
	public void shouldPutEveryItemInOtherWithoutCategories() {
		List<BillItemGroups.Group> groups = BillItemGroups.build(Collections.singletonList(
				consommation(false, item(null, "10", false), item(service(1, "X"), "20", false))),
				Collections.<BillItemGroups.Category>emptyList());

		assertThat(names(groups), contains(BillItemGroups.OTHER));
		assertThat(groups.get(0).getItems(), hasSize(2));
	}
}
