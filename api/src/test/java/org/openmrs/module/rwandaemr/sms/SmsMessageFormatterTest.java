package org.openmrs.module.rwandaemr.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openmrs.Location;

public class SmsMessageFormatterTest {

	@Test
	public void shouldAppendParentOfLoginLocation() {
		Location hospital = location("District Hospital", null);
		SmsMessageFormatter formatter = formatter(location("Outpatient Clinic", hospital));

		assertEquals("Your appointment is tomorrow.\nDistrict Hospital",
				formatter.appendHospitalName("Your appointment is tomorrow."));
	}

	@Test
	public void shouldUseFallbackLocationForBackgroundTasks() {
		Location hospital = location("District Hospital", null);
		SmsMessageFormatter formatter = formatter(null);

		assertEquals("Your appointment is tomorrow.\nDistrict Hospital",
				formatter.appendHospitalName("Your appointment is tomorrow.", location("NCD Clinic", hospital)));
	}

	@Test
	public void shouldNotAppendHospitalNameTwice() {
		Location hospital = location("District Hospital", null);
		SmsMessageFormatter formatter = formatter(location("Outpatient Clinic", hospital));

		assertEquals("Message\nDistrict Hospital", formatter.appendHospitalName("Message\nDistrict Hospital"));
	}

	@Test
	public void shouldLeaveMessageUnchangedWhenParentLocationIsUnavailable() {
		SmsMessageFormatter formatter = formatter(location("Hospital", null));

		assertEquals("Message", formatter.appendHospitalName("Message"));
	}

	private SmsMessageFormatter formatter(final Location loginLocation) {
		return new SmsMessageFormatter() {
			@Override
			protected Location getLoginLocation() {
				return loginLocation;
			}
		};
	}

	private Location location(String name, Location parent) {
		Location location = new Location();
		location.setName(name);
		location.setParentLocation(parent);
		return location;
	}
}
