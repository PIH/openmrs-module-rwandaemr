package org.openmrs.module.rwandaemr.queue;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;

public interface QueueService extends OpenmrsService {

    QueueEntry addPatientToQueueFromRegistration(Encounter encounter);

    Location resolveServicePointFromServiceRequestedConcept(Concept concept);

    QueueEntry getActiveQueueEntry(Patient patient, Location servicePoint, Date date);

    QueueEntry getActiveQueueEntry(Patient patient, Date date);

    QueueEntry getActiveQueueEntry(Visit visit);

    String generateQueueNumber(Location servicePoint, Date date);

    List<QueueEntry> getQueueEntriesForCurrentSessionLocation(QueueStatus status, Date date);

    List<QueueEntry> getQueueEntriesByLocation(Location location, QueueStatus status, Date date);

    List<QueueEntry> getQueueEntriesByLocation(Location location, QueueStatus status, Date startDate, Date endDate);

    List<QueueEntry> getQueueEntriesByServicePoint(Location servicePoint, Location visibleLocation,
                                                   QueueStatus status, Date date);

    List<QueueEntry> getQueueEntriesByVisits(Collection<Visit> visits);

    QueueEntry callNextPatient(Location servicePoint, Location location);

    QueueEntry callPatient(QueueEntry queueEntry);

    QueueEntry callPatientAfterVitals(QueueEntry queueEntry, Encounter vitalsEncounter);

    QueueEntry startService(QueueEntry queueEntry);

    QueueEntry completeService(QueueEntry queueEntry);

    QueueEntry transferPatient(QueueEntry queueEntry, Location destinationServicePoint, String reason);

    QueueEntry markPatientTransferred(QueueEntry queueEntry, String reason);

    QueueEntry putOnHold(QueueEntry queueEntry, String reason);

    QueueEntry cancelQueueEntry(QueueEntry queueEntry, String reason);

    QueueEntry updatePriority(QueueEntry queueEntry, QueuePriority priority);

    QueueStatusHistory createQueueStatusHistory(QueueEntry queueEntry, QueueStatus previousStatus,
                                                QueueStatus newStatus, String reason);

    QueueEntry getQueueEntry(Integer id);

    QueueEntry getQueueEntryByUuid(String uuid);

    List<QueueStatusHistory> getQueueStatusHistory(QueueEntry queueEntry);

    List<Location> getServicePointLocations();

    QueueServicePointConceptMap saveServicePointConceptMap(QueueServicePointConceptMap conceptMap);

    QueueServicePointConceptMap getServicePointConceptMapByUuid(String uuid);

    List<QueueServicePointConceptMap> getAllServicePointConceptMaps(boolean includeInactive);
}
