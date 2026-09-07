package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.Visit;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

final class QueueVisitServicePoints {

    private static final Set<QueueStatus> ACTIVE_STATUSES = EnumSet.of(
            QueueStatus.WAITING,
            QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS,
            QueueStatus.ON_HOLD);

    private QueueVisitServicePoints() {
    }

    static Map<Integer, List<Location>> mapByEntryId(List<QueueEntry> reportEntries,
                                                     List<QueueEntry> visitEntries) {
        Map<String, List<QueueEntry>> entriesByVisit = groupByVisit(visitEntries);

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

    static Map<Integer, List<Location>> mapConcurrentDestinationsByEntryId(List<QueueEntry> displayedEntries,
                                                                           List<QueueEntry> visitEntries) {
        Map<String, List<QueueEntry>> entriesByVisit = groupByVisit(visitEntries);
        Map<Integer, List<Location>> destinationsByEntryId = new LinkedHashMap<Integer, List<Location>>();
        for (QueueEntry displayedEntry : nullSafe(displayedEntries)) {
            if (displayedEntry.getId() == null) {
                continue;
            }
            LinkedHashMap<String, Location> destinations = new LinkedHashMap<String, Location>();
            String sourceServicePointKey = locationKey(displayedEntry.getServicePoint());
            List<QueueEntry> relatedEntries = entriesByVisit.get(visitKey(displayedEntry.getVisit()));
            if (sourceServicePointKey != null && relatedEntries != null) {
                for (QueueEntry relatedEntry : relatedEntries) {
                    if (!isSameEntry(displayedEntry, relatedEntry)
                            && ACTIVE_STATUSES.contains(relatedEntry.getStatus())
                            && sourceServicePointKey.equals(locationKey(relatedEntry.getPreviousServicePoint()))) {
                        add(destinations, relatedEntry.getServicePoint());
                    }
                }
            }
            destinationsByEntryId.put(displayedEntry.getId(), new ArrayList<Location>(destinations.values()));
        }
        return destinationsByEntryId;
    }

    static Map<Integer, List<Location>> mapTransferDestinationsByEntryId(
            List<QueueEntry> displayedEntries, List<Location> servicePoints,
            Map<Integer, List<Location>> concurrentDestinationsByEntryId) {
        Map<Integer, List<Location>> destinationsByEntryId = new LinkedHashMap<Integer, List<Location>>();
        for (QueueEntry displayedEntry : nullSafe(displayedEntries)) {
            if (displayedEntry.getId() == null) {
                continue;
            }
            String currentServicePointKey = locationKey(displayedEntry.getServicePoint());
            String previousServicePointKey = locationKey(displayedEntry.getPreviousServicePoint());
            Set<String> concurrentDestinationKeys = locationKeys(
                    concurrentDestinationsByEntryId == null
                            ? null : concurrentDestinationsByEntryId.get(displayedEntry.getId()));
            LinkedHashMap<String, Location> destinations = new LinkedHashMap<String, Location>();

            for (Location servicePoint : nullSafeLocations(servicePoints)) {
                String servicePointKey = locationKey(servicePoint);
                if (servicePointKey != null && servicePointKey.equals(previousServicePointKey)
                        && !servicePointKey.equals(currentServicePointKey)) {
                    add(destinations, servicePoint);
                    break;
                }
            }
            for (Location servicePoint : nullSafeLocations(servicePoints)) {
                String servicePointKey = locationKey(servicePoint);
                if (servicePointKey == null || servicePointKey.equals(currentServicePointKey)
                        || concurrentDestinationKeys.contains(servicePointKey)) {
                    continue;
                }
                add(destinations, servicePoint);
            }
            destinationsByEntryId.put(displayedEntry.getId(), new ArrayList<Location>(destinations.values()));
        }
        return destinationsByEntryId;
    }

    private static Map<String, List<QueueEntry>> groupByVisit(List<QueueEntry> entries) {
        Map<String, List<QueueEntry>> entriesByVisit = new LinkedHashMap<String, List<QueueEntry>>();
        for (QueueEntry entry : nullSafe(entries)) {
            String visitKey = visitKey(entry.getVisit());
            if (visitKey != null) {
                if (!entriesByVisit.containsKey(visitKey)) {
                    entriesByVisit.put(visitKey, new ArrayList<QueueEntry>());
                }
                entriesByVisit.get(visitKey).add(entry);
            }
        }
        return entriesByVisit;
    }

    private static boolean isSameEntry(QueueEntry first, QueueEntry second) {
        return first == second || first.getId() != null && first.getId().equals(second.getId());
    }

    private static List<QueueEntry> nullSafe(List<QueueEntry> entries) {
        return entries == null ? Collections.<QueueEntry>emptyList() : entries;
    }

    private static List<Location> nullSafeLocations(List<Location> locations) {
        return locations == null ? Collections.<Location>emptyList() : locations;
    }

    private static Set<String> locationKeys(List<Location> locations) {
        Set<String> keys = new LinkedHashSet<String>();
        for (Location location : nullSafeLocations(locations)) {
            String key = locationKey(location);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
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
