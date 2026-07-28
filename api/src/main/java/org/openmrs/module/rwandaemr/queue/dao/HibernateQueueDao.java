package org.openmrs.module.rwandaemr.queue.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Setter;
import org.hibernate.Query;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;

public class HibernateQueueDao implements QueueDao {

    @Setter
    private DbSessionFactory sessionFactory;

    @Override
    public QueueServicePointConceptMap saveServicePointConceptMap(QueueServicePointConceptMap conceptMap) {
        session().saveOrUpdate(conceptMap);
        return conceptMap;
    }

    @Override
    public QueueServicePointConceptMap getServicePointConceptMapByUuid(String uuid) {
        return getUnique("from QueueServicePointConceptMap m where m.uuid = :uuid", "uuid", uuid);
    }

    @Override
    public QueueServicePointConceptMap getServicePointConceptMapByConcept(Concept concept) {
        if (concept == null) {
            return null;
        }
        return getUnique("from QueueServicePointConceptMap m where m.serviceRequestedConcept = :concept " +
                "and m.active = true and m.voided = false", "concept", concept);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueServicePointConceptMap> getAllServicePointConceptMaps(boolean includeInactive) {
        String hql = "from QueueServicePointConceptMap m where m.voided = false";
        if (!includeInactive) {
            hql += " and m.active = true";
        }
        hql += " order by m.servicePoint.name";
        return session().createQuery(hql).list();
    }

    @Override
    public QueueEntry saveQueueEntry(QueueEntry queueEntry) {
        session().saveOrUpdate(queueEntry);
        return queueEntry;
    }

    @Override
    public QueueEntry getQueueEntry(Integer id) {
        return id == null ? null : (QueueEntry) session().get(QueueEntry.class, id);
    }

    @Override
    public QueueEntry getQueueEntryByUuid(String uuid) {
        return getUnique("from QueueEntry q where q.uuid = :uuid", "uuid", uuid);
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueueEntry getActiveQueueEntry(Patient patient, Location servicePoint, Date startOfDay, Date endOfDay,
                                          List<QueueStatus> activeStatuses) {
        if (patient == null || servicePoint == null) {
            return null;
        }
        List<QueueEntry> entries = session().createQuery("from QueueEntry q where q.voided = false " +
                        "and q.patient = :patient and q.servicePoint = :servicePoint " +
                        "and q.statusName in (:statuses) and q.arrivalTime >= :startOfDay and q.arrivalTime < :endOfDay " +
                        "order by q.arrivalTime")
                .setParameter("patient", patient)
                .setParameter("servicePoint", servicePoint)
                .setParameterList("statuses", toStatusNames(activeStatuses))
                .setParameter("startOfDay", startOfDay)
                .setParameter("endOfDay", endOfDay)
                .list();
        return entries.isEmpty() ? null : entries.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueueEntry getActiveQueueEntry(Patient patient, Date startOfDay, Date endOfDay,
                                          List<QueueStatus> activeStatuses) {
        if (patient == null) {
            return null;
        }
        List<QueueEntry> entries = session().createQuery("from QueueEntry q where q.voided = false " +
                        "and q.patient = :patient and q.statusName in (:statuses) " +
                        "and q.arrivalTime >= :startOfDay and q.arrivalTime < :endOfDay " +
                        "order by q.arrivalTime desc")
                .setParameter("patient", patient)
                .setParameterList("statuses", toStatusNames(activeStatuses))
                .setParameter("startOfDay", startOfDay)
                .setParameter("endOfDay", endOfDay)
                .setMaxResults(1)
                .list();
        return entries.isEmpty() ? null : entries.get(0);
    }

    @Override
    public int countQueueEntries(Location servicePoint, Date startOfDay, Date endOfDay) {
        if (servicePoint == null) {
            return 0;
        }
        Number count = (Number) session().createQuery("select count(q.id) from QueueEntry q where q.voided = false " +
                        "and q.servicePoint = :servicePoint and q.arrivalTime >= :startOfDay and q.arrivalTime < :endOfDay")
                .setParameter("servicePoint", servicePoint)
                .setParameter("startOfDay", startOfDay)
                .setParameter("endOfDay", endOfDay)
                .uniqueResult();
        return count == null ? 0 : count.intValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueEntry> getQueueEntries(Location location, QueueStatus status, Date startOfDay, Date endOfDay) {
        String hql = "from QueueEntry q where q.voided = false and q.arrivalTime >= :startOfDay and q.arrivalTime < :endOfDay";
        if (location != null) {
            hql += " and (q.sessionLocation = :location or q.servicePoint = :location)";
        }
        if (status != null) {
            hql += " and q.statusName = :status";
        }
        hql += " order by q.arrivalTime";
        Query query = session().createQuery(hql);
        query.setParameter("startOfDay", startOfDay);
        query.setParameter("endOfDay", endOfDay);
        if (location != null) {
            query.setParameter("location", location);
        }
        if (status != null) {
            query.setParameter("status", status.name());
        }
        return query.list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueEntry> getQueueEntriesByServicePoint(Location servicePoint, Location visibleLocation,
                                                          QueueStatus status, Date startOfDay, Date endOfDay) {
        String hql = "from QueueEntry q where q.voided = false and q.servicePoint = :servicePoint " +
                "and q.arrivalTime >= :startOfDay and q.arrivalTime < :endOfDay";
        if (visibleLocation != null) {
            hql += " and (q.sessionLocation = :visibleLocation or q.servicePoint = :visibleLocation)";
        }
        if (status != null) {
            hql += " and q.statusName = :status";
        }
        hql += " order by q.arrivalTime";
        Query query = session().createQuery(hql);
        query.setParameter("servicePoint", servicePoint);
        query.setParameter("startOfDay", startOfDay);
        query.setParameter("endOfDay", endOfDay);
        if (visibleLocation != null) {
            query.setParameter("visibleLocation", visibleLocation);
        }
        if (status != null) {
            query.setParameter("status", status.name());
        }
        return query.list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueEntry> getQueueEntriesByVisit(Visit visit) {
        return session().createQuery("from QueueEntry q where q.voided = false and q.visit = :visit order by q.arrivalTime")
                .setParameter("visit", visit)
                .list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueEntry> getQueueEntriesByVisits(Collection<Visit> visits) {
        if (visits == null || visits.isEmpty()) {
            return Collections.emptyList();
        }
        return session().createQuery("from QueueEntry q where q.voided = false and q.visit in (:visits) " +
                        "order by q.arrivalTime, q.id")
                .setParameterList("visits", visits)
                .list();
    }

    @Override
    public QueueStatusHistory saveQueueStatusHistory(QueueStatusHistory history) {
        session().saveOrUpdate(history);
        return history;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<QueueStatusHistory> getQueueStatusHistory(QueueEntry queueEntry) {
        return session().createQuery("from QueueStatusHistory h where h.queueEntry = :queueEntry order by h.dateChanged")
                .setParameter("queueEntry", queueEntry)
                .list();
    }

    @SuppressWarnings("unchecked")
    private <T> T getUnique(String hql, String parameterName, Object value) {
        if (value == null) {
            return null;
        }
        List<T> results = session().createQuery(hql).setParameter(parameterName, value).list();
        return results.isEmpty() ? null : results.get(0);
    }

    private DbSession session() {
        return sessionFactory.getCurrentSession();
    }

    private List<String> toStatusNames(List<QueueStatus> statuses) {
        List<String> statusNames = new ArrayList<String>();
        if (statuses != null) {
            for (QueueStatus status : statuses) {
                if (status != null) {
                    statusNames.add(status.name());
                }
            }
        }
        return statusNames;
    }
}
