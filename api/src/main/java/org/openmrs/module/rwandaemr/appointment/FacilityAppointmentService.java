package org.openmrs.module.rwandaemr.appointment;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Program;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public interface FacilityAppointmentService extends OpenmrsService {

    AppointmentSchedule saveSchedule(Location servicePoint, Date scheduleDate, int maximumPatients, String notes);

    AppointmentSchedule updateScheduleCapacity(Integer scheduleId, int maximumPatients);

    AppointmentSchedule setScheduleActive(Integer scheduleId, boolean active);

    AppointmentSchedule getSchedule(Integer scheduleId);

    List<AppointmentScheduleSummary> getScheduleSummaries(Location servicePoint, Date startDate, Date endDate,
                                                          boolean includeInactive);

    List<AppointmentScheduleSummary> getAvailableSchedules(Location servicePoint, Date startDate, Date endDate);

    AppointmentBooking requestAppointment(Patient patient, Integer scheduleId, String notes);

    AppointmentBooking requestAppointment(Patient patient, Integer scheduleId, Program program,
                                          AppointmentVisitType visitType, String notes);

    AppointmentBooking postponeBooking(Integer bookingId, Integer newScheduleId);

    AppointmentBooking cancelBooking(Integer bookingId);

    AppointmentBooking updateBookingStatus(Integer bookingId, AppointmentStatus status);

    AppointmentBooking getBooking(Integer bookingId);

    List<AppointmentBooking> getBookings(Collection<AppointmentSchedule> schedules);

    List<AppointmentBooking> getPatientBookings(Patient patient, Date startDate, Date endDate);

    List<Location> getServicePointLocations();
}
