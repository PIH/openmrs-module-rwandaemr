package org.openmrs.module.rwandaemr.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class QueuePriorityTest {

    @Test
    public void shouldDisplayNormalPriorityAsNotEmergency() {
        assertEquals("Not Emergency", QueuePriority.NORMAL.getDisplayName());
        assertEquals("NORMAL", QueuePriority.NORMAL.name());
    }
}
