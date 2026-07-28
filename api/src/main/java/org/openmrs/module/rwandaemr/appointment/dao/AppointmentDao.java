package org.openmrs.module.rwandaemr.appointment.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public interface AppointmentDao {

    AppointmentSchedule saveSchedule(AppointmentSchedule schedule);

    AppointmentSchedule getSchedule(Integer id);

    AppointmentSchedule getScheduleForUpdate(Integer id);

    AppointmentSchedule getScheduleByServicePointAndDate(Location servicePoint, Date scheduleDate);

    List<AppointmentSchedule> getSchedules(Location servicePoint, Date startDate, Date endDate,
                                           boolean includeInactive);

    AppointmentBooking saveBooking(AppointmentBooking booking);

    AppointmentBooking getBooking(Integer id);

    AppointmentBooking getBookingForUpdate(Integer id);

    AppointmentBooking getBookingByUuid(String uuid);

    AppointmentBooking getBooking(Patient patient, AppointmentSchedule schedule);

    int countBookings(AppointmentSchedule schedule, List<String> statusNames);

    List<AppointmentBooking> getBookings(Collection<AppointmentSchedule> schedules);

    List<AppointmentBooking> getPatientBookings(Patient patient, Date startDate, Date endDate);
}
