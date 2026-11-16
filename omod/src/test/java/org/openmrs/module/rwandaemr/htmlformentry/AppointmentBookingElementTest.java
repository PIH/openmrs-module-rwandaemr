package org.openmrs.module.rwandaemr.htmlformentry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openmrs.Location;

public class AppointmentBookingElementTest {

    @Test
    public void shouldSelectConfiguredServicePointsByIdUuidOrName() {
        Location ncd = location(11, "ncd-uuid", "NCD Clinic");
        Location oncology = location(12, "oncology-uuid", "Oncology");
        Location maternity = location(13, "maternity-uuid", "Maternity");

        List<Location> selected = AppointmentBookingElement.selectServicePoints(
                Arrays.asList(ncd, oncology, maternity), "11, oncology-uuid, Maternity");

        assertEquals(Arrays.asList(ncd, oncology, maternity), selected);
    }

    @Test
    public void shouldUseEveryServicePointWhenAttributeIsOmitted() {
        List<Location> available = Arrays.asList(
                location(11, "ncd-uuid", "NCD Clinic"),
                location(12, "oncology-uuid", "Oncology"));

        assertEquals(available, AppointmentBookingElement.selectServicePoints(available, null));
    }

    @Test
    public void shouldRejectUnknownConfiguredServicePoint() {
        List<Location> available = Arrays.asList(location(11, "ncd-uuid", "NCD Clinic"));

        assertThrows(IllegalArgumentException.class,
                () -> AppointmentBookingElement.selectServicePoints(available, "Unknown Clinic"));
    }

    private Location location(Integer id, String uuid, String name) {
        Location location = new Location(id);
        location.setUuid(uuid);
        location.setName(name);
        return location;
    }
}
