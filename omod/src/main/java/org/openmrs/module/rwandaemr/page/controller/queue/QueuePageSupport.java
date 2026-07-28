package org.openmrs.module.rwandaemr.page.controller.queue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueuePrivileges;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;

public abstract class QueuePageSupport {

    protected boolean canViewQueue() {
        return Context.hasPrivilege(QueuePrivileges.VIEW);
    }

    protected boolean canViewAllLocations() {
        return Context.hasPrivilege(QueuePrivileges.VIEW_ALL_LOCATIONS);
    }

    protected boolean canManageQueue() {
        return Context.hasPrivilege(QueuePrivileges.MANAGE);
    }

    protected boolean canCallPatient() {
        return Context.hasPrivilege(QueuePrivileges.CALL_PATIENT);
    }

    protected boolean canTransferPatient() {
        return Context.hasPrivilege(QueuePrivileges.TRANSFER_PATIENT);
    }

    protected Location getVisibleLocation(UiSessionContext sessionContext, Integer locationId) {
        if (canViewAllLocations() && locationId != null) {
            return Context.getLocationService().getLocation(locationId);
        }
        return sessionContext.getSessionLocation();
    }

    protected QueueStatus parseStatus(String status) {
        if (StringUtils.isBlank(status) || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return QueueStatus.valueOf(status);
    }

    protected Location getLocation(Integer locationId) {
        return locationId == null ? null : Context.getLocationService().getLocation(locationId);
    }

    protected void processEntryAction(QueueService queueService, String action, Integer entryId,
                                      Integer destinationServicePointId, String priority, String reason) {
        if ("callNext".equals(action)) {
            return;
        }
        QueueEntry entry = queueService.getQueueEntry(entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Queue entry was not found");
        }
        if ("start".equals(action)) {
            queueService.startService(entry);
        } else if ("complete".equals(action)) {
            queueService.completeService(entry);
        } else if ("hold".equals(action)) {
            queueService.putOnHold(entry, reason);
        } else if ("cancel".equals(action)) {
            queueService.cancelQueueEntry(entry, reason);
        } else if ("transfer".equals(action)) {
            Location destinationServicePoint = getLocation(destinationServicePointId);
            queueService.transferPatient(entry, destinationServicePoint, reason);
        } else if ("updatePriority".equals(action)) {
            QueuePriority queuePriority = StringUtils.isBlank(priority) ? null : QueuePriority.valueOf(priority);
            queueService.updatePriority(entry, queuePriority);
        } else {
            throw new IllegalArgumentException("Unsupported queue action: " + action);
        }
    }

    protected String openPatientDashboard(UiUtils ui, QueueService queueService, Integer entryId) {
        QueueEntry entry = queueService.getQueueEntry(entryId);
        if (entry == null) {
            throw new IllegalArgumentException("Queue entry was not found");
        }
        if (entry.getPatient() == null || entry.getPatient().getId() == null) {
            throw new IllegalArgumentException("Queue entry has no patient dashboard");
        }
        queueService.callPatient(entry);
        return "redirect:" + ui.pageLink("coreapps", "clinicianfacing/patient") + "?patientId=" + entry.getPatient().getId();
    }

    protected String redirect(UiUtils ui, String page, Object... namesAndValues) {
        List<String> params = new ArrayList<String>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            Object value = namesAndValues[i + 1];
            if (value != null) {
                params.add(encode(String.valueOf(namesAndValues[i])) + "=" + encode(String.valueOf(value)));
            }
        }
        String query = params.isEmpty() ? "" : "?" + StringUtils.join(params, "&");
        return "redirect:" + ui.pageLink("rwandaemr", page) + query;
    }

    protected void setToast(UiSessionContext sessionContext, String message) {
        sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, message);
    }

    protected void setError(UiSessionContext sessionContext, String message) {
        sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, message);
    }

    protected List<QueueEntry> getEntriesForLocation(QueueService queueService, Location location, QueueStatus status,
                                                     Date date) {
        return getEntriesForLocation(queueService, location, status, date, date);
    }

    protected List<QueueEntry> getEntriesForLocation(QueueService queueService, Location location, QueueStatus status,
                                                     Date startDate, Date endDate) {
        if (canViewAllLocations() && location == null) {
            return queueService.getQueueEntriesByLocation(null, status, startDate, endDate);
        }
        if (location == null) {
            return new ArrayList<QueueEntry>();
        }
        return queueService.getQueueEntriesByLocation(location, status, startDate, endDate);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }
}
