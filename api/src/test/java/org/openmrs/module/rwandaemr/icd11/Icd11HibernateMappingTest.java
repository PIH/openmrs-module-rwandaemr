package org.openmrs.module.rwandaemr.icd11;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Icd11HibernateMappingTest {

	@Test
	public void shouldLoadIcd11HibernateMappings() {
		Configuration configuration = new Configuration();
		configuration.addResource("Icd11.hbm.xml");
		assertDoesNotThrow(configuration::buildMappings);
	}
}
