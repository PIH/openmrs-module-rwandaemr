package org.openmrs.module.rwandaemr.appointment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

public class AppointmentHibernateMappingTest {

    @Test
    public void shouldLoadAppointmentHibernateMappings() {
        Configuration configuration = new Configuration();
        configuration.addResource("RwandaEmrAppointment.hbm.xml");
        assertDoesNotThrow(configuration::buildMappings);
    }
}
