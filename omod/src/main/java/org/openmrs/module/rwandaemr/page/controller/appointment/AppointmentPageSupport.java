package org.openmrs.module.rwandaemr.page.controller.appointment;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;

public abstract class AppointmentPageSupport {

    protected LocalDate parseDate(String value, LocalDate defaultValue) {
        if (StringUtils.isBlank(value)) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("Appointment date is required");
            }
            return defaultValue;
        }
        try {
            return LocalDate.parse(value);
        }
        catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Appointment dates must be valid dates");
        }
    }

    protected Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    protected Location findServicePoint(List<Location> servicePoints, Integer servicePointId) {
        if (servicePointId == null) {
            return null;
        }
        for (Location servicePoint : servicePoints) {
            if (servicePointId.equals(servicePoint.getId())) {
                return servicePoint;
            }
        }
        return null;
    }

    protected Patient findPatient(String patientId) {
        if (StringUtils.isBlank(patientId)) {
            return null;
        }
        Patient patient = Context.getPatientService().getPatientByUuid(patientId.trim());
        if (patient != null) {
            return patient;
        }
        try {
            return Context.getPatientService().getPatient(Integer.valueOf(patientId.trim()));
        }
        catch (NumberFormatException ignored) {
            return null;
        }
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

    private String encode(String value) {
        try {
            return URLEncoder.encode(StringUtils.defaultString(value), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }
}
