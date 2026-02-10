package org.openmrs.module.rwandaemr.integration.insurance;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component("mmiReceptionStore")
public class MmiReceptionStore {

	private final Map<String, String> receptionNumbers = new ConcurrentHashMap<>();

	public void storeReceptionNumber(Integer userId, Integer patientId, String receptionNumber) {
		if (userId == null || patientId == null || receptionNumber == null) {
			return;
		}
		receptionNumbers.put(getKey(userId, patientId), receptionNumber);
	}

	public String consumeReceptionNumber(Integer userId, Integer patientId) {
		if (userId == null || patientId == null) {
			return null;
		}
		return receptionNumbers.remove(getKey(userId, patientId));
	}

	private String getKey(Integer userId, Integer patientId) {
		return userId + ":" + patientId;
	}
}
