package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.Visit;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;

public class QueueVisitServicePointsTest {

    @Test
    public void shouldBuildAnOrderedDistinctServicePointJourneyForTheVisit() {
        Visit visit = visit(11);
        Location triage = location(1, "Triage");
        Location consultation = location(2, "Consultation");
        Location laboratory = location(3, "Laboratory");
        QueueEntry reportEntry = entry(21, visit, triage, consultation);
        QueueEntry laboratoryEntry = entry(22, visit, consultation, laboratory);

        Map<Integer, List<Location>> result = QueueVisitServicePoints.mapByEntryId(
                Collections.singletonList(reportEntry), Arrays.asList(reportEntry, laboratoryEntry));

        assertThat(names(result.get(21)), contains("Triage", "Consultation", "Laboratory"));
    }

    @Test
    public void shouldFallBackToTheReportEntryWhenThereIsNoVisit() {
        Location reception = location(1, "Reception");
        QueueEntry reportEntry = entry(21, null, null, reception);

        Map<Integer, List<Location>> result = QueueVisitServicePoints.mapByEntryId(
                Collections.singletonList(reportEntry), Collections.<QueueEntry>emptyList());

        assertThat(result.size(), is(1));
        assertThat(names(result.get(21)), contains("Reception"));
    }

    @Test
    public void shouldMapActiveConcurrentDestinationsToTheOriginalQueueEntry() {
        Visit visit = visit(11);
        Location consultation = location(1, "Consultation");
        Location laboratory = location(2, "Laboratory");
        Location radiology = location(3, "Radiology");
        Location imaging = location(4, "Imaging");
        QueueEntry originalEntry = entry(21, visit, null, consultation, QueueStatus.IN_PROGRESS);
        QueueEntry laboratoryEntry = entry(22, visit, consultation, laboratory, QueueStatus.WAITING);
        QueueEntry radiologyEntry = entry(23, visit, consultation, radiology, QueueStatus.CALLED);
        QueueEntry completedImagingEntry = entry(24, visit, consultation, imaging, QueueStatus.COMPLETED);

        Map<Integer, List<Location>> result = QueueVisitServicePoints.mapConcurrentDestinationsByEntryId(
                Arrays.asList(originalEntry, laboratoryEntry),
                Arrays.asList(originalEntry, laboratoryEntry, radiologyEntry, completedImagingEntry));

        assertThat(names(result.get(21)), contains("Laboratory", "Radiology"));
        assertThat(names(result.get(22)), is(Collections.<String>emptyList()));
    }

    @Test
    public void shouldKeepPreviousServicePointFirstWhenItIsAlsoActive() {
        Location laboratory = location(1, "Laboratory");
        Location outpatient = location(2, "Outpatient Clinic");
        Location radiology = location(3, "Radiology");
        Location pharmacy = location(4, "Pharmacy");
        QueueEntry laboratoryEntry = entry(
                21, visit(11), outpatient, laboratory, QueueStatus.WAITING);
        Map<Integer, List<Location>> concurrentDestinations = Collections.singletonMap(
                laboratoryEntry.getId(), Arrays.asList(outpatient, radiology));

        Map<Integer, List<Location>> result = QueueVisitServicePoints.mapTransferDestinationsByEntryId(
                Collections.singletonList(laboratoryEntry),
                Arrays.asList(laboratory, pharmacy, radiology, outpatient), concurrentDestinations);

        assertThat(names(result.get(21)), contains("Outpatient Clinic", "Pharmacy"));
    }

    private QueueEntry entry(int id, Visit visit, Location previous, Location current) {
        return entry(id, visit, previous, current, QueueStatus.WAITING);
    }

    private QueueEntry entry(int id, Visit visit, Location previous, Location current, QueueStatus status) {
        QueueEntry entry = new QueueEntry();
        entry.setId(id);
        entry.setVisit(visit);
        entry.setPreviousServicePoint(previous);
        entry.setServicePoint(current);
        entry.setStatus(status);
        return entry;
    }

    private Visit visit(int id) {
        Visit visit = new Visit();
        visit.setId(id);
        return visit;
    }

    private Location location(int id, String name) {
        Location location = new Location(id);
        location.setName(name);
        return location;
    }

    private List<String> names(List<Location> locations) {
        return locations.stream().map(Location::getName).collect(Collectors.toList());
    }
}
