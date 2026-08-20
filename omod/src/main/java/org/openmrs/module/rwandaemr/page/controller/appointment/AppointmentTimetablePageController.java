package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.appointment.AppointmentPrivileges;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.module.rwandaemr.appointment.ProviderLicenseUtil;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AppointmentTimetablePageController extends AppointmentPageSupport {

    public void get(PageModel model,
                    @SpringBean FacilityAppointmentService appointmentService,
                    @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                    @RequestParam(value = "startDate", required = false) String startDate,
                    @RequestParam(value = "endDate", required = false) String endDate) {
        if (!Context.hasPrivilege(AppointmentPrivileges.MANAGE_SCHEDULES)) {
            model.addAttribute("authorized", false);
            return;
        }
        LocalDate today = LocalDate.now();
        String selectedStartDate = startDate == null ? today.toString() : startDate;
        String selectedEndDate = endDate == null ? today.plusDays(30).toString() : endDate;
        List<Location> servicePoints = appointmentService.getServicePointLocations();
        List<Provider> licensedProviders = appointmentService.getLicensedProviders();
        Map<Integer, String> providerLicenses = new LinkedHashMap<Integer, String>();
        for (Provider provider : licensedProviders) {
            providerLicenses.put(provider.getId(), ProviderLicenseUtil.getLicense(provider));
        }
        Location selectedServicePoint = findServicePoint(servicePoints, servicePointId);
        List<AppointmentScheduleSummary> summaries = Collections.emptyList();
        String dateRangeError = null;
        try {
            LocalDate rangeStart = parseDate(selectedStartDate, today);
            LocalDate rangeEnd = parseDate(selectedEndDate, today.plusDays(30));
            if (rangeEnd.isBefore(rangeStart)) {
                throw new IllegalArgumentException("End date must be on or after start date");
            }
            summaries = appointmentService.getScheduleSummaries(
                    selectedServicePoint, toDate(rangeStart), toDate(rangeEnd), true);
        }
        catch (IllegalArgumentException e) {
            dateRangeError = e.getMessage();
        }
        model.addAttribute("authorized", true);
        model.addAttribute("servicePoints", servicePoints);
        model.addAttribute("licensedProviders", licensedProviders);
        model.addAttribute("providerLicenses", providerLicenses);
        model.addAttribute("selectedServicePoint", selectedServicePoint);
        model.addAttribute("selectedStartDate", selectedStartDate);
        model.addAttribute("selectedEndDate", selectedEndDate);
        model.addAttribute("today", today.toString());
        model.addAttribute("todayDate", toDate(today));
        model.addAttribute("dateRangeError", dateRangeError);
        model.addAttribute("scheduleSummaries", summaries);
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean FacilityAppointmentService appointmentService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "scheduleId", required = false) Integer scheduleId,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId,
                       @RequestParam(value = "scheduleDate", required = false) String scheduleDate,
                       @RequestParam(value = "maximumPatients", required = false) Integer maximumPatients,
                       @RequestParam(value = "providerId", required = false) Integer providerId,
                       @RequestParam(value = "notes", required = false) String notes,
                       @RequestParam(value = "active", required = false) Boolean active,
                       @RequestParam(value = "startDate", required = false) String startDate,
                       @RequestParam(value = "endDate", required = false) String endDate) {
        try {
            if ("toggle".equals(action)) {
                appointmentService.setScheduleActive(scheduleId, Boolean.TRUE.equals(active));
                setToast(sessionContext, Boolean.TRUE.equals(active)
                        ? "Appointment schedule opened" : "Appointment schedule closed");
            } else if ("capacity".equals(action)) {
                if (maximumPatients == null) {
                    throw new IllegalArgumentException("Maximum patients is required");
                }
                appointmentService.updateScheduleCapacity(scheduleId, maximumPatients);
                setToast(sessionContext, "Appointment capacity updated");
            } else if ("save".equals(action)) {
                Location servicePoint = Context.getLocationService().getLocation(servicePointId);
                Provider provider = providerId == null
                        ? null : Context.getProviderService().getProvider(providerId);
                if (providerId != null && provider == null) {
                    throw new IllegalArgumentException("Selected provider was not found");
                }
                if (maximumPatients == null) {
                    throw new IllegalArgumentException("Maximum patients is required");
                }
                appointmentService.saveSchedule(
                        servicePoint, toDate(parseDate(scheduleDate, null)), maximumPatients, provider, notes);
                setToast(sessionContext, "Appointment timetable saved");
            } else {
                throw new IllegalArgumentException("Unsupported timetable action");
            }
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return redirect(ui, "appointment/appointmentTimetable",
                "servicePointId", servicePointId, "startDate", startDate, "endDate", endDate);
    }
}
