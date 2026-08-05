package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QueueDashboardPageController extends QueuePageSupport {

    static final int DEFAULT_PAGE_SIZE = 10;

    static final List<Integer> PAGE_SIZE_OPTIONS = Collections.unmodifiableList(
            Arrays.asList(10, 20, 40, 60, 80, 100));

    public void get(PageModel model,
                    UiSessionContext sessionContext,
                    @SpringBean QueueService queueService,
                    @SpringBean("encounterService") EncounterService encounterService,
                    @RequestParam(value = "locationId", required = false) Integer locationId,
                    @RequestParam(value = "status", required = false) String status,
                    @RequestParam(value = "page", required = false) Integer page,
                    @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        if (!canViewQueue()) {
            model.addAttribute("authorized", false);
            return;
        }
        List<Location> loginLocations = queueService.getServicePointLocations();
        boolean viewAllLocations = canViewAllLocations();
        Location location = sessionContext.getSessionLocation();
        if (viewAllLocations && locationId != null) {
            Location requestedLocation = findLoginLocation(loginLocations, locationId);
            if (requestedLocation != null) {
                location = requestedLocation;
                if (!requestedLocation.equals(sessionContext.getSessionLocation())) {
                    sessionContext.setSessionLocation(requestedLocation);
                }
            }
        }
        QueueStatus selectedStatus = parseStatus(status);
        Date referenceTime = new Date();
        List<QueueEntry> allEntries = getEntriesForLocation(queueService, location, selectedStatus, referenceTime);
        int selectedPageSize = normalizePageSize(pageSize);
        int totalEntries = allEntries.size();
        int totalPages = calculateTotalPages(totalEntries, selectedPageSize);
        int currentPage = normalizePage(page, totalPages);
        int firstEntryIndex = (currentPage - 1) * selectedPageSize;
        int lastEntryIndex = Math.min(firstEntryIndex + selectedPageSize, totalEntries);
        List<QueueEntry> entries = new ArrayList<QueueEntry>(
                allEntries.subList(firstEntryIndex, lastEntryIndex));
        model.addAttribute("authorized", true);
        model.addAttribute("canViewAllLocations", viewAllLocations);
        model.addAttribute("canManageQueue", canManageQueue());
        model.addAttribute("canTransferPatient", canTransferPatient());
        model.addAttribute("locations", loginLocations);
        model.addAttribute("selectedLocation", location);
        model.addAttribute("selectedStatus", selectedStatus == null ? "ALL" : selectedStatus.name());
        model.addAttribute("statuses", QueueStatus.values());
        model.addAttribute("priorities", QueuePriority.values());
        model.addAttribute("servicePoints", loginLocations);
        model.addAttribute("entries", entries);
        model.addAttribute("totalEntries", totalEntries);
        model.addAttribute("pageStart", totalEntries == 0 ? 0 : firstEntryIndex + 1);
        model.addAttribute("pageEnd", lastEntryIndex);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", selectedPageSize);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        model.addAttribute("pageNumbers", buildPageNumbers(currentPage, totalPages));
        model.addAttribute("waitingTimeByEntryId", QueueWaitingTime.formatByEntryId(entries, referenceTime));
        model.addAttribute("latestVitalsByEntryId", getLatestVitalsByEntryId(entries, encounterService));
        Set<Visit> displayedVisits = new LinkedHashSet<Visit>();
        for (QueueEntry entry : entries) {
            if (entry.getVisit() != null) {
                displayedVisits.add(entry.getVisit());
            }
        }
        model.addAttribute("concurrentServicePointsByEntryId",
                QueueVisitServicePoints.mapConcurrentDestinationsByEntryId(
                        entries, queueService.getQueueEntriesByVisits(displayedVisits)));
    }

    private Location findLoginLocation(List<Location> loginLocations, Integer locationId) {
        for (Location loginLocation : loginLocations) {
            if (locationId.equals(loginLocation.getId())) {
                return loginLocation;
            }
        }
        return null;
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean QueueService queueService,
                       @SpringBean("encounterService") EncounterService encounterService,
                       @RequestParam(value = "action", required = false) String action,
                       @RequestParam(value = "entryId", required = false) Integer entryId,
                       @RequestParam(value = "encounterId", required = false) String encounterId,
                       @RequestParam(value = "locationId", required = false) Integer locationId,
                       @RequestParam(value = "destinationServicePointId", required = false) Integer destinationServicePointId,
                       @RequestParam(value = "priority", required = false) String priority,
                       @RequestParam(value = "reason", required = false) String reason,
                       @RequestParam(value = "status", required = false) String status,
                       @RequestParam(value = "page", required = false) Integer page,
                       @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        try {
            if ("openDashboard".equals(action)) {
                return openPatientDashboard(ui, queueService, entryId);
            }
            if ("vitalsSaved".equals(action)) {
                Encounter vitalsEncounter = getEncounter(encounterService, encounterId);
                if (vitalsEncounter == null) {
                    throw new IllegalArgumentException("Saved vitals encounter was not found");
                }
                queueService.callPatientAfterVitals(queueService.getQueueEntry(entryId), vitalsEncounter);
                setToast(sessionContext, "Vitals saved. Patient status changed to Called");
                return dashboardRedirect(ui, locationId, status, page, pageSize);
            }
            if (!"transfer".equals(action) && !"updatePriority".equals(action)) {
                throw new IllegalArgumentException("Unsupported queue action: " + action);
            }
            processEntryAction(queueService, action, entryId, destinationServicePointId, priority, reason);
            setToast(sessionContext, "Queue updated");
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return dashboardRedirect(ui, locationId, status, page, pageSize);
    }

    private String dashboardRedirect(UiUtils ui, Integer locationId, String status,
                                     Integer page, Integer pageSize) {
        return redirect(ui, "queue/queueDashboard", "locationId", locationId, "status", status,
                "page", page, "pageSize", normalizePageSize(pageSize));
    }

    static int normalizePageSize(Integer requestedPageSize) {
        return requestedPageSize != null && PAGE_SIZE_OPTIONS.contains(requestedPageSize)
                ? requestedPageSize : DEFAULT_PAGE_SIZE;
    }

    static int calculateTotalPages(int totalEntries, int pageSize) {
        return Math.max(1, (totalEntries + pageSize - 1) / pageSize);
    }

    static int normalizePage(Integer requestedPage, int totalPages) {
        int page = requestedPage == null ? 1 : requestedPage;
        return Math.max(1, Math.min(page, totalPages));
    }

    static List<Integer> buildPageNumbers(int currentPage, int totalPages) {
        int firstPage = Math.max(1, currentPage - 2);
        int lastPage = Math.min(totalPages, firstPage + 4);
        firstPage = Math.max(1, lastPage - 4);
        List<Integer> pageNumbers = new ArrayList<Integer>();
        for (int page = firstPage; page <= lastPage; page++) {
            pageNumbers.add(page);
        }
        return pageNumbers;
    }

    private Encounter getEncounter(EncounterService encounterService, String encounterId) {
        if (StringUtils.isBlank(encounterId)) {
            return null;
        }
        try {
            return encounterService.getEncounter(Integer.valueOf(encounterId));
        }
        catch (NumberFormatException ignored) {
            return encounterService.getEncounterByUuid(encounterId);
        }
    }

    private Map<Integer, LatestVitalsSummary> getLatestVitalsByEntryId(List<QueueEntry> entries,
                                                                       EncounterService encounterService) {
        Map<Integer, LatestVitalsSummary> summariesByEntryId = new LinkedHashMap<Integer, LatestVitalsSummary>();
        Map<Integer, Visit> visitsById = new LinkedHashMap<Integer, Visit>();
        for (QueueEntry entry : entries) {
            Visit visit = entry.getVisit();
            if (visit != null && visit.getId() != null) {
                visitsById.put(visit.getId(), visit);
            }
        }
        EncounterType vitalsEncounterType = encounterService.getEncounterTypeByUuid(
                LatestVitalsSummary.VITALS_ENCOUNTER_TYPE_UUID);
        if (visitsById.isEmpty() || vitalsEncounterType == null) {
            return summariesByEntryId;
        }

        List<Encounter> vitalsEncounters = encounterService.getEncounters(new EncounterSearchCriteriaBuilder()
                .setEncounterTypes(Collections.singletonList(vitalsEncounterType))
                .setVisits(visitsById.values())
                .setIncludeVoided(false)
                .createEncounterSearchCriteria());
        Map<Integer, List<Encounter>> encountersByVisitId = new HashMap<Integer, List<Encounter>>();
        for (Encounter encounter : vitalsEncounters) {
            if (encounter.getVisit() == null || encounter.getVisit().getId() == null) {
                continue;
            }
            Integer visitId = encounter.getVisit().getId();
            if (!encountersByVisitId.containsKey(visitId)) {
                encountersByVisitId.put(visitId, new ArrayList<Encounter>());
            }
            encountersByVisitId.get(visitId).add(encounter);
        }

        Map<Integer, LatestVitalsSummary> summariesByVisitId = new HashMap<Integer, LatestVitalsSummary>();
        for (Map.Entry<Integer, List<Encounter>> visitEncounters : encountersByVisitId.entrySet()) {
            summariesByVisitId.put(visitEncounters.getKey(),
                    LatestVitalsSummary.from(visitEncounters.getValue(), Context.getLocale()));
        }
        for (QueueEntry entry : entries) {
            if (entry.getId() != null && entry.getVisit() != null) {
                LatestVitalsSummary summary = summariesByVisitId.get(entry.getVisit().getId());
                if (summary != null) {
                    summariesByEntryId.put(entry.getId(), summary);
                }
            }
        }
        return summariesByEntryId;
    }
}
