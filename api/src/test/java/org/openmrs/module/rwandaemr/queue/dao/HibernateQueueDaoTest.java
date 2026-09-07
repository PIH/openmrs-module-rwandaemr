package org.openmrs.module.rwandaemr.queue.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;

import org.hibernate.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.queue.QueueAssignmentFilter;

public class HibernateQueueDaoTest {

    @Test
    public void shouldAddEachPatientNameTokenToQueueQuery() {
        DbSessionFactory sessionFactory = mock(DbSessionFactory.class);
        DbSession session = mock(DbSession.class);
        Query query = mock(Query.class);
        when(sessionFactory.getCurrentSession()).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(0L);
        HibernateQueueDao dao = new HibernateQueueDao();
        dao.setSessionFactory(sessionFactory);

        dao.countQueueEntries(null, null, new Date(0), new Date(1_000),
                null, null, " Aline Uwa ", QueueAssignmentFilter.ALL, null);

        ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
        verify(session).createQuery(hql.capture());
        assertTrue(hql.getValue().contains("lower(patientName.givenName) like :patientName0"));
        assertTrue(hql.getValue().contains("lower(patientName.familyName) like :patientName1"));
        verify(query).setParameter("patientName0", "%aline%");
        verify(query).setParameter("patientName1", "%uwa%");
    }

    @Test
    public void shouldFilterEntriesAssignedToAnyCurrentProvider() {
        DbSessionFactory sessionFactory = mock(DbSessionFactory.class);
        DbSession session = mock(DbSession.class);
        Query query = mock(Query.class);
        when(sessionFactory.getCurrentSession()).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(0L);
        HibernateQueueDao dao = new HibernateQueueDao();
        dao.setSessionFactory(sessionFactory);

        dao.countQueueEntries(null, null, new Date(0), new Date(1_000), null, null, null,
                QueueAssignmentFilter.ASSIGNED_TO_ME, Arrays.asList(7, 9));

        ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
        verify(session).createQuery(hql.capture());
        assertTrue(hql.getValue().contains("q.assignedProvider.id in (:currentProviderIds)"));
        verify(query).setParameterList("currentProviderIds", Arrays.asList(7, 9));
    }

    @Test
    public void shouldIncludeUnassignedEntriesWhenFilteringEntriesNotAssignedToCurrentProvider() {
        DbSessionFactory sessionFactory = mock(DbSessionFactory.class);
        DbSession session = mock(DbSession.class);
        Query query = mock(Query.class);
        when(sessionFactory.getCurrentSession()).thenReturn(session);
        when(session.createQuery(anyString())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(0L);
        HibernateQueueDao dao = new HibernateQueueDao();
        dao.setSessionFactory(sessionFactory);

        dao.countQueueEntries(null, null, new Date(0), new Date(1_000), null, null, null,
                QueueAssignmentFilter.NOT_ASSIGNED_TO_ME, Arrays.asList(7, 9));

        ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
        verify(session).createQuery(hql.capture());
        assertTrue(hql.getValue().contains(
                "q.assignedProvider is null or q.assignedProvider.id not in (:currentProviderIds)"));
        verify(query).setParameterList("currentProviderIds", Arrays.asList(7, 9));
    }
}
