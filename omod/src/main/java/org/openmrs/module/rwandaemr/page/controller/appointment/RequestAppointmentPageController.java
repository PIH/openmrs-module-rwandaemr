package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.appointment.AppointmentPrivileges;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.AppointmentStatus;
import org.openmrs.module.rwandaemr.appointment.AppointmentVisitType;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RequestAppointmentPageController extends AppointmentPageSupport {

    public void get(PageModel model,
                    @SpringBean FacilityAppointmentService appointmentService,
                    @RequestParam(value = "patientId", required = false) String patientId,
                    @RequestParam(value = "servicePointId", required = false) Integer servicePointId) {
        if (!Context.hasPrivilege(AppointmentPrivileges.BOOK)) {
            model.addAttribute("authorized", false);
            return;
        }
        Patient patient = findPatient(patientId);
        List<Location> servicePoints = appointmentService.getServicePointLocations();
        Location selectedServicePoint = findServicePoint(servicePoints, servicePointId);
        LocalDate today = LocalDate.now();
        List<AppointmentScheduleSummary> availableSchedules = selectedServicePoint == null
                ? Collections.<AppointmentScheduleSummary>emptyList()
                : appointmentService.getAvailableSchedules(
                        selectedServicePoint, toDate(today), toDate(today.plusDays(90)));
        List<AppointmentBooking> patientBookings = patient == null
                ? Collections.<AppointmentBooking>emptyList()
                : appointmentService.getPatientBookings(patient, toDate(today), null);
        Set<Integer> bookedScheduleIds = new HashSet<Integer>();
        for (AppointmentBooking booking : patientBookings) {
            if (booking.getSchedule() != null) {
                bookedScheduleIds.add(booking.getSchedule().getId());
            }
        }
        Map<Integer, List<AppointmentScheduleSummary>> postponeSchedulesByBookingId =
                new LinkedHashMap<Integer, List<AppointmentScheduleSummary>>();
        for (AppointmentBooking booking : patientBookings) {
            if ((AppointmentStatus.REQUESTED.equals(booking.getStatus())
                    || AppointmentStatus.CONFIRMED.equals(booking.getStatus()))
                    && booking.getSchedule() != null && booking.getSchedule().getServicePoint() != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(booking.getSchedule().getScheduleDate());
                LocalDate currentDate = LocalDate.of(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH) + 1,
                        calendar.get(Calendar.DAY_OF_MONTH));
                List<AppointmentScheduleSummary> availablePostponeSchedules =
                        appointmentService.getAvailableSchedules(
                        booking.getSchedule().getServicePoint(),
                        toDate(today),
                        toDate(currentDate.plusDays(90)));
                List<AppointmentScheduleSummary> postponeSchedules =
                        new ArrayList<AppointmentScheduleSummary>();
                for (AppointmentScheduleSummary summary : availablePostponeSchedules) {
                    if (!bookedScheduleIds.contains(summary.getSchedule().getId())) {
                        postponeSchedules.add(summary);
                    }
                }
                postponeSchedulesByBookingId.put(booking.getId(), postponeSchedules);
            }
        }
        model.addAttribute("authorized", true);
        model.addAttribute("patient", patient);
        model.addAttribute("patientId", patientId);
        model.addAttribute("servicePoints", servicePoints);
        model.addAttribute("selectedServicePoint", selectedServicePoint);
        model.addAttribute("availableSchedules", availableSchedules);
        model.addAttribute("patientBookings", patientBookings);
        model.addAttribute("postponeSchedulesByBookingId", postponeSchedulesByBookingId);
        model.addAttribute("programs", Context.getProgramWorkflowService().getAllPrograms(false));
        model.addAttribute("visitTypes", AppointmentVisitType.values());
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean FacilityAppointmentService appointmentService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "patientId", required = false) String patientId,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                       @RequestParam(value = "scheduleId", required = false) Integer scheduleId,
                       @RequestParam(value = "newScheduleId", required = false) Integer newScheduleId,
                       @RequestParam(value = "bookingId", required = false) Integer bookingId,
                       @RequestParam(value = "programId", required = false) Integer programId,
                       @RequestParam(value = "visitType", required = false) String visitType,
                       @RequestParam(value = "notes", required = false) String notes) {
        try {
            if ("cancel".equals(action)) {
                appointmentService.cancelBooking(bookingId);
                setToast(sessionContext, "Appointment cancelled");
            } else if ("reschedule".equals(action) || "postpone".equals(action)) {
                AppointmentBooking booking = appointmentService.postponeBooking(bookingId, newScheduleId);
                AppointmentSchedule schedule = booking.getSchedule();
                servicePointId = schedule == null || schedule.getServicePoint() == null
                        ? servicePointId : schedule.getServicePoint().getId();
                setToast(sessionContext, "Appointment rescheduled");
            } else if ("request".equals(action)) {
                Patient patient = findPatient(patientId);
                if (patient == null) {
                    throw new IllegalArgumentException("Patient was not found");
                }
                Program program = programId == null
                        ? null : Context.getProgramWorkflowService().getProgram(programId);
                if (programId != null && (program == null || Boolean.TRUE.equals(program.getRetired()))) {
                    throw new IllegalArgumentException("Program must be active");
                }
                AppointmentVisitType appointmentVisitType = StringUtils.isBlank(visitType)
                        ? null : AppointmentVisitType.valueOf(visitType);
                AppointmentBooking booking = appointmentService.requestAppointment(
                        patient, scheduleId, program, appointmentVisitType, notes);
                AppointmentSchedule schedule = booking.getSchedule();
                servicePointId = schedule == null || schedule.getServicePoint() == null
                        ? servicePointId : schedule.getServicePoint().getId();
                setToast(sessionContext, "Appointment confirmed");
            } else {
                throw new IllegalArgumentException("Unsupported appointment action");
            }
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return redirect(ui, "appointment/requestAppointment",
                "patientId", patientId, "servicePointId", servicePointId);
    }
}
