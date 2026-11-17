package org.openmrs.module.rwandaemr.sms;

import java.util.List;

public class SmsLogSearchResult {

	private List<SmsLogEntry> logs;
	private int totalCount;

	public SmsLogSearchResult(List<SmsLogEntry> logs, int totalCount) {
		this.logs = logs;
		this.totalCount = totalCount;
	}

	public List<SmsLogEntry> getLogs() {
		return logs;
	}

	public int getTotalCount() {
		return totalCount;
	}
}
