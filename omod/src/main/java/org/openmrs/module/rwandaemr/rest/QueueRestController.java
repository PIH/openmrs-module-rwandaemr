package org.openmrs.module.rwandaemr.rest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.rwandaemr.queue.QueuePriority;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.QueueStatus;
import org.openmrs.module.rwandaemr.queue.model.QueueEntry;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.module.rwandaemr.queue.model.QueueStatusHistory;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.response.IllegalRequestException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * REST API exposing the RwandaEMR queue module ({@link QueueService}) so an external application
 * can read and drive the queue without duplicating any of its business logic. OpenMRS remains the
 * single source of truth for queue data; this controller is a thin pass-through layer.
 */
@Controller
public class QueueRestController {

    protected final Log log = LogFactory.getLog(getClass());

    private static final String BASE = "/rest/v1/rwandaemr/queue";

    private static final String SERVICE_REQUESTED_CONCEPT_GP = "registration.serviceRequestedConcept";

    private static final String DEFAULT_SERVICE_REQUESTED_CONCEPT = "6702";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

    /**
     * Controller-local exception handling so callers get real HTTP status codes. Some other
     * ad-hoc REST controllers in this module rely on a webapp-wide resolver that renders thrown
     * {@link ResponseException}s as HTTP 200 with the error in the body; a controller-local
     * {@code @ExceptionHandler} takes precedence over that for this controller's own methods.
     */
    @ExceptionHandler(ResponseException.class)
    @ResponseBody
    public Object handleResponseException(ResponseException e, HttpServletResponse response) {
        ResponseStatus annotation = e.getClass().getAnnotation(ResponseStatus.class);
        HttpStatus status = annotation != null ? annotation.value() : HttpStatus.BAD_REQUEST;
        response.setStatus(status.value());
        log.warn("Queue REST API error: " + e.getMessage());
        SimpleObject error = new SimpleObject();
        error.put("error", e.getMessage());
        return error;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public Object handleIllegalArgumentException(IllegalArgumentException e, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        log.warn("Queue REST API error: " + e.getMessage());
        SimpleObject error = new SimpleObject();
        error.put("error", e.getMessage());
        return error;
    }

    // ------------------------------------------------------------------
    // Queue entries
    // ------------------------------------------------------------------

    @RequestMapping(value = BASE, method = RequestMethod.GET)
    @ResponseBody
    public Object list(HttpServletRequest request) throws ResponseException {
        String servicePointUuid = required(request, "servicePointUuid");
        Location servicePoint = getLocationOrThrow(servicePointUuid, "servicePointUuid");
        Location visibleLocation = optionalLocation(request, "visibleLocationUuid");
        QueueStatus status = optionalStatus(request, "status");
        Date date = optionalDate(request, "date", new Date());

        List<QueueEntry> entries = getQueueService()
                .getQueueEntriesByServicePoint(servicePoint, visibleLocation, status, date);

        List<SimpleObject> result = new ArrayList<>();
        for (QueueEntry entry : entries) {
            result.add(toSimpleObject(entry));
        }
        SimpleObject response = new SimpleObject();
        response.put("results", result);
        return response;
    }

    @RequestMapping(value = BASE + "/{uuid}", method = RequestMethod.GET)
    @ResponseBody
    public Object get(@PathVariable("uuid") String uuid) throws ResponseException {
        return toSimpleObject(getQueueEntryOrThrow(uuid));
    }

    @RequestMapping(value = BASE + "/{uuid}/history", method = RequestMethod.GET)
    @ResponseBody
    public Object history(@PathVariable("uuid") String uuid) throws ResponseException {
        QueueEntry entry = getQueueEntryOrThrow(uuid);
        List<SimpleObject> result = new ArrayList<>();
        for (QueueStatusHistory history : getQueueService().getQueueStatusHistory(entry)) {
            result.add(toSimpleObject(history));
        }
        SimpleObject response = new SimpleObject();
        response.put("results", result);
        return response;
    }

    /**
     * Resolves the Service Requested concept the same way the native registration form does
     * (by id, uuid, or mapping reference via {@link HtmlFormEntryUtil#getConcept}), and returns
     * it with its active, non-retired coded answers. The generic /concept/{uuid} REST resource
     * can't be used for this by callers because the configured GP value is a legacy numeric id,
     * which that resource does not accept.
     */
    @RequestMapping(value = BASE + "/servicerequested", method = RequestMethod.GET)
    @ResponseBody
    public Object serviceRequestedOptions() throws ResponseException {
        String configured = Context.getAdministrationService().getGlobalProperty(
                SERVICE_REQUESTED_CONCEPT_GP, DEFAULT_SERVICE_REQUESTED_CONCEPT);
        String conceptRef = StringUtils.isBlank(configured) ? DEFAULT_SERVICE_REQUESTED_CONCEPT : configured;

        Concept concept;
        try {
            concept = HtmlFormEntryUtil.getConcept(conceptRef);
        }
        catch (RuntimeException e) {
            concept = null;
        }
        if (concept == null || concept.getDatatype() == null || !concept.getDatatype().isCoded()) {
            throw new IllegalRequestException(
                    "The concept configured by " + SERVICE_REQUESTED_CONCEPT_GP + " was not found or is not coded");
        }

        List<ConceptAnswer> configuredAnswers = new ArrayList<>(concept.getAnswers());
        Collections.sort(configuredAnswers);
        List<SimpleObject> answers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ConceptAnswer answer : configuredAnswers) {
            Concept answerConcept = answer.getAnswerConcept();
            if (answerConcept == null || Boolean.TRUE.equals(answerConcept.getRetired())) {
                continue;
            }
            if (seen.add(answerConcept.getUuid())) {
                answers.add(toSimpleObject(answerConcept));
            }
        }

        SimpleObject response = toSimpleObject(concept);
        response.put("answers", answers);
        return response;
    }

    @RequestMapping(value = BASE + "/register", method = RequestMethod.POST)
    @ResponseBody
    public Object register(HttpServletRequest request) throws ResponseException {
        String encounterUuid = required(request, "encounterUuid");
        Encounter encounter = Context.getEncounterService().getEncounterByUuid(encounterUuid);
        if (encounter == null) {
            throw new IllegalRequestException("No encounter found with uuid " + encounterUuid);
        }
        QueueEntry entry = getQueueService().addPatientToQueueFromRegistration(encounter);
        if (entry == null) {
            SimpleObject response = new SimpleObject();
            response.put("created", false);
            return response;
        }
        SimpleObject response = toSimpleObject(entry);
        response.put("created", true);
        return response;
    }

    @RequestMapping(value = BASE + "/callnext", method = RequestMethod.POST)
    @ResponseBody
    public Object callNext(HttpServletRequest request) throws ResponseException {
        Location servicePoint = getLocationOrThrow(required(request, "servicePointUuid"), "servicePointUuid");
        Location visibleLocation = optionalLocation(request, "visibleLocationUuid");
        QueueEntry entry = getQueueService().callNextPatient(servicePoint, visibleLocation);
        if (entry == null) {
            SimpleObject response = new SimpleObject();
            response.put("called", false);
            return response;
        }
        SimpleObject response = toSimpleObject(entry);
        response.put("called", true);
        return response;
    }

    @RequestMapping(value = BASE + "/{uuid}/call", method = RequestMethod.POST)
    @ResponseBody
    public Object call(@PathVariable("uuid") String uuid) throws ResponseException {
        return toSimpleObject(getQueueService().callPatient(getQueueEntryOrThrow(uuid)));
    }

    @RequestMapping(value = BASE + "/{uuid}/start", method = RequestMethod.POST)
    @ResponseBody
    public Object start(@PathVariable("uuid") String uuid) throws ResponseException {
        return toSimpleObject(getQueueService().startService(getQueueEntryOrThrow(uuid)));
    }

    @RequestMapping(value = BASE + "/{uuid}/complete", method = RequestMethod.POST)
    @ResponseBody
    public Object complete(@PathVariable("uuid") String uuid) throws ResponseException {
        return toSimpleObject(getQueueService().completeService(getQueueEntryOrThrow(uuid)));
    }

    @RequestMapping(value = BASE + "/{uuid}/hold", method = RequestMethod.POST)
    @ResponseBody
    public Object hold(@PathVariable("uuid") String uuid, HttpServletRequest request) throws ResponseException {
        String reason = request.getParameter("reason");
        return toSimpleObject(getQueueService().putOnHold(getQueueEntryOrThrow(uuid), reason));
    }

    @RequestMapping(value = BASE + "/{uuid}/cancel", method = RequestMethod.POST)
    @ResponseBody
    public Object cancel(@PathVariable("uuid") String uuid, HttpServletRequest request) throws ResponseException {
        String reason = request.getParameter("reason");
        return toSimpleObject(getQueueService().cancelQueueEntry(getQueueEntryOrThrow(uuid), reason));
    }

    @RequestMapping(value = BASE + "/{uuid}/transfer", method = RequestMethod.POST)
    @ResponseBody
    public Object transfer(@PathVariable("uuid") String uuid, HttpServletRequest request) throws ResponseException {
        String destinationUuid = required(request, "destinationServicePointUuid");
        Location destination = getLocationOrThrow(destinationUuid, "destinationServicePointUuid");
        String reason = request.getParameter("reason");
        try {
            return toSimpleObject(getQueueService().transferPatient(getQueueEntryOrThrow(uuid), destination, reason));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalRequestException(e.getMessage());
        }
    }

    @RequestMapping(value = BASE + "/{uuid}/transferred", method = RequestMethod.POST)
    @ResponseBody
    public Object markTransferred(@PathVariable("uuid") String uuid, HttpServletRequest request) throws ResponseException {
        String reason = request.getParameter("reason");
        try {
            return toSimpleObject(getQueueService().markPatientTransferred(getQueueEntryOrThrow(uuid), reason));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalRequestException(e.getMessage());
        }
    }

    @RequestMapping(value = BASE + "/{uuid}/priority", method = RequestMethod.POST)
    @ResponseBody
    public Object updatePriority(@PathVariable("uuid") String uuid, HttpServletRequest request) throws ResponseException {
        String priorityName = required(request, "priority");
        QueuePriority priority;
        try {
            priority = QueuePriority.valueOf(priorityName.trim().toUpperCase(Locale.ENGLISH));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalRequestException("Invalid priority: " + priorityName);
        }
        return toSimpleObject(getQueueService().updatePriority(getQueueEntryOrThrow(uuid), priority));
    }

    // ------------------------------------------------------------------
    // Service points / service point <-> concept mapping (admin config)
    // ------------------------------------------------------------------

    @RequestMapping(value = BASE + "/servicepoints", method = RequestMethod.GET)
    @ResponseBody
    public Object servicePoints() throws ResponseException {
        List<SimpleObject> result = new ArrayList<>();
        for (Location location : getQueueService().getServicePointLocations()) {
            result.add(toSimpleObject(location));
        }
        SimpleObject response = new SimpleObject();
        response.put("results", result);
        return response;
    }

    @RequestMapping(value = BASE + "/servicepointmap", method = RequestMethod.GET)
    @ResponseBody
    public Object listServicePointMaps(HttpServletRequest request) throws ResponseException {
        boolean includeInactive = Boolean.parseBoolean(request.getParameter("includeInactive"));
        List<SimpleObject> result = new ArrayList<>();
        for (QueueServicePointConceptMap map : getQueueService().getAllServicePointConceptMaps(includeInactive)) {
            result.add(toSimpleObject(map));
        }
        SimpleObject response = new SimpleObject();
        response.put("results", result);
        return response;
    }

    @RequestMapping(value = BASE + "/servicepointmap/{uuid}", method = RequestMethod.GET)
    @ResponseBody
    public Object getServicePointMap(@PathVariable("uuid") String uuid) throws ResponseException {
        QueueServicePointConceptMap map = getQueueService().getServicePointConceptMapByUuid(uuid);
        if (map == null) {
            throw new IllegalRequestException("No queue service point mapping found with uuid " + uuid);
        }
        return toSimpleObject(map);
    }

    @RequestMapping(value = BASE + "/servicepointmap", method = RequestMethod.POST)
    @ResponseBody
    public Object saveServicePointMap(HttpServletRequest request) throws ResponseException {
        String conceptUuid = required(request, "conceptUuid");
        String servicePointUuid = required(request, "servicePointUuid");
        Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
        if (concept == null) {
            throw new IllegalRequestException("No concept found with uuid " + conceptUuid);
        }
        Location servicePoint = getLocationOrThrow(servicePointUuid, "servicePointUuid");

        String mapUuid = request.getParameter("uuid");
        QueueServicePointConceptMap map = StringUtils.isBlank(mapUuid)
                ? new QueueServicePointConceptMap()
                : getQueueService().getServicePointConceptMapByUuid(mapUuid);
        if (map == null) {
            throw new IllegalRequestException("No queue service point mapping found with uuid " + mapUuid);
        }
        map.setServiceRequestedConcept(concept);
        map.setServicePoint(servicePoint);
        String activeParam = request.getParameter("active");
        if (StringUtils.isNotBlank(activeParam)) {
            map.setActive(Boolean.parseBoolean(activeParam));
        }
        try {
            return toSimpleObject(getQueueService().saveServicePointConceptMap(map));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalRequestException(e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private QueueService getQueueService() {
        return Context.getService(QueueService.class);
    }

    private String required(HttpServletRequest request, String name) throws ResponseException {
        String value = request.getParameter(name);
        if (StringUtils.isBlank(value)) {
            throw new IllegalRequestException(name + " parameter is required");
        }
        return value.trim();
    }

    private Location getLocationOrThrow(String uuid, String paramName) throws ResponseException {
        Location location = Context.getLocationService().getLocationByUuid(uuid);
        if (location == null) {
            throw new IllegalRequestException("No location found for " + paramName + " " + uuid);
        }
        return location;
    }

    private Location optionalLocation(HttpServletRequest request, String name) throws ResponseException {
        String uuid = request.getParameter(name);
        return StringUtils.isBlank(uuid) ? null : getLocationOrThrow(uuid, name);
    }

    private QueueStatus optionalStatus(HttpServletRequest request, String name) throws ResponseException {
        String value = request.getParameter(name);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return QueueStatus.valueOf(value.trim().toUpperCase(Locale.ENGLISH));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalRequestException("Invalid status: " + value);
        }
    }

    private Date optionalDate(HttpServletRequest request, String name, Date defaultValue) throws ResponseException {
        String value = request.getParameter(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return dateFormat.parse(value.trim());
        }
        catch (ParseException e) {
            throw new IllegalRequestException("Invalid date for " + name + ", expected yyyy-MM-dd: " + value);
        }
    }

    private QueueEntry getQueueEntryOrThrow(String uuid) throws ResponseException {
        QueueEntry entry = getQueueService().getQueueEntryByUuid(uuid);
        if (entry == null) {
            throw new IllegalRequestException("No queue entry found with uuid " + uuid);
        }
        return entry;
    }

    private SimpleObject toSimpleObject(QueueEntry entry) {
        SimpleObject o = new SimpleObject();
        o.put("uuid", entry.getUuid());
        o.put("queueNumber", entry.getQueueNumber());
        o.put("status", entry.getStatusName());
        o.put("priority", entry.getPriorityName());
        o.put("comments", entry.getComments());
        o.put("patient", toSimpleObject(entry.getPatient()));
        o.put("visit", entry.getVisit() == null ? null : simpleRef(entry.getVisit().getUuid(), null));
        o.put("encounter", entry.getEncounter() == null ? null : simpleRef(entry.getEncounter().getUuid(), null));
        o.put("servicePoint", toSimpleObject(entry.getServicePoint()));
        o.put("previousServicePoint", toSimpleObject(entry.getPreviousServicePoint()));
        o.put("sessionLocation", toSimpleObject(entry.getSessionLocation()));
        o.put("serviceRequestedConcept", toSimpleObject(entry.getServiceRequestedConcept()));
        o.put("assignedProvider", toSimpleObject(entry.getAssignedProvider()));
        o.put("arrivalTime", entry.getArrivalTime());
        o.put("calledTime", entry.getCalledTime());
        o.put("serviceStartTime", entry.getServiceStartTime());
        o.put("serviceEndTime", entry.getServiceEndTime());
        o.put("completedTime", entry.getCompletedTime());
        o.put("dateCreated", entry.getDateCreated());
        o.put("dateChanged", entry.getDateChanged());
        return o;
    }

    private SimpleObject toSimpleObject(QueueStatusHistory history) {
        SimpleObject o = new SimpleObject();
        o.put("uuid", history.getUuid());
        o.put("previousStatus", history.getPreviousStatusName());
        o.put("newStatus", history.getNewStatusName());
        o.put("reason", history.getReason());
        o.put("changedBy", history.getChangedBy() == null ? null
                : simpleRef(history.getChangedBy().getUuid(), history.getChangedBy().getDisplayString()));
        o.put("dateChanged", history.getDateChanged());
        return o;
    }

    private SimpleObject toSimpleObject(QueueServicePointConceptMap map) {
        SimpleObject o = new SimpleObject();
        o.put("uuid", map.getUuid());
        o.put("active", map.getActive());
        o.put("serviceRequestedConcept", toSimpleObject(map.getServiceRequestedConcept()));
        o.put("servicePoint", toSimpleObject(map.getServicePoint()));
        return o;
    }

    private SimpleObject toSimpleObject(Location location) {
        if (location == null) {
            return null;
        }
        return simpleRef(location.getUuid(), location.getName());
    }

    private SimpleObject toSimpleObject(Concept concept) {
        if (concept == null) {
            return null;
        }
        String display;
        try {
            display = concept.getDisplayString();
        }
        catch (Exception e) {
            display = concept.getUuid();
        }
        return simpleRef(concept.getUuid(), display);
    }

    private SimpleObject toSimpleObject(Patient patient) {
        if (patient == null) {
            return null;
        }
        String display = patient.getPersonName() == null ? null : patient.getPersonName().getFullName();
        return simpleRef(patient.getUuid(), display);
    }

    private SimpleObject toSimpleObject(Provider provider) {
        if (provider == null) {
            return null;
        }
        return simpleRef(provider.getUuid(), provider.getName());
    }

    private SimpleObject simpleRef(String uuid, String display) {
        SimpleObject o = new SimpleObject();
        o.put("uuid", uuid);
        o.put("display", display);
        return o;
    }
}
