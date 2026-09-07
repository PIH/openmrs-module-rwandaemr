package org.openmrs.module.rwandaemr.queue.dao;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.module.rwandaemr.queue.QueueAssignmentFilter;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;

public interface QueueDao {

    QueueServicePointConceptMap saveServicePointConceptMap(QueueServicePointConceptMap conceptMap);

    QueueServicePointConceptMap getServicePointConceptMapByUuid(String uuid);

    QueueServicePointConceptMap getServicePointConceptMapByConcept(Concept concept);

    List<QueueServicePointConceptMap> getAllServicePointConceptMaps(boolean includeInactive);

    QueueEntry saveQueueEntry(QueueEntry queueEntry);

    QueueEntry getQueueEntry(Integer id);

    QueueEntry getQueueEntryByUuid(String uuid);

    QueueEntry getActiveQueueEntry(Patient patient, Location servicePoint, Date startOfDay, Date endOfDay,
                                   List<QueueStatus> activeStatuses);

    QueueEntry getActiveQueueEntry(Patient patient, Date startOfDay, Date endOfDay,
                                   List<QueueStatus> activeStatuses);

    int countQueueEntries(Location servicePoint, Date startOfDay, Date endOfDay);

    List<QueueEntry> getQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay);

    int countQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay,
                          Date currentDayStart, List<QueueStatus> activeStatuses, String patientName,
                          QueueAssignmentFilter assignmentFilter, Collection<Integer> currentProviderIds);

    List<QueueEntry> getQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay,
                                     Date currentDayStart, List<QueueStatus> activeStatuses,
                                     String patientName, QueueAssignmentFilter assignmentFilter,
                                     Collection<Integer> currentProviderIds, int firstResult, int maxResults);

    List<QueueEntry> getQueueEntriesByServicePoint(Location servicePoint, Location visibleLocation,
                                                   QueueStatus status, Date startOfDay, Date endOfDay);

    List<QueueEntry> getQueueEntriesByVisit(Visit visit);

    List<QueueEntry> getQueueEntriesByVisits(Collection<Visit> visits);

    QueueStatusHistory saveQueueStatusHistory(QueueStatusHistory history);

    List<QueueStatusHistory> getQueueStatusHistory(QueueEntry queueEntry);
}
