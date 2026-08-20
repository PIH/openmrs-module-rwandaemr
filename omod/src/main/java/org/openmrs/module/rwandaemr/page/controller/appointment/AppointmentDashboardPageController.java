package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.appointment.AppointmentPrivileges;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.AppointmentStatus;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.parameter.VisitSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AppointmentDashboardPageController extends AppointmentPageSupport {

    public Object get(PageModel model,
                      @SpringBean FacilityAppointmentService appointmentService,
                      @SpringBean RwandaEmrConfig rwandaEmrConfig,
                      @SpringBean("encounterService") EncounterService encounterService,
                      @SpringBean("visitService") VisitService visitService,
                      @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                      @RequestParam(value = "startDate", required = false) String startDate,
                      @RequestParam(value = "endDate", required = false) String endDate,
                      @RequestParam(value = "export", required = false) String export) {
        if (!Context.hasPrivilege(AppointmentPrivileges.VIEW)) {
            model.addAttribute("authorized", false);
            return null;
        }
        LocalDate today = LocalDate.now();
        String selectedStartDate = StringUtils.isBlank(startDate) ? today.toString() : startDate;
        String selectedEndDate = StringUtils.isBlank(endDate) ? today.toString() : endDate;
        List<Location> servicePoints = appointmentService.getServicePointLocations();
        Location selectedServicePoint = findServicePoint(servicePoints, servicePointId);
        List<AppointmentScheduleSummary> summaries = Collections.emptyList();
        Map<Integer, List<AppointmentBooking>> bookingsByScheduleId =
                new LinkedHashMap<Integer, List<AppointmentBooking>>();
        Map<Integer, Patient> bookedPatientsById = new LinkedHashMap<Integer, Patient>();
        Map<Integer, Integer> registrationEncounterIdsByPatientId =
                Collections.emptyMap();
        Set<Integer> todayScheduleIds = new HashSet<Integer>();
        String dateRangeError = null;
        int totalCapacity = 0;
        int totalBooked = 0;
        try {
            LocalDate rangeStart = parseDate(selectedStartDate, today);
            LocalDate rangeEnd = parseDate(selectedEndDate, today);
            Date todayStart = toDate(today);
            Date tomorrowStart = toDate(today.plusDays(1));
            if (rangeEnd.isBefore(rangeStart)) {
                throw new IllegalArgumentException("End date must be on or after start date");
            }
            summaries = appointmentService.getScheduleSummaries(
                    selectedServicePoint, toDate(rangeStart), toDate(rangeEnd), true);
            List<AppointmentSchedule> schedules = new ArrayList<AppointmentSchedule>();
            for (AppointmentScheduleSummary summary : summaries) {
                AppointmentSchedule schedule = summary.getSchedule();
                schedules.add(schedule);
                bookingsByScheduleId.put(schedule.getId(), new ArrayList<AppointmentBooking>());
                Date scheduleDate = schedule.getScheduleDate();
                if (scheduleDate != null && !scheduleDate.before(todayStart) && scheduleDate.before(tomorrowStart)) {
                    todayScheduleIds.add(schedule.getId());
                }
                totalCapacity += schedule.getMaximumPatients() == null ? 0 : schedule.getMaximumPatients();
                totalBooked += summary.getBookedPatients();
            }
            for (AppointmentBooking booking : appointmentService.getBookings(schedules)) {
                Integer scheduleId = booking.getSchedule().getId();
                if (bookingsByScheduleId.containsKey(scheduleId)) {
                    bookingsByScheduleId.get(scheduleId).add(booking);
                }
                Patient patient = booking.getPatient();
                if (todayScheduleIds.contains(scheduleId) && patient != null && patient.getId() != null) {
                    bookedPatientsById.put(patient.getId(), patient);
                }
            }
            registrationEncounterIdsByPatientId = findEditableRegistrationEncounterIds(
                    encounterService, visitService, rwandaEmrConfig.getRegistrationEncounterType(),
                    bookedPatientsById.values(), todayStart, tomorrowStart);
        }
        catch (IllegalArgumentException e) {
            dateRangeError = e.getMessage();
        }
        model.addAttribute("authorized", true);
        model.addAttribute("canManageAppointments", Context.hasPrivilege(AppointmentPrivileges.MANAGE));
        model.addAttribute("servicePoints", servicePoints);
        model.addAttribute("selectedServicePoint", selectedServicePoint);
        model.addAttribute("selectedStartDate", selectedStartDate);
        model.addAttribute("selectedEndDate", selectedEndDate);
        model.addAttribute("dateRangeError", dateRangeError);
        model.addAttribute("scheduleSummaries", summaries);
        model.addAttribute("bookingsByScheduleId", bookingsByScheduleId);
        model.addAttribute("todayScheduleIds", todayScheduleIds);
        model.addAttribute("registrationEncounterIdsByPatientId", registrationEncounterIdsByPatientId);
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("totalBooked", totalBooked);
        model.addAttribute("totalRemaining", Math.max(0, totalCapacity - totalBooked));
        model.addAttribute("appointmentStatuses", AppointmentStatus.values());
        if (dateRangeError == null && "excel".equalsIgnoreCase(export)) {
            String filename = "appointment-dashboard-" + selectedStartDate + "-to-" + selectedEndDate + ".xls";
            return new FileDownload(filename, AppointmentDashboardExcel.CONTENT_TYPE,
                    AppointmentDashboardExcel.create(summaries, bookingsByScheduleId));
        }
        return null;
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean FacilityAppointmentService appointmentService,
                       @SpringBean RwandaEmrConfig rwandaEmrConfig,
                       @SpringBean("encounterService") EncounterService encounterService,
                       @SpringBean("visitService") VisitService visitService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "bookingId", required = false) Integer bookingId,
                       @RequestParam(value = "appointmentStatus", required = false) String appointmentStatus,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                       @RequestParam(value = "startDate", required = false) String startDate,
                       @RequestParam(value = "endDate", required = false) String endDate) {
        try {
            if ("register".equals(action)) {
                AppointmentBooking booking = appointmentService.markBookingPresent(bookingId);
                return registrationRedirect(
                        ui, encounterService, visitService, rwandaEmrConfig, booking);
            }
            AppointmentStatus status = StringUtils.isBlank(appointmentStatus)
                    ? null : AppointmentStatus.valueOf(appointmentStatus);
            appointmentService.updateBookingStatus(bookingId, status);
            setToast(sessionContext, "Appointment status updated");
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return redirect(ui, "appointment/appointmentDashboard",
                "servicePointId", servicePointId, "startDate", startDate, "endDate", endDate);
    }

    private String registrationRedirect(
            UiUtils ui, EncounterService encounterService, VisitService visitService,
            RwandaEmrConfig rwandaEmrConfig, AppointmentBooking booking) {
        Patient patient = booking == null ? null : booking.getPatient();
        if (patient == null || patient.getId() == null) {
            throw new IllegalStateException("Appointment patient was not found");
        }
        LocalDate today = LocalDate.now();
        Map<Integer, Integer> encounterIds = findEditableRegistrationEncounterIds(
                encounterService, visitService, rwandaEmrConfig.getRegistrationEncounterType(),
                Collections.singletonList(patient), toDate(today), toDate(today.plusDays(1)));
        Integer encounterId = encounterIds.get(patient.getId());
        Map<String, Object> summaryParameters = new LinkedHashMap<String, Object>();
        summaryParameters.put("patientId", patient.getId());
        summaryParameters.put("appId", "rwandaemr.registerPatient");
        String returnUrl = ui.pageLink("registrationapp", "registrationSummary", summaryParameters);

        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("patientId", patient.getId());
        parameters.put("definitionUiResource", "file:configuration/htmlforms/registration.xml");
        parameters.put("returnUrl", returnUrl);
        String page;
        if (encounterId == null) {
            page = "htmlform/enterHtmlFormWithStandardUi";
            parameters.put("createVisit", true);
        } else {
            page = "htmlform/editHtmlFormWithStandardUi";
            parameters.put("encounterId", encounterId);
        }
        return "redirect:" + ui.pageLink("htmlformentryui", page, parameters);
    }

    private Map<Integer, Integer> findEditableRegistrationEncounterIds(
            EncounterService encounterService, VisitService visitService, EncounterType registrationEncounterType,
            Collection<Patient> patients, Date todayStart, Date tomorrowStart) {
        if (registrationEncounterType == null || patients.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Encounter> candidates = new ArrayList<Encounter>();
        candidates.addAll(encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                .setFromDate(todayStart)
                .setToDate(new Date(tomorrowStart.getTime() - 1L))
                .setEncounterTypes(Collections.singletonList(registrationEncounterType))
                .setIncludeVoided(false)
                .createEncounterSearchCriteria()));

        List<Visit> activeVisits = visitService.getVisits(new VisitSearchCriteriaBuilder()
                .patients(patients)
                .includeInactive(false)
                .includeVoided(false)
                .build());
        if (!activeVisits.isEmpty()) {
            candidates.addAll(encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                    .setVisits(activeVisits)
                    .setEncounterTypes(Collections.singletonList(registrationEncounterType))
                    .setIncludeVoided(false)
                    .createEncounterSearchCriteria()));
        }

        return latestEncounterIdsByPatient(candidates, bookedPatientIds(patients));
    }

    private Set<Integer> bookedPatientIds(Collection<Patient> patients) {
        Set<Integer> patientIds = new HashSet<Integer>();
        for (Patient patient : patients) {
            if (patient != null && patient.getId() != null) {
                patientIds.add(patient.getId());
            }
        }
        return patientIds;
    }

    static Map<Integer, Integer> latestEncounterIdsByPatient(
            Collection<Encounter> encounters, Set<Integer> patientIds) {
        Map<Integer, Encounter> latestByPatient = new LinkedHashMap<Integer, Encounter>();
        for (Encounter encounter : encounters) {
            Integer patientId = encounter.getPatient() == null ? null : encounter.getPatient().getId();
            if (encounter.getId() == null || patientId == null || !patientIds.contains(patientId)) {
                continue;
            }
            Encounter current = latestByPatient.get(patientId);
            if (current == null || isLaterEncounter(encounter, current)) {
                latestByPatient.put(patientId, encounter);
            }
        }

        Map<Integer, Integer> encounterIdsByPatient = new LinkedHashMap<Integer, Integer>();
        for (Map.Entry<Integer, Encounter> entry : latestByPatient.entrySet()) {
            encounterIdsByPatient.put(entry.getKey(), entry.getValue().getId());
        }
        return encounterIdsByPatient;
    }

    private static boolean isLaterEncounter(Encounter candidate, Encounter current) {
        Date candidateDate = candidate.getEncounterDatetime();
        Date currentDate = current.getEncounterDatetime();
        if (candidateDate == null) {
            return false;
        }
        if (currentDate == null || candidateDate.after(currentDate)) {
            return true;
        }
        return candidateDate.equals(currentDate) && candidate.getId() > current.getId();
    }
}
