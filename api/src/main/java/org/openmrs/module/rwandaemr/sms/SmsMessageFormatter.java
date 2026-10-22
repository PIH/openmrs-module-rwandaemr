package org.openmrs.module.rwandaemr.sms;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Location;
import org.openmrs.api.context.Context;

/** Adds facility identification required on SMS messages sent by RwandaEMR. */
public class SmsMessageFormatter {

	public String appendHospitalName(String message) {
		return appendHospitalName(message, null);
	}

	/**
	 * Appends the parent of the current login location. The fallback location is
	 * used by background jobs, where an OpenMRS user context may not have a login
	 * location.
	 */
	public String appendHospitalName(String message, Location fallbackLocation) {
		String hospitalName = getHospitalName(fallbackLocation);
		if (StringUtils.isBlank(message) || StringUtils.isBlank(hospitalName)) {
			return message;
		}
		String trimmedMessage = StringUtils.stripEnd(message, null);
		if (StringUtils.endsWithIgnoreCase(trimmedMessage, hospitalName)) {
			return trimmedMessage;
		}
		return trimmedMessage + "\n" + hospitalName;
	}

	protected String getHospitalName(Location fallbackLocation) {
		Location loginLocation = getLoginLocation();
		Location sourceLocation = loginLocation == null ? fallbackLocation : loginLocation;
		Location hospital = sourceLocation == null ? null : sourceLocation.getParentLocation();
		return hospital == null ? null : StringUtils.trimToNull(hospital.getName());
	}

	protected Location getLoginLocation() {
		return Context.getUserContext() == null ? null : Context.getUserContext().getLocation();
	}
}
