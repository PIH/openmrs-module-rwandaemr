package org.openmrs.module.rwandaemr.htmlformentry;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.HiddenFieldWidget;
import org.openmrs.module.rwandaemr.appointment.AppointmentScheduleSummary;
import org.openmrs.module.rwandaemr.appointment.FacilityAppointmentService;
import org.openmrs.module.rwandaemr.appointment.model.AppointmentSchedule;
import org.openmrs.module.rwandaemr.htmlformentry.CreateFacilityAppointmentAction;

public class AppointmentBookingElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FIELD_NAME = "rwandaemrAppointmentScheduleId";

    private final String id;
    private final boolean required;
    private final String notes;
    private final List<Location> servicePoints;
    private final HiddenFieldWidget scheduleWidget = new HiddenFieldWidget();
    private final ErrorWidget errorWidget = new ErrorWidget();

    public AppointmentBookingElement(FormEntryContext context, Map<String, String> attributes) {
        id = StringUtils.isBlank(attributes.get("id")) ? "appointment-booking" : attributes.get("id");
        if (!id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Appointment booking widget id must be a valid HTML id");
        }
        required = !"false".equalsIgnoreCase(attributes.get("required"));
        notes = StringUtils.trimToNull(attributes.get("notes"));
        servicePoints = selectServicePoints(
                Context.getService(FacilityAppointmentService.class).getServicePointLocations(),
                attributes.get("servicePoints"));
        context.registerWidget(scheduleWidget, FIELD_NAME);
        context.registerErrorWidget(scheduleWidget, errorWidget);
    }

    @Override
    public String generateHtml(FormEntryContext context) {
        StringBuilder html = new StringBuilder(errorWidget.generateHtml(context));
        html.append("<div id=\"").append(id).append("\" class=\"rwandaemr-appointment-booking\">")
                .append(scheduleWidget.generateHtml(context));
        if (FormEntryContext.Mode.VIEW == context.getMode()) {
            return html.append("<span>Appointment booking is available while entering the form.</span></div>")
                    .toString();
        }
        html.append("<label for=\"").append(id).append("-service\">Service point")
                .append(required ? " *" : "").append("</label>")
                .append("<div class=\"rwandaemr-appointment-row\"><select id=\"").append(id)
                .append("-service\"><option value=\"\">Select service point</option>");
        for (Map<String, Object> servicePoint : getServicePointOptions()) {
            html.append("<option value=\"").append(servicePoint.get("id")).append("\">")
                    .append(escape(String.valueOf(servicePoint.get("name")))).append("</option>");
        }
        html.append("</select><button id=\"").append(id)
                .append("-check\" type=\"button\" class=\"button\">&#128197; Check Availability</button></div>")
                .append("<div id=\"").append(id).append("-availability\" class=\"rwandaemr-appointment-availability\"")
                .append(" style=\"display:none\"><label for=\"").append(id).append("-schedule\">Available date")
                .append(required ? " *" : "").append("</label><select id=\"").append(id)
                .append("-schedule\"><option value=\"\">Select available date</option></select>")
                .append("<span id=\"").append(id).append("-message\"></span></div></div>")
                .append(renderStyles()).append(renderScript(encodeJson(getAvailableScheduleOptions())));
        return html.toString();
    }

    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest request) {
        List<FormSubmissionError> errors = new ArrayList<FormSubmissionError>();
        String value = StringUtils.trimToNull(request.getParameter(FIELD_NAME));
        if (value == null) {
            if (required) {
                errors.add(new FormSubmissionError(scheduleWidget, "Select an available appointment date"));
            }
            return errors;
        }
        try {
            Integer scheduleId = Integer.valueOf(value);
            FacilityAppointmentService service = Context.getService(FacilityAppointmentService.class);
            AppointmentSchedule schedule = service.getSchedule(scheduleId);
            if (schedule == null || !Boolean.TRUE.equals(schedule.getActive())) {
                throw new IllegalArgumentException("The selected appointment date is not available");
            }
            boolean available = false;
            java.util.Date today = java.util.Date.from(
                    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            for (AppointmentScheduleSummary summary : service.getAvailableSchedules(
                    schedule.getServicePoint(), today, null)) {
                if (scheduleId.equals(summary.getSchedule().getId())) {
                    available = true;
                    break;
                }
            }
            if (!available) {
                throw new IllegalArgumentException("The selected appointment date is no longer available");
            }
        }
        catch (NumberFormatException e) {
            errors.add(new FormSubmissionError(scheduleWidget, "Invalid appointment date"));
        }
        catch (IllegalArgumentException e) {
            errors.add(new FormSubmissionError(scheduleWidget, e.getMessage()));
        }
        return errors;
    }

    @Override
    public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
        String value = StringUtils.trimToNull(request.getParameter(FIELD_NAME));
        if (value != null) {
            session.getSubmissionActions().addCustomFormSubmissionAction(
                    new CreateFacilityAppointmentAction(Integer.valueOf(value), notes));
        }
    }

    private List<Map<String, Object>> getServicePointOptions() {
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        for (Location location : servicePoints) {
            Map<String, Object> option = new HashMap<String, Object>();
            option.put("id", location.getId());
            option.put("name", location.getName());
            options.add(option);
        }
        return options;
    }

    private List<Map<String, Object>> getAvailableScheduleOptions() {
        FacilityAppointmentService service = Context.getService(FacilityAppointmentService.class);
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        java.util.Date today = java.util.Date.from(
                LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        for (Location location : servicePoints) {
            for (AppointmentScheduleSummary summary : service.getAvailableSchedules(location, today, null)) {
                AppointmentSchedule schedule = summary.getSchedule();
                Map<String, Object> option = new HashMap<String, Object>();
                option.put("id", schedule.getId());
                option.put("servicePointId", location.getId());
                option.put("date", new java.text.SimpleDateFormat("dd.MMM.yyyy").format(schedule.getScheduleDate()));
                option.put("provider", schedule.getProvider() == null ? "" : schedule.getProvider().getName());
                option.put("remaining", summary.getRemainingCapacity());
                options.add(option);
            }
        }
        return options;
    }

    static List<Location> selectServicePoints(List<Location> availableServicePoints, String configuredValues) {
        List<Location> available = availableServicePoints == null
                ? new ArrayList<Location>() : availableServicePoints;
        if (StringUtils.isBlank(configuredValues)) {
            return new ArrayList<Location>(available);
        }
        List<Location> selected = new ArrayList<Location>();
        Set<Integer> selectedIds = new LinkedHashSet<Integer>();
        for (String configuredValue : configuredValues.split(",")) {
            String value = StringUtils.trimToNull(configuredValue);
            if (value == null) {
                continue;
            }
            Location match = null;
            for (Location location : available) {
                if (location != null && (value.equals(String.valueOf(location.getId()))
                        || value.equalsIgnoreCase(StringUtils.defaultString(location.getUuid()))
                        || value.equalsIgnoreCase(StringUtils.defaultString(location.getName())))) {
                    match = location;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException("Unknown appointment service point: " + value);
            }
            if (match.getId() == null || selectedIds.add(match.getId())) {
                selected.add(match);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("The servicePoints attribute must contain at least one service point");
        }
        return selected;
    }

    private String encodeJson(Object value) {
        try {
            return Base64.getEncoder().encodeToString(
                    OBJECT_MAPPER.writeValueAsString(value).getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to render appointment availability", e);
        }
    }

    private String escape(String value) {
        return StringEscapeUtils.escapeHtml4(StringUtils.defaultString(value));
    }

    private String renderStyles() {
        return "<style type=\"text/css\">#" + id + "{background:#f7f9f9;border-top:1px solid #d4dddd;" +
                "border-bottom:1px solid #d4dddd;padding:14px 10px}#" + id + " label{display:block;font-weight:bold;" +
                "margin-bottom:5px}#" + id + " .rwandaemr-appointment-row,#" + id +
                " .rwandaemr-appointment-availability{display:flex;align-items:flex-end;flex-wrap:wrap;gap:10px}#" + id +
                " select{box-sizing:border-box;min-height:36px;min-width:310px}#" + id +
                " .rwandaemr-appointment-availability{margin-top:12px}#" + id +
                " .rwandaemr-appointment-availability label{width:100%}#" + id +
                "-message{color:#666}</style>";
    }

    private String renderScript(String schedulesBase64) {
        return "<script type=\"text/javascript\">(function(){" +
                "var root=document.getElementById('" + id + "');if(!root){return;}" +
                "var hidden=root.querySelector(\"input[name='" + FIELD_NAME + "']\");" +
                "var service=document.getElementById('" + id + "-service');" +
                "var box=document.getElementById('" + id + "-availability');" +
                "var dates=document.getElementById('" + id + "-schedule');" +
                "var message=document.getElementById('" + id + "-message');var schedules=[];" +
                "try{schedules=JSON.parse(decodeURIComponent(escape(window.atob('" + schedulesBase64 + "'))));}" +
                "catch(e){message.textContent='Unable to load appointment availability.';}" +
                "document.getElementById('" + id + "-check').addEventListener('click',function(){" +
                "hidden.value='';dates.options.length=0;dates.add(new Option('Select available date',''));" +
                "if(!service.value){box.style.display='none';return;}var count=0;" +
                "schedules.forEach(function(s){if(String(s.servicePointId)===String(service.value)){" +
                "var label=s.date+(s.provider?' - Provider: '+s.provider:'')+' - '+s.remaining+' places remaining';" +
                "dates.add(new Option(label,s.id));count++;}});" +
                "message.textContent=count?'':'No appointment dates with remaining capacity are available.';" +
                "box.style.display='flex';});" +
                "service.addEventListener('change',function(){hidden.value='';box.style.display='none';});" +
                "dates.addEventListener('change',function(){hidden.value=this.value;});})();</script>";
    }
}
