package org.openmrs.module.rwandaemr.appointment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Program;
import org.openmrs.User;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.appointment.dao.AppointmentDao;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class FacilityAppointmentServiceImpl extends BaseOpenmrsService implements FacilityAppointmentService {

    private static final List<String> CAPACITY_STATUSES = Arrays.asList(
            AppointmentStatus.REQUESTED.name(),
            AppointmentStatus.CONFIRMED.name(),
            AppointmentStatus.COMPLETED.name());

    @Setter
    private AppointmentDao dao;

    @Setter
    private LocationTagUtil locationTagUtil;

    @Override
    @Authorized(AppointmentPrivileges.MANAGE_SCHEDULES)
    public AppointmentSchedule saveSchedule(Location servicePoint, Date scheduleDate, int maximumPatients,
                                            String notes) {
        validateServicePoint(servicePoint);
        Date normalizedDate = normalizeDate(scheduleDate);
        if (normalizedDate.before(today())) {
            throw new IllegalArgumentException("Appointment schedule date cannot be in the past");
        }
        if (maximumPatients < 1) {
            throw new IllegalArgumentException("Maximum patients must be at least 1");
        }
        AppointmentSchedule schedule = dao.getScheduleByServicePointAndDate(servicePoint, normalizedDate);
        if (schedule != null) {
            schedule = dao.getScheduleForUpdate(schedule.getId());
            if (!normalizedDate.after(today())
                    && !Integer.valueOf(maximumPatients).equals(schedule.getMaximumPatients())) {
                throw new IllegalStateException(
                        "Capacity can only be changed for a future appointment schedule");
            }
            int bookedPatients = dao.countBookings(schedule, CAPACITY_STATUSES);
            if (maximumPatients < bookedPatients) {
                throw new IllegalArgumentException(
                        "Maximum patients cannot be less than the " + bookedPatients + " existing appointments");
            }
        } else {
            schedule = new AppointmentSchedule();
            schedule.setUuid(UUID.randomUUID().toString());
            schedule.setServicePoint(servicePoint);
            schedule.setScheduleDate(normalizedDate);
            schedule.setActive(true);
            setCreationMetadata(schedule, new Date());
        }
        schedule.setMaximumPatients(maximumPatients);
        schedule.setNotes(StringUtils.trimToNull(notes));
        setChangeMetadataIfPersisted(schedule);
        return dao.saveSchedule(schedule);
    }

    @Override
    @Authorized(AppointmentPrivileges.MANAGE_SCHEDULES)
    public AppointmentSchedule updateScheduleCapacity(Integer scheduleId, int maximumPatients) {
        AppointmentSchedule schedule = requireScheduleForUpdate(scheduleId);
        if (!normalizeDate(schedule.getScheduleDate()).after(today())) {
            throw new IllegalStateException("Capacity can only be changed for a future appointment schedule");
        }
        if (maximumPatients < 1) {
            throw new IllegalArgumentException("Maximum patients must be at least 1");
        }
        int bookedPatients = dao.countBookings(schedule, CAPACITY_STATUSES);
        if (maximumPatients < bookedPatients) {
            throw new IllegalArgumentException(
                    "Maximum patients cannot be less than the " + bookedPatients + " existing appointments");
        }
        schedule.setMaximumPatients(maximumPatients);
        setChangeMetadata(schedule, new Date());
        return dao.saveSchedule(schedule);
    }

    @Override
    @Authorized(AppointmentPrivileges.MANAGE_SCHEDULES)
    public AppointmentSchedule setScheduleActive(Integer scheduleId, boolean active) {
        AppointmentSchedule schedule = requireScheduleForUpdate(scheduleId);
        schedule.setActive(active);
        setChangeMetadata(schedule, new Date());
        return dao.saveSchedule(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({
            AppointmentPrivileges.VIEW,
            AppointmentPrivileges.MANAGE_SCHEDULES,
            AppointmentPrivileges.BOOK,
            AppointmentPrivileges.MANAGE
    })
    public AppointmentSchedule getSchedule(Integer scheduleId) {
        return dao.getSchedule(scheduleId);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({ AppointmentPrivileges.VIEW, AppointmentPrivileges.MANAGE_SCHEDULES })
    public List<AppointmentScheduleSummary> getScheduleSummaries(Location servicePoint, Date startDate, Date endDate,
                                                                 boolean includeInactive) {
        Date rangeStart = normalizeDate(startDate);
        Date rangeEnd = normalizeDate(endDate);
        if (rangeEnd.before(rangeStart)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }
        return summarize(dao.getSchedules(servicePoint, rangeStart, rangeEnd, includeInactive));
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(AppointmentPrivileges.BOOK)
    public List<AppointmentScheduleSummary> getAvailableSchedules(Location servicePoint, Date startDate,
                                                                  Date endDate) {
        if (servicePoint == null) {
            return new ArrayList<AppointmentScheduleSummary>();
        }
        List<AppointmentScheduleSummary> available = new ArrayList<AppointmentScheduleSummary>();
        for (AppointmentScheduleSummary summary : getScheduleSummaries(
                servicePoint, laterOf(startDate, today()), endDate, false)) {
            if (summary.isAvailable()) {
                available.add(summary);
            }
        }
        return available;
    }

    @Override
    @Authorized(AppointmentPrivileges.BOOK)
    public AppointmentBooking requestAppointment(Patient patient, Integer scheduleId, String notes) {
        return requestAppointment(patient, scheduleId, null, null, notes);
    }

    @Override
    @Authorized(AppointmentPrivileges.BOOK)
    public AppointmentBooking requestAppointment(Patient patient, Integer scheduleId, Program program,
                                                 AppointmentVisitType visitType, String notes) {
        if (patient == null || Boolean.TRUE.equals(patient.getVoided())) {
            throw new IllegalArgumentException("An active patient is required");
        }
        validateProgram(program);
        AppointmentSchedule schedule = requireScheduleForUpdate(scheduleId);
        if (!Boolean.TRUE.equals(schedule.getActive())) {
            throw new IllegalStateException("The selected appointment date is not available");
        }
        if (schedule.getScheduleDate().before(today())) {
            throw new IllegalStateException("Appointments cannot be requested for a past date");
        }
        AppointmentBooking booking = dao.getBooking(patient, schedule);
        if (booking != null && usesCapacity(booking.getStatus())) {
            if (AppointmentStatus.REQUESTED.equals(booking.getStatus())) {
                booking.setStatus(AppointmentStatus.CONFIRMED);
                setChangeMetadata(booking, new Date());
                return dao.saveBooking(booking);
            }
            return booking;
        }
        int bookedPatients = dao.countBookings(schedule, CAPACITY_STATUSES);
        int maximumPatients = schedule.getMaximumPatients() == null ? 0 : schedule.getMaximumPatients();
        if (bookedPatients >= maximumPatients) {
            throw new IllegalStateException("The selected appointment date is fully booked");
        }
        Date now = new Date();
        if (booking == null) {
            booking = new AppointmentBooking();
            booking.setUuid(UUID.randomUUID().toString());
            booking.setPatient(patient);
            booking.setSchedule(schedule);
            setCreationMetadata(booking, now);
        } else {
            setChangeMetadata(booking, now);
        }
        booking.setProgram(program);
        booking.setVisitType(visitType);
        booking.setStatus(AppointmentStatus.CONFIRMED);
        booking.setRequestedAt(now);
        booking.setNotes(StringUtils.trimToNull(notes));
        return dao.saveBooking(booking);
    }

    @Override
    @Authorized(AppointmentPrivileges.BOOK)
    public AppointmentBooking postponeBooking(Integer bookingId, Integer newScheduleId) {
        AppointmentBooking booking = requireBookingForUpdate(bookingId);
        if (!AppointmentStatus.REQUESTED.equals(booking.getStatus())
                && !AppointmentStatus.CONFIRMED.equals(booking.getStatus())) {
            throw new IllegalStateException("This appointment can no longer be postponed");
        }

        AppointmentSchedule currentSchedule = booking.getSchedule();
        AppointmentSchedule newSchedule = requireScheduleForUpdate(newScheduleId);
        if (currentSchedule == null || currentSchedule.getId().equals(newSchedule.getId())) {
            throw new IllegalArgumentException("Select a different appointment date");
        }
        if (!Boolean.TRUE.equals(newSchedule.getActive())) {
            throw new IllegalStateException("The selected appointment date is not available");
        }
        if (!normalizeDate(newSchedule.getScheduleDate()).after(
                normalizeDate(currentSchedule.getScheduleDate()))) {
            throw new IllegalArgumentException("The postponed appointment date must be later than the current date");
        }
        if (!normalizeDate(newSchedule.getScheduleDate()).after(today())) {
            throw new IllegalArgumentException("The postponed appointment date must be in the future");
        }
        if (!currentSchedule.getServicePoint().equals(newSchedule.getServicePoint())) {
            throw new IllegalArgumentException("The postponed appointment must use the same service point");
        }
        AppointmentBooking existing = dao.getBooking(booking.getPatient(), newSchedule);
        if (existing != null) {
            throw new IllegalStateException("The patient already has an appointment on the selected schedule");
        }
        int bookedPatients = dao.countBookings(newSchedule, CAPACITY_STATUSES);
        int maximumPatients = newSchedule.getMaximumPatients() == null ? 0 : newSchedule.getMaximumPatients();
        if (bookedPatients >= maximumPatients) {
            throw new IllegalStateException("The selected appointment date is fully booked");
        }

        booking.setSchedule(newSchedule);
        if (AppointmentStatus.REQUESTED.equals(booking.getStatus())) {
            booking.setStatus(AppointmentStatus.CONFIRMED);
        }
        setChangeMetadata(booking, new Date());
        return dao.saveBooking(booking);
    }

    @Override
    @Authorized(AppointmentPrivileges.BOOK)
    public AppointmentBooking cancelBooking(Integer bookingId) {
        AppointmentBooking booking = requireBookingForUpdate(bookingId);
        if (AppointmentStatus.REQUESTED.equals(booking.getStatus())
                || AppointmentStatus.CONFIRMED.equals(booking.getStatus())) {
            booking.setStatus(AppointmentStatus.CANCELLED);
            setChangeMetadata(booking, new Date());
            dao.saveBooking(booking);
        } else if (!AppointmentStatus.CANCELLED.equals(booking.getStatus())) {
            throw new IllegalStateException("This appointment can no longer be cancelled");
        }
        return booking;
    }

    @Override
    @Authorized(AppointmentPrivileges.MANAGE)
    public AppointmentBooking updateBookingStatus(Integer bookingId, AppointmentStatus status) {
        AppointmentBooking booking = requireBookingForUpdate(bookingId);
        if (status == null) {
            throw new IllegalArgumentException("Appointment status is required");
        }
        validateTransition(booking.getStatus(), status);
        booking.setStatus(status);
        setChangeMetadata(booking, new Date());
        return dao.saveBooking(booking);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({
            AppointmentPrivileges.VIEW,
            AppointmentPrivileges.BOOK,
            AppointmentPrivileges.MANAGE
    })
    public AppointmentBooking getBooking(Integer bookingId) {
        return dao.getBooking(bookingId);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({ AppointmentPrivileges.VIEW, AppointmentPrivileges.MANAGE })
    public List<AppointmentBooking> getBookings(Collection<AppointmentSchedule> schedules) {
        return dao.getBookings(schedules);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({ AppointmentPrivileges.BOOK, AppointmentPrivileges.VIEW, AppointmentPrivileges.MANAGE })
    public List<AppointmentBooking> getPatientBookings(Patient patient, Date startDate, Date endDate) {
        return dao.getPatientBookings(patient,
                startDate == null ? null : normalizeDate(startDate),
                endDate == null ? null : normalizeDate(endDate));
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({
            AppointmentPrivileges.VIEW,
            AppointmentPrivileges.MANAGE_SCHEDULES,
            AppointmentPrivileges.BOOK,
            AppointmentPrivileges.MANAGE
    })
    public List<Location> getServicePointLocations() {
        return locationTagUtil.getLoginLocations();
    }

    protected User getAuthenticatedUser() {
        return Context.getAuthenticatedUser();
    }

    private List<AppointmentScheduleSummary> summarize(List<AppointmentSchedule> schedules) {
        List<AppointmentScheduleSummary> summaries = new ArrayList<AppointmentScheduleSummary>();
        for (AppointmentSchedule schedule : schedules) {
            summaries.add(new AppointmentScheduleSummary(
                    schedule, dao.countBookings(schedule, CAPACITY_STATUSES)));
        }
        return summaries;
    }

    private AppointmentSchedule requireScheduleForUpdate(Integer scheduleId) {
        AppointmentSchedule schedule = dao.getScheduleForUpdate(scheduleId);
        if (schedule == null || Boolean.TRUE.equals(schedule.getVoided())) {
            throw new IllegalArgumentException("Appointment schedule was not found");
        }
        return schedule;
    }

    private AppointmentBooking requireBookingForUpdate(Integer bookingId) {
        AppointmentBooking booking = dao.getBookingForUpdate(bookingId);
        if (booking == null || Boolean.TRUE.equals(booking.getVoided())) {
            throw new IllegalArgumentException("Appointment booking was not found");
        }
        return booking;
    }

    private void validateServicePoint(Location servicePoint) {
        if (servicePoint == null || Boolean.TRUE.equals(servicePoint.getRetired())
                || !locationTagUtil.isLoginLocation(servicePoint)) {
            throw new IllegalArgumentException("Service point must be an active Login Location");
        }
    }

    private void validateProgram(Program program) {
        if (program != null && Boolean.TRUE.equals(program.getRetired())) {
            throw new IllegalArgumentException("Program must be active");
        }
    }

    private void validateTransition(AppointmentStatus current, AppointmentStatus next) {
        if (current == next) {
            return;
        }
        boolean allowed = (AppointmentStatus.REQUESTED.equals(current)
                || AppointmentStatus.CONFIRMED.equals(current))
                && (AppointmentStatus.COMPLETED.equals(next) || AppointmentStatus.CANCELLED.equals(next));
        if (!allowed) {
            throw new IllegalStateException("Appointment cannot change from " + current + " to " + next);
        }
    }

    private boolean usesCapacity(AppointmentStatus status) {
        return status != null && status.usesCapacity();
    }

    private Date laterOf(Date left, Date right) {
        Date normalizedLeft = normalizeDate(left);
        Date normalizedRight = normalizeDate(right);
        return normalizedLeft.after(normalizedRight) ? normalizedLeft : normalizedRight;
    }

    private Date today() {
        return normalizeDate(new Date());
    }

    private Date normalizeDate(Date date) {
        if (date == null) {
            throw new IllegalArgumentException("Appointment date is required");
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private void setCreationMetadata(BaseOpenmrsData object, Date date) {
        object.setCreator(getAuthenticatedUser());
        object.setDateCreated(date);
        object.setVoided(false);
    }

    private void setChangeMetadataIfPersisted(BaseOpenmrsData object) {
        if (object.getId() != null) {
            setChangeMetadata(object, new Date());
        }
    }

    private void setChangeMetadata(BaseOpenmrsData object, Date date) {
        object.setChangedBy(getAuthenticatedUser());
        object.setDateChanged(date);
    }
}
