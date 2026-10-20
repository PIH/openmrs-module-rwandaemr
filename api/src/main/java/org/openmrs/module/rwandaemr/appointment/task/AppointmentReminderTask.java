package org.openmrs.module.rwandaemr.appointment.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.scheduler.tasks.AbstractTask;

public class AppointmentReminderTask extends AbstractTask {

    private static final Log log = LogFactory.getLog(AppointmentReminderTask.class);

    @Override
    public void execute() {
        try {
            int sent = Context.getService(FacilityAppointmentService.class).sendAppointmentReminders();
            log.info("Sent " + sent + " appointment reminder SMS message(s) for appointments in two days");
        }
        catch (Exception e) {
            log.error("Unable to process appointment reminder SMS messages", e);
        }
    }
}
