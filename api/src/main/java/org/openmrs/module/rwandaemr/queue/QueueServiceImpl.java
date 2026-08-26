package org.openmrs.module.rwandaemr.queue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.annotation.Authorized;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.rwandaemr.LocationTagUtil;
import org.openmrs.module.rwandaemr.queue.dao.QueueDao;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class QueueServiceImpl extends BaseOpenmrsService implements QueueService {

    private static final String SERVICE_REQUESTED_CONCEPT_GP = "registration.serviceRequestedConcept";

    private static final String DEFAULT_SERVICE_REQUESTED_CONCEPT = "6702";

    private static final String LABORATORY_SERVICE_POINT_GP = "rwandaemr.queue.laboratoryServicePoint";

    private static final List<QueueStatus> ACTIVE_STATUSES = Arrays.asList(
            QueueStatus.WAITING,
            QueueStatus.CALLED,
            QueueStatus.IN_PROGRESS,
            QueueStatus.ON_HOLD);

    protected Log log = LogFactory.getLog(getClass());

    @Setter
    private QueueDao dao;

    @Setter
    private LocationTagUtil locationTagUtil;

    @Setter
    private QueueWebhookNotifier webhookNotifier;

    @Override
    @Transactional
    public QueueEntry addPatientToQueueFromRegistration(Encounter encounter) {
        try {
            if (encounter == null) {
                log.warn("Skipping queue creation because registration encounter is null");
                return null;
            }
            if (encounter.getPatient() == null) {
                log.warn("Skipping queue creation because registration encounter has no patient: " + encounter.getUuid());
                return null;
            }

            Obs serviceRequestedObs = getServiceRequestedObs(encounter);
            if (serviceRequestedObs == null || serviceRequestedObs.getValueCoded() == null) {
                log.info("Skipping queue creation because Service Requested is missing for encounter " + encounter.getUuid());
                return null;
            }

            Concept serviceRequested = serviceRequestedObs.getValueCoded();
            Location servicePoint = resolveServicePointForRegistration(encounter, serviceRequested);
            if (servicePoint == null) {
                log.warn("Skipping queue creation because registration has no Login Location service point from encounter location or Service Requested mapping: "
                        + serviceRequested.getUuid());
                return null;
            }

            Date now = new Date();
            QueueEntry activeEntry = getActiveQueueEntry(encounter.getPatient(), servicePoint, now);
            if (activeEntry != null) {
                log.info("Skipping duplicate queue entry for patient " + encounter.getPatient().getUuid()
                        + ", service point " + servicePoint.getName() + ". Existing queue entry: "
                        + activeEntry.getUuid());
                return activeEntry;
            }

            QueueEntry queueEntry = new QueueEntry();
            queueEntry.setUuid(UUID.randomUUID().toString());
            queueEntry.setPatient(encounter.getPatient());
            queueEntry.setVisit(encounter.getVisit());
            queueEntry.setEncounter(encounter);
            queueEntry.setEncounterLocation(encounter.getLocation());
            queueEntry.setSessionLocation(getCurrentSessionLocation());
            queueEntry.setServicePoint(servicePoint);
            queueEntry.setServiceRequestedConcept(serviceRequested);
            queueEntry.setPriority(QueuePriority.NORMAL);
            queueEntry.setStatus(QueueStatus.WAITING);
            queueEntry.setArrivalTime(now);
            queueEntry.setQueueNumber(generateQueueNumber(servicePoint, now));
            setCreationMetadata(queueEntry, now);
            dao.saveQueueEntry(queueEntry);
            createQueueStatusHistory(queueEntry, null, QueueStatus.WAITING, "Created from registration");
            notifyWebhook(queueEntry, "CREATED");
            log.info("Created queue entry " + queueEntry.getQueueNumber() + " for patient "
                    + encounter.getPatient().getUuid() + " at " + servicePoint.getName());
            return queueEntry;
        }
        catch (RuntimeException e) {
            log.error("Queue creation from registration failed for encounter "
                    + (encounter == null ? "null" : encounter.getUuid()), e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Location resolveServicePointFromServiceRequestedConcept(Concept concept) {
        QueueServicePointConceptMap conceptMap = dao.getServicePointConceptMapByConcept(concept);
        if (conceptMap == null || conceptMap.getServicePoint() == null) {
            return null;
        }
        Location servicePoint = conceptMap.getServicePoint();
        if (!isLoginLocation(servicePoint)) {
            log.warn("Queue service point mapping points to a location that is not tagged as Login Location: "
                    + servicePoint.getName());
            return null;
        }
        return servicePoint;
    }

    @Override
    @Transactional(readOnly = true)
    public QueueEntry getActiveQueueEntry(Patient patient, Location servicePoint, Date date) {
        return dao.getActiveQueueEntry(patient, servicePoint, startOfDay(date), endOfDay(date), ACTIVE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueEntry getActiveQueueEntry(Patient patient, Date date) {
        return dao.getActiveQueueEntry(patient, startOfDay(date), endOfDay(date), ACTIVE_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueEntry getActiveQueueEntry(Visit visit) {
        if (visit == null) {
            return null;
        }
        QueueEntry latest = null;
        for (QueueEntry entry : dao.getQueueEntriesByVisit(visit)) {
            if (ACTIVE_STATUSES.contains(entry.getStatus())
                    && (latest == null || isAfter(entry.getArrivalTime(), latest.getArrivalTime()))) {
                latest = entry;
            }
        }
        return latest;
    }

    @Override
    @Transactional(readOnly = true)
    public String generateQueueNumber(Location servicePoint, Date date) {
        int next = dao.countQueueEntries(servicePoint, startOfDay(date), endOfDay(date)) + 1;
        String code = servicePoint == null ? "Q" : StringUtils.defaultString(servicePoint.getName());
        code = code.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ENGLISH);
        if (code.length() > 4) {
            code = code.substring(0, 4);
        }
        if (StringUtils.isBlank(code)) {
            code = "Q";
        }
        return String.format("%03d", next) + "-" + code;
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueEntry> getQueueEntriesForCurrentSessionLocation(QueueStatus status, Date date) {
        Location location = getCurrentSessionLocation();
        if (location == null) {
            return Collections.emptyList();
        }
        return getQueueEntriesByLocation(location, status, date);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueEntry> getQueueEntriesByLocation(Location location, QueueStatus status, Date date) {
        return getQueueEntriesByLocation(location, status, date, date);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueEntry> getQueueEntriesByLocation(Location location, QueueStatus status, Date startDate,
                                                       Date endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Queue report start and end dates are required");
        }
        Date rangeStart = startOfDay(startDate);
        Date lastDayStart = startOfDay(endDate);
        if (rangeStart.after(lastDayStart)) {
            throw new IllegalArgumentException("Queue report end date must be on or after the start date");
        }
        List<QueueEntry> entries = dao.getQueueEntries(location, status, rangeStart, endOfDay(endDate));
        return sortQueueEntries(entries);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueEntry> getQueueEntriesByServicePoint(Location servicePoint, Location visibleLocation,
                                                          QueueStatus status, Date date) {
        List<QueueEntry> entries = dao.getQueueEntriesByServicePoint(servicePoint, visibleLocation, status,
                startOfDay(date), endOfDay(date));
        return sortQueueEntries(entries);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueEntry> getQueueEntriesByVisits(Collection<Visit> visits) {
        if (visits == null || visits.isEmpty()) {
            return Collections.emptyList();
        }
        return dao.getQueueEntriesByVisits(visits);
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry callNextPatient(Location servicePoint, Location location) {
        if (servicePoint == null) {
            throw new IllegalArgumentException("Queue service point location is required");
        }
        List<QueueEntry> entries = getQueueEntriesByServicePoint(servicePoint, location, QueueStatus.WAITING, new Date());
        if (entries.isEmpty()) {
            return null;
        }
        return callPatient(entries.get(0), "Called next patient");
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry callPatient(QueueEntry queueEntry) {
        return callPatient(queueEntry, "Patient dashboard opened");
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry callPatientAfterVitals(QueueEntry queueEntry, Encounter vitalsEncounter) {
        if (queueEntry == null) {
            throw new IllegalArgumentException("Queue entry is required");
        }
        if (vitalsEncounter == null) {
            throw new IllegalArgumentException("Vitals encounter is required");
        }
        if (queueEntry.getPatient() == null || !queueEntry.getPatient().equals(vitalsEncounter.getPatient())) {
            throw new IllegalArgumentException("Vitals encounter does not belong to the queue patient");
        }
        if (queueEntry.getVisit() == null || !queueEntry.getVisit().equals(vitalsEncounter.getVisit())) {
            throw new IllegalArgumentException("Vitals encounter does not belong to the queue visit");
        }
        return callPatient(queueEntry, "Vitals recorded");
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry startService(QueueEntry queueEntry) {
        queueEntry.setServiceStartTime(new Date());
        return changeStatus(queueEntry, QueueStatus.IN_PROGRESS, "Service started");
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry completeService(QueueEntry queueEntry) {
        Date now = new Date();
        queueEntry.setServiceEndTime(now);
        queueEntry.setCompletedTime(now);
        return changeStatus(queueEntry, QueueStatus.COMPLETED, "Service completed");
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.TRANSFER_PATIENT)
    public QueueEntry transferPatient(QueueEntry queueEntry, Location destinationServicePoint, String reason) {
        if (queueEntry == null) {
            throw new IllegalArgumentException("Queue entry is required");
        }
        if (destinationServicePoint == null) {
            throw new IllegalArgumentException("Destination service point location is required");
        }
        if (!isLoginLocation(destinationServicePoint)) {
            throw new IllegalArgumentException("Destination service point must be tagged as Login Location");
        }
        if (destinationServicePoint.equals(queueEntry.getServicePoint())) {
            throw new IllegalArgumentException("Destination service point must be different from the current service point");
        }
        Date now = new Date();
        QueueEntry activeEntry = queueEntry.getPatient() == null ? null :
                getActiveQueueEntry(queueEntry.getPatient(), destinationServicePoint, now);
        if (activeEntry != null) {
            throw new IllegalArgumentException("Patient already has an active queue entry at the destination service point");
        }
        queueEntry.setQueueNumber(generateQueueNumber(destinationServicePoint, now));
        queueEntry.setArrivalTime(now);
        queueEntry.setPreviousServicePoint(queueEntry.getServicePoint());
        queueEntry.setServicePoint(destinationServicePoint);
        if (isLaboratoryServicePoint(destinationServicePoint)) {
            if (queueEntry.getAssignedProvider() == null) {
                queueEntry.setAssignedProvider(getCurrentProvider());
            }
            if (queueEntry.getCalledTime() == null) {
                queueEntry.setCalledTime(now);
            }
            queueEntry.setServiceStartTime(now);
            queueEntry.setServiceEndTime(null);
            queueEntry.setCompletedTime(null);
            return changeStatus(queueEntry, QueueStatus.IN_PROGRESS, reason);
        }
        queueEntry.setSessionLocation(destinationServicePoint);
        queueEntry.setAssignedProvider(null);
        queueEntry.setCalledTime(null);
        queueEntry.setServiceStartTime(null);
        queueEntry.setServiceEndTime(null);
        queueEntry.setCompletedTime(null);
        return changeStatus(queueEntry, QueueStatus.WAITING, reason);
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.TRANSFER_PATIENT)
    public QueueEntry markPatientTransferred(QueueEntry queueEntry, String reason) {
        if (queueEntry == null) {
            throw new IllegalArgumentException("Queue entry is required");
        }
        if (QueueStatus.TRANSFERRED.equals(queueEntry.getStatus())) {
            return queueEntry;
        }
        if (!ACTIVE_STATUSES.contains(queueEntry.getStatus())) {
            throw new IllegalArgumentException("Only an active queue entry can be marked as transferred");
        }
        Date now = new Date();
        queueEntry.setServiceEndTime(now);
        queueEntry.setCompletedTime(now);
        return changeStatus(queueEntry, QueueStatus.TRANSFERRED, reason);
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CALL_PATIENT)
    public QueueEntry putOnHold(QueueEntry queueEntry, String reason) {
        return changeStatus(queueEntry, QueueStatus.ON_HOLD, reason);
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.MANAGE)
    public QueueEntry cancelQueueEntry(QueueEntry queueEntry, String reason) {
        return changeStatus(queueEntry, QueueStatus.CANCELLED, reason);
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.MANAGE)
    public QueueEntry updatePriority(QueueEntry queueEntry, QueuePriority priority) {
        if (queueEntry == null) {
            throw new IllegalArgumentException("Queue entry is required");
        }
        if (priority == null) {
            throw new IllegalArgumentException("Queue priority is required");
        }
        if (priority.equals(queueEntry.getPriority())) {
            return queueEntry;
        }
        queueEntry.setPriority(priority);
        queueEntry.setChangedBy(getAuthenticatedUser());
        queueEntry.setDateChanged(new Date());
        QueueEntry saved = dao.saveQueueEntry(queueEntry);
        notifyWebhook(saved, "PRIORITY_CHANGED");
        return saved;
    }

    @Override
    @Transactional
    public QueueStatusHistory createQueueStatusHistory(QueueEntry queueEntry, QueueStatus previousStatus,
                                                       QueueStatus newStatus, String reason) {
        QueueStatusHistory history = new QueueStatusHistory();
        history.setUuid(UUID.randomUUID().toString());
        history.setQueueEntry(queueEntry);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(getAuthenticatedUser());
        history.setDateChanged(new Date());
        history.setReason(reason);
        return dao.saveQueueStatusHistory(history);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public QueueEntry getQueueEntry(Integer id) {
        return dao.getQueueEntry(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public QueueEntry getQueueEntryByUuid(String uuid) {
        return dao.getQueueEntryByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<QueueStatusHistory> getQueueStatusHistory(QueueEntry queueEntry) {
        return dao.getQueueStatusHistory(queueEntry);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.VIEW)
    public List<Location> getServicePointLocations() {
        return getLocationTagUtil().getLoginLocations();
    }

    @Override
    @Transactional
    @Authorized(QueuePrivileges.CONFIGURE)
    public QueueServicePointConceptMap saveServicePointConceptMap(QueueServicePointConceptMap conceptMap) {
        if (conceptMap.getServicePoint() != null && !isLoginLocation(conceptMap.getServicePoint())) {
            throw new IllegalArgumentException("Queue service point must be a location tagged as Login Location");
        }
        Date now = new Date();
        if (conceptMap.getUuid() == null) {
            conceptMap.setUuid(UUID.randomUUID().toString());
        }
        if (conceptMap.getDateCreated() == null) {
            setCreationMetadata(conceptMap, now);
        } else {
            conceptMap.setChangedBy(getAuthenticatedUser());
            conceptMap.setDateChanged(now);
        }
        if (conceptMap.getVoided() == null) {
            conceptMap.setVoided(false);
        }
        if (conceptMap.getActive() == null) {
            conceptMap.setActive(true);
        }
        return dao.saveServicePointConceptMap(conceptMap);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.CONFIGURE)
    public QueueServicePointConceptMap getServicePointConceptMapByUuid(String uuid) {
        return dao.getServicePointConceptMapByUuid(uuid);
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized(QueuePrivileges.CONFIGURE)
    public List<QueueServicePointConceptMap> getAllServicePointConceptMaps(boolean includeInactive) {
        return dao.getAllServicePointConceptMaps(includeInactive);
    }

    protected User getAuthenticatedUser() {
        return Context.getAuthenticatedUser();
    }

    protected Location getCurrentSessionLocation() {
        return Context.getUserContext() == null ? null : Context.getUserContext().getLocation();
    }

    protected Obs getServiceRequestedObs(Encounter encounter) {
        Concept serviceRequestedConcept = getServiceRequestedConcept();
        if (serviceRequestedConcept == null) {
            return null;
        }
        for (Obs obs : encounter.getObsAtTopLevel(true)) {
            if (!Boolean.TRUE.equals(obs.getVoided()) && serviceRequestedConcept.equals(obs.getConcept())) {
                return obs;
            }
        }
        return null;
    }

    protected Concept getServiceRequestedConcept() {
        String gpVal = Context.getAdministrationService().getGlobalProperty(
                SERVICE_REQUESTED_CONCEPT_GP, DEFAULT_SERVICE_REQUESTED_CONCEPT);
        return HtmlFormEntryUtil.getConcept(StringUtils.isBlank(gpVal)
                ? DEFAULT_SERVICE_REQUESTED_CONCEPT : gpVal);
    }

    protected Location resolveServicePointForRegistration(Encounter encounter, Concept serviceRequested) {
        Location encounterLocation = encounter == null ? null : encounter.getLocation();
        if (isLoginLocation(encounterLocation)) {
            return encounterLocation;
        }
        if (encounterLocation != null) {
            log.warn("Registration encounter location is not tagged as Login Location: " + encounterLocation.getName());
        }
        return resolveServicePointFromServiceRequestedConcept(serviceRequested);
    }

    protected boolean isLoginLocation(Location location) {
        return location != null && getLocationTagUtil().isLoginLocation(location);
    }

    protected boolean isLaboratoryServicePoint(Location location) {
        if (location == null) {
            return false;
        }
        String configuredLocation = Context.getAdministrationService().getGlobalProperty(LABORATORY_SERVICE_POINT_GP);
        if (StringUtils.isNotBlank(configuredLocation)
                && (configuredLocation.equalsIgnoreCase(location.getUuid())
                || configuredLocation.equalsIgnoreCase(location.getName())
                || configuredLocation.equals(String.valueOf(location.getId())))) {
            return true;
        }
        return StringUtils.equalsIgnoreCase(location.getName(), "lab")
                || StringUtils.containsIgnoreCase(location.getName(), "laboratory");
    }

    protected LocationTagUtil getLocationTagUtil() {
        if (locationTagUtil != null) {
            return locationTagUtil;
        }
        List<LocationTagUtil> components = Context.getRegisteredComponents(LocationTagUtil.class);
        if (components == null || components.isEmpty()) {
            throw new IllegalStateException("LocationTagUtil is not available");
        }
        return components.get(0);
    }

    protected QueueWebhookNotifier getWebhookNotifier() {
        if (webhookNotifier != null) {
            return webhookNotifier;
        }
        List<QueueWebhookNotifier> components = Context.getRegisteredComponents(QueueWebhookNotifier.class);
        return (components == null || components.isEmpty()) ? null : components.get(0);
    }

    private QueueEntry changeStatus(QueueEntry queueEntry, QueueStatus newStatus, String reason) {
        if (queueEntry == null) {
            return null;
        }
        QueueStatus previousStatus = queueEntry.getStatus();
        queueEntry.setStatus(newStatus);
        queueEntry.setChangedBy(getAuthenticatedUser());
        queueEntry.setDateChanged(new Date());
        dao.saveQueueEntry(queueEntry);
        createQueueStatusHistory(queueEntry, previousStatus, newStatus, reason);
        notifyWebhook(queueEntry, "STATUS_CHANGED");
        return queueEntry;
    }

    private void notifyWebhook(QueueEntry queueEntry, String event) {
        QueueWebhookNotifier notifier = getWebhookNotifier();
        if (notifier != null) {
            notifier.notifyChanged(queueEntry, event);
        }
    }

    private boolean isAfter(Date candidate, Date reference) {
        return candidate != null && (reference == null || candidate.after(reference));
    }

    private QueueEntry callPatient(QueueEntry queueEntry, String reason) {
        if (queueEntry == null) {
            throw new IllegalArgumentException("Queue entry is required");
        }
        if (!QueueStatus.WAITING.equals(queueEntry.getStatus())) {
            return queueEntry;
        }
        Date now = new Date();
        queueEntry.setAssignedProvider(getCurrentProvider());
        queueEntry.setCalledTime(now);
        return changeStatus(queueEntry, QueueStatus.CALLED, reason);
    }

    private void setCreationMetadata(BaseOpenmrsData object, Date date) {
        object.setCreator(getAuthenticatedUser());
        object.setDateCreated(date);
        object.setVoided(false);
    }

    private List<QueueEntry> sortQueueEntries(List<QueueEntry> entries) {
        List<QueueEntry> sorted = new ArrayList<QueueEntry>(entries);
        Collections.sort(sorted, new Comparator<QueueEntry>() {
            @Override
            public int compare(QueueEntry left, QueueEntry right) {
                int leftWeight = left.getPriority() == null ? QueuePriority.NORMAL.getSortWeight() : left.getPriority().getSortWeight();
                int rightWeight = right.getPriority() == null ? QueuePriority.NORMAL.getSortWeight() : right.getPriority().getSortWeight();
                if (leftWeight != rightWeight) {
                    return Integer.compare(leftWeight, rightWeight);
                }
                Date leftArrival = left.getArrivalTime();
                Date rightArrival = right.getArrivalTime();
                if (leftArrival == null && rightArrival == null) {
                    return 0;
                }
                if (leftArrival == null) {
                    return 1;
                }
                if (rightArrival == null) {
                    return -1;
                }
                return leftArrival.compareTo(rightArrival);
            }
        });
        return sorted;
    }

    protected Provider getCurrentProvider() {
        User user = getAuthenticatedUser();
        if (user == null || user.getPerson() == null) {
            return null;
        }
        Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(user.getPerson(), false);
        return providers == null || providers.isEmpty() ? null : providers.iterator().next();
    }

    private Date startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date == null ? new Date() : date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private Date endOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startOfDay(date));
        calendar.add(Calendar.DATE, 1);
        return calendar.getTime();
    }
}
