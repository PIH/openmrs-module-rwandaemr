package org.openmrs.module.rwandaemr.billing;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.mohbilling.GlobalPropertyConfig;
import org.openmrs.module.mohbilling.model.Consommation;
import org.openmrs.module.mohbilling.model.HopService;
import org.openmrs.module.mohbilling.model.PatientServiceBill;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The items of a global bill grouped by service category, in the order of the global bill view: each service of
 * mohbilling.REVENUE on its own, then the grouped categories IMAGING, ACTS, DCPACTS and AUTRES.  The global bill view
 * leaves out items of a service none of these names; here they go to a last group, so every item can be verified.
 * An item is in one group only, the first that names its service.
 */
public class BillItemGroups {

	/** The grouped categories after the revenue services, as the global bill view lists them */
	public static final String[] GROUPED_CATEGORIES = { "mohbilling.IMAGING", "mohbilling.ACTS", "mohbilling.DCPACTS",
			"mohbilling.AUTRES" };

	public static final String OTHER = "OTHER";

	/**
	 * A category: a name and the services in it
	 */
	public static class Category {
		private final String name;
		private final Set<Integer> serviceIds;

		public Category(String name, Collection<Integer> serviceIds) {
			this.name = name;
			this.serviceIds = new HashSet<Integer>(serviceIds);
		}

		public String getName() {
			return name;
		}

		boolean contains(HopService service) {
			return service != null && service.getServiceId() != null && serviceIds.contains(service.getServiceId());
		}
	}

	/**
	 * The items of one category
	 */
	public static class Group {
		private final String name;
		private final List<PatientServiceBill> items = new ArrayList<PatientServiceBill>();
		private BigDecimal total = BigDecimal.ZERO;

		Group(String name) {
			this.name = name;
		}

		void add(PatientServiceBill item) {
			items.add(item);
			if (item.getUnitPrice() != null && item.getQuantity() != null) {
				total = total.add(item.getAmount());
			}
		}

		public String getName() {
			return name;
		}

		public List<PatientServiceBill> getItems() {
			return items;
		}

		public BigDecimal getTotal() {
			return total;
		}
	}

	/**
	 * @return the categories configured in the global properties, in the order of the global bill view
	 */
	public static List<Category> getConfiguredCategories() {
		List<Category> ret = new ArrayList<Category>();
		for (HopService service : GlobalPropertyConfig.getHospitalServiceByCategory("mohbilling.REVENUE")) {
			if (service != null && service.getServiceId() != null) {
				ret.add(new Category(service.getName(), Collections.singletonList(service.getServiceId())));
			}
		}
		for (String property : GROUPED_CATEGORIES) {
			List<Integer> ids = new ArrayList<Integer>();
			for (HopService service : GlobalPropertyConfig.getHospitalServiceByCategory(property)) {
				if (service != null && service.getServiceId() != null) {
					ids.add(service.getServiceId());
				}
			}
			if (!ids.isEmpty()) {
				ret.add(new Category(StringUtils.substringAfter(property, ".").toUpperCase(Locale.ROOT), ids));
			}
		}
		return ret;
	}

	/**
	 * @param consommations the consommations of a global bill
	 * @param categories the categories, in the order to show them
	 * @return the groups that have items, in the order of the categories, then the items of no category
	 */
	public static List<Group> build(Collection<Consommation> consommations, List<Category> categories) {
		Map<String, Group> groups = new LinkedHashMap<String, Group>();
		for (Category category : categories) {
			if (!groups.containsKey(category.getName())) {
				groups.put(category.getName(), new Group(category.getName()));
			}
		}
		Group other = new Group(OTHER);
		if (consommations != null) {
			for (Consommation consommation : consommations) {
				if (consommation == null || Boolean.TRUE.equals(consommation.getVoided()) || consommation.getBillItems() == null) {
					continue;
				}
				for (PatientServiceBill item : consommation.getBillItems()) {
					if (item == null || Boolean.TRUE.equals(item.getVoided())) {
						continue;
					}
					Group group = null;
					for (Category category : categories) {
						if (category.contains(item.getHopService())) {
							group = groups.get(category.getName());
							break;
						}
					}
					(group == null ? other : group).add(item);
				}
			}
		}
		List<Group> ret = new ArrayList<Group>();
		for (Group group : groups.values()) {
			if (!group.getItems().isEmpty()) {
				ret.add(group);
			}
		}
		if (!other.getItems().isEmpty()) {
			ret.add(other);
		}
		return ret;
	}
}
