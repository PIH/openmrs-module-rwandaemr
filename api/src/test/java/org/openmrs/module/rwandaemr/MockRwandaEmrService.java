package org.openmrs.module.rwandaemr;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockRwandaEmrService extends RwandaEmrServiceImpl {

	@Setter
	Map<Order, List<Obs>> obsByOrder = new HashMap<>();

	@Getter
	private List<Encounter> encounters;

	public void addObsForOrder(Order order, Obs obs) {
		obsByOrder.computeIfAbsent(order, k -> new ArrayList<>()).add(obs);
	}

	@Override
	public List<Obs> getObsByOrder(Order order) {
		return obsByOrder.getOrDefault(order, new ArrayList<>());
	}

	@Override
	public void saveEncounters(List<Encounter> encounters) {
		this.encounters = encounters;
	}
}
