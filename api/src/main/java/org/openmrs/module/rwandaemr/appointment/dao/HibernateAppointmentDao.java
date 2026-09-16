package org.openmrs.module.rwandaemr.appointment.dao;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Setter;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentBooking;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;

public class HibernateAppointmentDao implements AppointmentDao {

    @Setter
    private DbSessionFactory sessionFactory;

    @Override
    public AppointmentSchedule saveSchedule(AppointmentSchedule schedule) {
        session().saveOrUpdate(schedule);
        return schedule;
    }

    @Override
    public AppointmentSchedule getSchedule(Integer id) {
        return id == null ? null : (AppointmentSchedule) session().get(AppointmentSchedule.class, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppointmentSchedule getScheduleForUpdate(Integer id) {
        if (id == null) {
            return null;
        }
        List<AppointmentSchedule> schedules = session()
                .createQuery("from AppointmentSchedule s where s.id = :id and s.voided = false")
                .setParameter("id", id)
                .setLockMode("s", LockMode.PESSIMISTIC_WRITE)
                .list();
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppointmentSchedule getScheduleByServicePointDateAndProvider(Location servicePoint, Date scheduleDate,
                                                                        Provider provider) {
        if (servicePoint == null || scheduleDate == null) {
            return null;
        }
        String hql = "from AppointmentSchedule s where s.servicePoint = :servicePoint " +
                "and s.scheduleDate = :scheduleDate and s.voided = false";
        hql += provider == null ? " and s.provider is null" : " and s.provider = :provider";
        Query query = session().createQuery(hql)
                .setParameter("servicePoint", servicePoint)
                .setParameter("scheduleDate", scheduleDate);
        if (provider != null) {
            query.setParameter("provider", provider);
        }
        List<AppointmentSchedule> schedules = query.list();
        return schedules.isEmpty() ? null : schedules.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AppointmentSchedule> getSchedules(Location servicePoint, Date startDate, Date endDate,
                                                  boolean includeInactive) {
        String hql = "from AppointmentSchedule s where s.voided = false " +
                "and s.scheduleDate >= :startDate and s.scheduleDate <= :endDate";
        if (servicePoint != null) {
            hql += " and s.servicePoint = :servicePoint";
        }
        if (!includeInactive) {
            hql += " and s.active = true";
        }
        hql += " order by s.scheduleDate, s.servicePoint.name";
        Query query = session().createQuery(hql)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate);
        if (servicePoint != null) {
            query.setParameter("servicePoint", servicePoint);
        }
        return query.list();
    }

    @Override
    public AppointmentBooking saveBooking(AppointmentBooking booking) {
        session().saveOrUpdate(booking);
        return booking;
    }

    @Override
    public AppointmentBooking getBooking(Integer id) {
        return id == null ? null : (AppointmentBooking) session().get(AppointmentBooking.class, id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppointmentBooking getBookingForUpdate(Integer id) {
        if (id == null) {
            return null;
        }
        List<AppointmentBooking> bookings = session()
                .createQuery("from AppointmentBooking b where b.id = :id and b.voided = false")
                .setParameter("id", id)
                .setLockMode("b", LockMode.PESSIMISTIC_WRITE)
                .list();
        return bookings.isEmpty() ? null : bookings.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppointmentBooking getBookingByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        List<AppointmentBooking> bookings = session()
                .createQuery("from AppointmentBooking b where b.uuid = :uuid")
                .setParameter("uuid", uuid)
                .list();
        return bookings.isEmpty() ? null : bookings.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AppointmentBooking getBooking(Patient patient, AppointmentSchedule schedule) {
        if (patient == null || schedule == null) {
            return null;
        }
        List<AppointmentBooking> bookings = session().createQuery(
                        "from AppointmentBooking b where b.patient = :patient and b.schedule = :schedule " +
                                "and b.voided = false")
                .setParameter("patient", patient)
                .setParameter("schedule", schedule)
                .list();
        return bookings.isEmpty() ? null : bookings.get(0);
    }

    @Override
    public int countBookings(AppointmentSchedule schedule, List<String> statusNames) {
        if (schedule == null || statusNames == null || statusNames.isEmpty()) {
            return 0;
        }
        Number count = (Number) session().createQuery(
                        "select count(b.id) from AppointmentBooking b where b.schedule = :schedule " +
                                "and b.voided = false and b.statusName in (:statuses)")
                .setParameter("schedule", schedule)
                .setParameterList("statuses", statusNames)
                .uniqueResult();
        return count == null ? 0 : count.intValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AppointmentBooking> getBookings(Collection<AppointmentSchedule> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return Collections.emptyList();
        }
        return session().createQuery(
                        "from AppointmentBooking b where b.schedule in (:schedules) and b.voided = false " +
                                "order by b.schedule.scheduleDate, b.schedule.servicePoint.name, b.requestedAt")
                .setParameterList("schedules", schedules)
                .list();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AppointmentBooking> getPatientBookings(Patient patient, Date startDate, Date endDate) {
        if (patient == null) {
            return Collections.emptyList();
        }
        String hql = "from AppointmentBooking b where b.patient = :patient and b.voided = false " +
                "and b.schedule.voided = false";
        if (startDate != null) {
            hql += " and b.schedule.scheduleDate >= :startDate";
        }
        if (endDate != null) {
            hql += " and b.schedule.scheduleDate <= :endDate";
        }
        hql += " order by b.schedule.scheduleDate, b.schedule.servicePoint.name";
        Query query = session().createQuery(hql).setParameter("patient", patient);
        if (startDate != null) {
            query.setParameter("startDate", startDate);
        }
        if (endDate != null) {
            query.setParameter("endDate", endDate);
        }
        return query.list();
    }

    private DbSession session() {
        return sessionFactory.getCurrentSession();
    }
}
