package org.openmrs.module.rwandaemr.radiology;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class RadiologyHl7MessageLogTest {

	private static final String ORU = "MSH|^~\\&|RIS|PACS|OpenMRS|RwandaEMR|20261010093000||ORU^R01|MSG00042|P|2.3\r"
			+ "PID|1||3220000000||Test^Patient\r"
			+ "OBR|1|ORD-686277^OpenMRS||CTABD^CT of abdomen\r"
			+ "OBX|1|TX|CTABD||Report text||||||I\r"
			+ "OBX|2|RP|LINK||^https://pacs.example/viewer?AccessionNumber=ORD-686277\r";

	@Test
	public void shouldReadFieldsByTheirHl7Number() {
		assertThat(RadiologyHl7MessageLog.getField(ORU, "MSH", 0, 9), equalTo("ORU^R01"));
		assertThat(RadiologyHl7MessageLog.getField(ORU, "MSH", 0, 10), equalTo("MSG00042"));
		assertThat(RadiologyHl7MessageLog.getComponent(RadiologyHl7MessageLog.getField(ORU, "OBR", 0, 2), 0),
				equalTo("ORD-686277"));
		assertThat(RadiologyHl7MessageLog.getField(ORU, "OBX", 0, 11), equalTo("I"));
		assertThat(HL7Utils.getViewerUrl(RadiologyHl7MessageLog.getField(ORU, "OBX", 1, 5)),
				equalTo("https://pacs.example/viewer?AccessionNumber=ORD-686277"));
	}

	@Test
	public void shouldReturnNullForMissingFields() {
		String withoutLink = ORU.replace("^https://pacs.example/viewer?AccessionNumber=ORD-686277", "");
		assertThat(HL7Utils.getViewerUrl(RadiologyHl7MessageLog.getField(withoutLink, "OBX", 1, 5)), nullValue());
		assertThat(RadiologyHl7MessageLog.getField(ORU, "OBX", 2, 5), nullValue());
		assertThat(RadiologyHl7MessageLog.getField(ORU, "ORC", 0, 1), nullValue());
		assertThat(RadiologyHl7MessageLog.getField(ORU, "OBR", 0, 40), nullValue());
		assertThat(RadiologyHl7MessageLog.getField(null, "MSH", 0, 9), nullValue());
	}
}
