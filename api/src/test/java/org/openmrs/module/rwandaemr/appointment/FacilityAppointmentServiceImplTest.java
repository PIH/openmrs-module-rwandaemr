package org.openmrs.module.rwandaemr.appointment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.Program;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.User;
import org.openmrs.api.ProviderService;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.appointment.dao.AppointmentDao;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public class FacilityAppointmentServiceImplTest {

    private AppointmentDao dao;
    private LocationTagUtil locationTagUtil;
    private ProviderService providerService;
    private TestFacilityAppointmentService service;
    private Location servicePoint;
    private Patient patient;
    private AppointmentSchedule schedule;

    @BeforeEach
    public void setUp() {
        dao = mock(AppointmentDao.class);
        locationTagUtil = mock(LocationTagUtil.class);
        providerService = mock(ProviderService.class);
        service = new TestFacilityAppointmentService();
        service.setDao(dao);
        service.setLocationTagUtil(locationTagUtil);
        service.setProviderService(providerService);
        service.user = new User(1);

        servicePoint = new Location(11);
        servicePoint.setName("Consultation");
        servicePoint.setRetired(false);
        when(locationTagUtil.isLoginLocation(servicePoint)).thenReturn(true);

        patient = new Patient(21);
        patient.setVoided(false);

        schedule = new AppointmentSchedule();
        schedule.setId(31);
        schedule.setServicePoint(servicePoint);
        schedule.setScheduleDate(futureDate(2));
        schedule.setMaximumPatients(3);
        schedule.setActive(true);
        schedule.setVoided(false);
        when(dao.getScheduleForUpdate(31)).thenReturn(schedule);
        when(dao.saveBooking(any(AppointmentBooking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dao.saveSchedule(any(AppointmentSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void shouldReserveAnAvailableAppointmentSlot() {
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(2);
        Program program = new Program(51);
        program.setRetired(false);

        AppointmentBooking booking = service.requestAppointment(
                patient, 31, program, AppointmentVisitType.FOLLOW_UP, "Follow-up");

        assertSame(schedule, booking.getSchedule());
        assertSame(patient, booking.getPatient());
        assertSame(program, booking.getProgram());
        assertEquals(AppointmentVisitType.FOLLOW_UP, booking.getVisitType());
        assertEquals(AppointmentStatus.CONFIRMED, booking.getStatus());
        assertEquals("Follow-up", booking.getNotes());
        verify(dao).saveBooking(booking);
    }

    @Test
    public void shouldRejectAppointmentWhenScheduleIsFull() {
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(3);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.requestAppointment(patient, 31, null));

        assertEquals("The selected appointment date is fully booked", exception.getMessage());
        verify(dao, never()).saveBooking(any(AppointmentBooking.class));
    }

    @Test
    public void shouldReturnExistingCapacityBookingForDuplicateRequest() {
        AppointmentBooking existing = booking(AppointmentStatus.CONFIRMED);
        when(dao.getBooking(patient, schedule)).thenReturn(existing);

        AppointmentBooking result = service.requestAppointment(patient, 31, null);

        assertSame(existing, result);
        verify(dao, never()).saveBooking(any(AppointmentBooking.class));
    }

    @Test
    public void shouldReactivateCancelledBookingWhenCapacityIsAvailable() {
        AppointmentBooking cancelled = booking(AppointmentStatus.CANCELLED);
        when(dao.getBooking(patient, schedule)).thenReturn(cancelled);
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(1);

        AppointmentBooking result = service.requestAppointment(patient, 31, "Return visit");

        assertSame(cancelled, result);
        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        assertEquals("Return visit", result.getNotes());
        verify(dao).saveBooking(cancelled);
    }

    @Test
    public void shouldRejectRetiredProgram() {
        Program retiredProgram = new Program(51);
        retiredProgram.setRetired(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.requestAppointment(
                        patient, 31, retiredProgram, AppointmentVisitType.INITIAL, null));

        assertEquals("Program must be active", exception.getMessage());
        verify(dao, never()).saveBooking(any(AppointmentBooking.class));
    }

    @Test
    public void shouldNotReduceCapacityBelowExistingAppointments() {
        when(dao.getScheduleByServicePointDateAndProvider(
                any(Location.class), any(Date.class), isNull())).thenReturn(schedule);
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(3);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveSchedule(servicePoint, schedule.getScheduleDate(), 2, null));

        assertTrue(exception.getMessage().contains("3 existing appointments"));
        verify(dao, never()).saveSchedule(any(AppointmentSchedule.class));
    }

    @Test
    public void shouldUpdateCapacityForFutureSchedule() {
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(2);

        AppointmentSchedule result = service.updateScheduleCapacity(31, 5);

        assertSame(schedule, result);
        assertEquals(5, result.getMaximumPatients());
        assertSame(service.user, result.getChangedBy());
        verify(dao).saveSchedule(schedule);
    }

    @Test
    public void shouldNotReduceFutureCapacityBelowExistingAppointments() {
        when(dao.countBookings(schedule, capacityStatuses())).thenReturn(3);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateScheduleCapacity(31, 2));

        assertTrue(exception.getMessage().contains("3 existing appointments"));
        assertEquals(3, schedule.getMaximumPatients());
        verify(dao, never()).saveSchedule(any(AppointmentSchedule.class));
    }

    @Test
    public void shouldRejectCapacityChangeWhenScheduleIsNotInFuture() {
        schedule.setScheduleDate(new Date());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateScheduleCapacity(31, 5));

        assertEquals("Capacity can only be changed for a future appointment schedule", exception.getMessage());
        assertEquals(3, schedule.getMaximumPatients());
        verify(dao, never()).saveSchedule(any(AppointmentSchedule.class));
    }

    @Test
    public void shouldRejectTodayCapacityChangeThroughScheduleSave() {
        schedule.setScheduleDate(new Date());
        when(dao.getScheduleByServicePointDateAndProvider(
                any(Location.class), any(Date.class), isNull())).thenReturn(schedule);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.saveSchedule(servicePoint, schedule.getScheduleDate(), 5, null));

        assertEquals("Capacity can only be changed for a future appointment schedule", exception.getMessage());
        assertEquals(3, schedule.getMaximumPatients());
        verify(dao, never()).saveSchedule(any(AppointmentSchedule.class));
    }

    @Test
    public void shouldListOnlyProvidersWithProviderLicense() {
        Provider licensedProvider = licensedProvider(71, "MED-123");
        Provider unlicensedProvider = new Provider(72);
        Person person = new Person(172);
        person.setVoided(false);
        unlicensedProvider.setPerson(person);
        unlicensedProvider.setRetired(false);
        when(providerService.getAllProviders(false))
                .thenReturn(java.util.Arrays.asList(unlicensedProvider, licensedProvider));

        java.util.List<Provider> providers = service.getLicensedProviders();

        assertEquals(Collections.singletonList(licensedProvider), providers);
        assertEquals("MED-123", ProviderLicenseUtil.getLicense(licensedProvider));
    }

    @Test
    public void shouldSaveOptionalLicensedProviderOnSchedule() {
        Provider provider = licensedProvider(71, "MED-123");
        when(providerService.getProvider(71)).thenReturn(provider);

        AppointmentSchedule result = service.saveSchedule(
                servicePoint, futureDate(3), 5, provider, "Licensed provider schedule");

        assertSame(provider, result.getProvider());
        verify(dao).getScheduleByServicePointDateAndProvider(
                eq(servicePoint), any(Date.class), eq(provider));
        verify(dao).saveSchedule(result);
    }

    @Test
    public void shouldRejectProviderWithoutProviderLicense() {
        Person person = new Person(61);
        person.setVoided(false);
        Provider provider = new Provider(71);
        provider.setPerson(person);
        provider.setRetired(false);
        when(providerService.getProvider(71)).thenReturn(provider);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.saveSchedule(servicePoint, futureDate(3), 5, provider, null));

        assertEquals("Provider must be active and have a Provider License", exception.getMessage());
        verify(dao, never()).saveSchedule(any(AppointmentSchedule.class));
    }

    @Test
    public void shouldCompleteConfirmedAppointment() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);

        AppointmentBooking result = service.updateBookingStatus(41, AppointmentStatus.COMPLETED);

        assertEquals(AppointmentStatus.COMPLETED, result.getStatus());
        verify(dao).saveBooking(confirmed);
    }

    @Test
    public void shouldMarkTodaysConfirmedAppointmentPresent() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        confirmed.getSchedule().setScheduleDate(new Date());
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);

        AppointmentBooking result = service.markBookingPresent(41);

        assertEquals(AppointmentStatus.PRESENT, result.getStatus());
        assertSame(service.user, result.getChangedBy());
        verify(dao).saveBooking(confirmed);
    }

    @Test
    public void shouldCompletePresentAppointment() {
        AppointmentBooking present = booking(AppointmentStatus.PRESENT);
        when(dao.getBookingForUpdate(41)).thenReturn(present);

        AppointmentBooking result = service.updateBookingStatus(41, AppointmentStatus.COMPLETED);

        assertEquals(AppointmentStatus.COMPLETED, result.getStatus());
        verify(dao).saveBooking(present);
    }

    @Test
    public void shouldKeepPresentAppointmentWhenRegistrationIsOpenedAgain() {
        AppointmentBooking present = booking(AppointmentStatus.PRESENT);
        present.getSchedule().setScheduleDate(new Date());
        when(dao.getBookingForUpdate(41)).thenReturn(present);

        AppointmentBooking result = service.markBookingPresent(41);

        assertSame(present, result);
        assertEquals(AppointmentStatus.PRESENT, result.getStatus());
        verify(dao, never()).saveBooking(present);
    }

    @Test
    public void shouldRejectMarkPresentForFutureAppointment() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.markBookingPresent(41));

        assertEquals("Only today's appointments can be marked present", exception.getMessage());
        verify(dao, never()).saveBooking(confirmed);
    }

    @Test
    public void shouldRejectInvalidAppointmentTransition() {
        AppointmentBooking completed = booking(AppointmentStatus.COMPLETED);
        when(dao.getBookingForUpdate(41)).thenReturn(completed);

        assertThrows(IllegalStateException.class,
                () -> service.updateBookingStatus(41, AppointmentStatus.CONFIRMED));
    }

    @Test
    public void shouldRejectNoShowTransition() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);

        assertThrows(IllegalStateException.class,
                () -> service.updateBookingStatus(41, AppointmentStatus.NO_SHOW));
        verify(dao, never()).saveBooking(confirmed);
    }

    @Test
    public void shouldNotCancelCompletedAppointment() {
        AppointmentBooking completed = booking(AppointmentStatus.COMPLETED);
        when(dao.getBookingForUpdate(41)).thenReturn(completed);

        assertThrows(IllegalStateException.class, () -> service.cancelBooking(41));
        verify(dao, never()).saveBooking(completed);
    }

    @Test
    public void shouldPostponeConfirmedAppointmentToLaterAvailableSchedule() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        AppointmentSchedule laterSchedule = schedule(32, servicePoint, 5, 4);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);
        when(dao.getScheduleForUpdate(32)).thenReturn(laterSchedule);
        when(dao.countBookings(laterSchedule, capacityStatuses())).thenReturn(3);

        AppointmentBooking result = service.postponeBooking(41, 32);

        assertSame(laterSchedule, result.getSchedule());
        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        assertSame(service.user, result.getChangedBy());
        verify(dao).saveBooking(confirmed);
    }

    @Test
    public void shouldBringForwardConfirmedAppointmentToEarlierAvailableSchedule() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        AppointmentSchedule earlierSchedule = schedule(32, servicePoint, 1, 4);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);
        when(dao.getScheduleForUpdate(32)).thenReturn(earlierSchedule);
        when(dao.countBookings(earlierSchedule, capacityStatuses())).thenReturn(3);

        AppointmentBooking result = service.postponeBooking(41, 32);

        assertSame(earlierSchedule, result.getSchedule());
        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        assertSame(service.user, result.getChangedBy());
        verify(dao).saveBooking(confirmed);
    }

    @Test
    public void shouldRejectRescheduleToPastDate() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        AppointmentSchedule pastSchedule = schedule(32, servicePoint, -1, 4);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);
        when(dao.getScheduleForUpdate(32)).thenReturn(pastSchedule);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.postponeBooking(41, 32));

        assertEquals("The new appointment date cannot be in the past", exception.getMessage());
        assertSame(schedule, confirmed.getSchedule());
        verify(dao, never()).saveBooking(confirmed);
    }

    @Test
    public void shouldRejectPostponeWhenLaterScheduleIsFull() {
        AppointmentBooking confirmed = booking(AppointmentStatus.CONFIRMED);
        AppointmentSchedule laterSchedule = schedule(32, servicePoint, 5, 4);
        when(dao.getBookingForUpdate(41)).thenReturn(confirmed);
        when(dao.getScheduleForUpdate(32)).thenReturn(laterSchedule);
        when(dao.countBookings(laterSchedule, capacityStatuses())).thenReturn(4);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> service.postponeBooking(41, 32));

        assertEquals("The selected appointment date is fully booked", exception.getMessage());
        assertSame(schedule, confirmed.getSchedule());
        verify(dao, never()).saveBooking(confirmed);
    }

    private AppointmentBooking booking(AppointmentStatus status) {
        AppointmentBooking booking = new AppointmentBooking();
        booking.setId(41);
        booking.setSchedule(schedule);
        booking.setPatient(patient);
        booking.setStatus(status);
        booking.setVoided(false);
        return booking;
    }

    private Date futureDate(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private AppointmentSchedule schedule(int id, Location location, int daysFromToday, int maximumPatients) {
        AppointmentSchedule appointmentSchedule = new AppointmentSchedule();
        appointmentSchedule.setId(id);
        appointmentSchedule.setServicePoint(location);
        appointmentSchedule.setScheduleDate(futureDate(daysFromToday));
        appointmentSchedule.setMaximumPatients(maximumPatients);
        appointmentSchedule.setActive(true);
        appointmentSchedule.setVoided(false);
        return appointmentSchedule;
    }

    private Provider licensedProvider(int id, String license) {
        Person person = new Person(id + 100);
        person.setVoided(false);
        Provider provider = new Provider(id);
        provider.setPerson(person);
        provider.setRetired(false);
        ProviderAttributeType licenseType = new ProviderAttributeType();
        licenseType.setName(ProviderLicenseUtil.ATTRIBUTE_TYPE_NAME);
        licenseType.setRetired(false);
        ProviderAttribute licenseAttribute = new ProviderAttribute();
        licenseAttribute.setAttributeType(licenseType);
        licenseAttribute.setValueReferenceInternal(license);
        provider.addAttribute(licenseAttribute);
        return provider;
    }

    private java.util.List<String> capacityStatuses() {
        return java.util.Arrays.asList("REQUESTED", "CONFIRMED", "PRESENT", "COMPLETED");
    }

    private static class TestFacilityAppointmentService extends FacilityAppointmentServiceImpl {

        private User user;

        @Override
        protected User getAuthenticatedUser() {
            return user;
        }
    }
}
