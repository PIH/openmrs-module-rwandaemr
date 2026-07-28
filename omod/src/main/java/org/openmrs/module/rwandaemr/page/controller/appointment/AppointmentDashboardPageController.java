package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.appointment.AppointmentPrivileges;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.AppointmentStatus;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AppointmentDashboardPageController extends AppointmentPageSupport {

    public void get(PageModel model,
                    @SpringBean FacilityAppointmentService appointmentService,
                    @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                    @RequestParam(value = "startDate", required = false) String startDate,
                    @RequestParam(value = "endDate", required = false) String endDate) {
        if (!Context.hasPrivilege(AppointmentPrivileges.VIEW)) {
            model.addAttribute("authorized", false);
            return;
        }
        LocalDate today = LocalDate.now();
        String selectedStartDate = StringUtils.isBlank(startDate) ? today.toString() : startDate;
        String selectedEndDate = StringUtils.isBlank(endDate) ? today.toString() : endDate;
        List<Location> servicePoints = appointmentService.getServicePointLocations();
        Location selectedServicePoint = findServicePoint(servicePoints, servicePointId);
        List<AppointmentScheduleSummary> summaries = Collections.emptyList();
        Map<Integer, List<AppointmentBooking>> bookingsByScheduleId =
                new LinkedHashMap<Integer, List<AppointmentBooking>>();
        String dateRangeError = null;
        int totalCapacity = 0;
        int totalBooked = 0;
        try {
            LocalDate rangeStart = parseDate(selectedStartDate, today);
            LocalDate rangeEnd = parseDate(selectedEndDate, today);
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
                totalCapacity += schedule.getMaximumPatients() == null ? 0 : schedule.getMaximumPatients();
                totalBooked += summary.getBookedPatients();
            }
            for (AppointmentBooking booking : appointmentService.getBookings(schedules)) {
                Integer scheduleId = booking.getSchedule().getId();
                if (bookingsByScheduleId.containsKey(scheduleId)) {
                    bookingsByScheduleId.get(scheduleId).add(booking);
                }
            }
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
        model.addAttribute("totalCapacity", totalCapacity);
        model.addAttribute("totalBooked", totalBooked);
        model.addAttribute("totalRemaining", Math.max(0, totalCapacity - totalBooked));
        model.addAttribute("appointmentStatuses", AppointmentStatus.values());
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean FacilityAppointmentService appointmentService,
                       @RequestParam(value = "bookingId", required = false) Integer bookingId,
                       @RequestParam(value = "appointmentStatus", required = false) String appointmentStatus,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                       @RequestParam(value = "startDate", required = false) String startDate,
                       @RequestParam(value = "endDate", required = false) String endDate) {
        try {
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
}
