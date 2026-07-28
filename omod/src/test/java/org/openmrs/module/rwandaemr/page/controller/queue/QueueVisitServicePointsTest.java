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

    private QueueEntry entry(int id, Visit visit, Location previous, Location current) {
        QueueEntry entry = new QueueEntry();
        entry.setId(id);
        entry.setVisit(visit);
        entry.setPreviousServicePoint(previous);
        entry.setServicePoint(current);
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
