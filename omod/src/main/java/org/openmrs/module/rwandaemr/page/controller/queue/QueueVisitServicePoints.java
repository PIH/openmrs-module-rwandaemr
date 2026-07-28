package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.Visit;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

final class QueueVisitServicePoints {

    private QueueVisitServicePoints() {
    }

    static Map<Integer, List<Location>> mapByEntryId(List<QueueEntry> reportEntries,
                                                     List<QueueEntry> visitEntries) {
        Map<String, List<QueueEntry>> entriesByVisit = new LinkedHashMap<String, List<QueueEntry>>();
        for (QueueEntry entry : nullSafe(visitEntries)) {
            String visitKey = visitKey(entry.getVisit());
            if (visitKey != null) {
                if (!entriesByVisit.containsKey(visitKey)) {
                    entriesByVisit.put(visitKey, new ArrayList<QueueEntry>());
                }
                entriesByVisit.get(visitKey).add(entry);
            }
        }

        Map<Integer, List<Location>> servicePointsByEntryId =
                new LinkedHashMap<Integer, List<Location>>();
        for (QueueEntry reportEntry : nullSafe(reportEntries)) {
            if (reportEntry.getId() == null) {
                continue;
            }
            List<QueueEntry> journeyEntries = entriesByVisit.get(visitKey(reportEntry.getVisit()));
            if (journeyEntries == null || journeyEntries.isEmpty()) {
                journeyEntries = Collections.singletonList(reportEntry);
            }
            LinkedHashMap<String, Location> servicePoints = new LinkedHashMap<String, Location>();
            for (QueueEntry journeyEntry : journeyEntries) {
                add(servicePoints, journeyEntry.getPreviousServicePoint());
                add(servicePoints, journeyEntry.getServicePoint());
            }
            servicePointsByEntryId.put(reportEntry.getId(),
                    new ArrayList<Location>(servicePoints.values()));
        }
        return servicePointsByEntryId;
    }

    private static List<QueueEntry> nullSafe(List<QueueEntry> entries) {
        return entries == null ? Collections.<QueueEntry>emptyList() : entries;
    }

    private static void add(Map<String, Location> servicePoints, Location location) {
        String key = locationKey(location);
        if (key != null && !servicePoints.containsKey(key)) {
            servicePoints.put(key, location);
        }
    }

    private static String visitKey(Visit visit) {
        if (visit == null) {
            return null;
        }
        if (visit.getId() != null) {
            return "id:" + visit.getId();
        }
        return StringUtils.isBlank(visit.getUuid()) ? null : "uuid:" + visit.getUuid();
    }

    private static String locationKey(Location location) {
        if (location == null) {
            return null;
        }
        if (location.getId() != null) {
            return "id:" + location.getId();
        }
        if (StringUtils.isNotBlank(location.getUuid())) {
            return "uuid:" + location.getUuid();
        }
        return StringUtils.isBlank(location.getName()) ? null : "name:" + location.getName();
    }
}
