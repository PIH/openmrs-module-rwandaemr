package org.openmrs.module.rwandaemr.page.controller.queue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.Visit;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.queue.QueuePrivileges;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.FileDownload;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QueueReportsPageController extends QueuePageSupport {

    public Object get(PageModel model,
                      UiSessionContext sessionContext,
                      @SpringBean QueueService queueService,
                      @RequestParam(value = "locationId", required = false) String locationId,
                      @RequestParam(value = "status", required = false) String status,
                      @RequestParam(value = "startDate", required = false) String startDate,
                      @RequestParam(value = "endDate", required = false) String endDate,
                      @RequestParam(value = "export", required = false) String export) {
        if (!org.openmrs.api.context.Context.hasPrivilege(QueuePrivileges.REPORTS)) {
            model.addAttribute("authorized", false);
            return null;
        }
        boolean viewAllLocations = canViewAllLocations();
        List<Location> loginLocations = queueService.getServicePointLocations();
        Location location = resolveLocation(sessionContext, locationId, viewAllLocations, loginLocations);
        QueueStatus selectedStatus = parseStatus(status);
        Date referenceTime = new Date();
        String today = LocalDate.now().toString();
        String selectedStartDate = StringUtils.isBlank(startDate) ? today : startDate;
        String selectedEndDate = StringUtils.isBlank(endDate) ? today : endDate;
        String dateRangeError = null;
        List<QueueEntry> entries = Collections.emptyList();
        try {
            LocalDate rangeStart = LocalDate.parse(selectedStartDate);
            LocalDate rangeEnd = LocalDate.parse(selectedEndDate);
            if (rangeEnd.isBefore(rangeStart)) {
                dateRangeError = "End date must be on or after start date";
            }
            else {
                entries = getEntriesForLocation(queueService, location, selectedStatus,
                        toDate(rangeStart), toDate(rangeEnd));
            }
        }
        catch (DateTimeParseException ignored) {
            dateRangeError = "Start date and end date must be valid dates";
        }
        model.addAttribute("authorized", true);
        model.addAttribute("canViewAllLocations", viewAllLocations);
        model.addAttribute("locations", loginLocations);
        model.addAttribute("selectedLocation", location);
        model.addAttribute("selectedStatus", selectedStatus == null ? "ALL" : selectedStatus.name());
        model.addAttribute("selectedStartDate", selectedStartDate);
        model.addAttribute("selectedEndDate", selectedEndDate);
        model.addAttribute("dateRangeError", dateRangeError);
        model.addAttribute("statuses", QueueStatus.values());
        model.addAttribute("entries", entries);
        Map<Integer, String> waitingTimeByEntryId = QueueWaitingTime.formatByEntryId(entries, referenceTime);
        model.addAttribute("waitingTimeByEntryId", waitingTimeByEntryId);
        if (dateRangeError == null && "excel".equalsIgnoreCase(export)) {
            String filename = "queue-report-" + selectedStartDate + "-to-" + selectedEndDate + ".xls";
            return new FileDownload(filename, QueueReportExcel.CONTENT_TYPE,
                    QueueReportExcel.create(entries, waitingTimeByEntryId));
        }
        Set<Visit> reportVisits = new LinkedHashSet<Visit>();
        for (QueueEntry entry : entries) {
            if (entry.getVisit() != null) {
                reportVisits.add(entry.getVisit());
            }
        }
        model.addAttribute("visitedServicePointsByEntryId", QueueVisitServicePoints.mapByEntryId(
                entries, queueService.getQueueEntriesByVisits(reportVisits)));
        return null;
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Location resolveLocation(UiSessionContext sessionContext, String locationId, boolean viewAllLocations,
                                     List<Location> loginLocations) {
        if (!viewAllLocations || StringUtils.isBlank(locationId)) {
            return sessionContext.getSessionLocation();
        }
        if ("ALL".equalsIgnoreCase(locationId)) {
            return null;
        }
        try {
            Location requestedLocation = findLocation(loginLocations, Integer.valueOf(locationId));
            return requestedLocation == null ? sessionContext.getSessionLocation() : requestedLocation;
        }
        catch (NumberFormatException ignored) {
            return sessionContext.getSessionLocation();
        }
    }

    private Location findLocation(List<Location> locations, Integer locationId) {
        for (Location location : locations) {
            if (locationId.equals(location.getId())) {
                return location;
            }
        }
        return null;
    }
}
