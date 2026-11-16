package org.openmrs.module.rwandaemr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.Date;

import org.junit.jupiter.api.Test;

public class RwandaEmrActivatorTest {

    @Test
    public void shouldScheduleAppointmentRemindersForNextSixThirtyInTheMorning() {
        Date now = new Date();

        Date runTime = RwandaEmrActivator.nextReminderRunTime();

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(runTime);
        assertEquals(6, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, calendar.get(Calendar.MINUTE));
        assertEquals(0, calendar.get(Calendar.SECOND));
        assertTrue(runTime.after(now));
        assertTrue(runTime.getTime() - now.getTime() <= 24L * 60L * 60L * 1000L);
    }
}
