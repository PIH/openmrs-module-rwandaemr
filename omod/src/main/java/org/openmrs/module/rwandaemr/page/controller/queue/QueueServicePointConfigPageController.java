package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.rwandaemr.queue.QueueService;
import org.openmrs.module.rwandaemr.queue.model.QueueServicePointConceptMap;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class QueueServicePointConfigPageController extends QueuePageSupport {

    static final String SERVICE_REQUESTED_CONCEPT_GP = "registration.serviceRequestedConcept";

    static final String DEFAULT_SERVICE_REQUESTED_CONCEPT = "6702";

    public void get(PageModel model,
                    @SpringBean QueueService queueService) {
        if (!Context.hasPrivilege(org.openmrs.module.rwandaemr.queue.QueuePrivileges.CONFIGURE)) {
            model.addAttribute("authorized", false);
            return;
        }
        Concept serviceRequestedConcept = getServiceRequestedConcept();
        List<Concept> serviceRequestedOptions = getServiceRequestedOptions(serviceRequestedConcept);
        model.addAttribute("authorized", true);
        model.addAttribute("servicePoints", queueService.getServicePointLocations());
        model.addAttribute("serviceRequestedConcept", serviceRequestedConcept);
        model.addAttribute("serviceRequestedOptions", serviceRequestedOptions);
        model.addAttribute("serviceRequestedConceptError",
                getServiceRequestedConceptError(serviceRequestedConcept, serviceRequestedOptions));
        model.addAttribute("conceptMaps", queueService.getAllServicePointConceptMaps(false));
    }

    public String post(UiUtils ui,
                       UiSessionContext sessionContext,
                       @SpringBean QueueService queueService,
                       @RequestParam(value = "conceptUuid", required = false) String conceptUuid,
                       @RequestParam(value = "servicePointId", required = false) Integer servicePointId) {
        try {
            Concept serviceRequestedConcept = getServiceRequestedConcept();
            List<Concept> serviceRequestedOptions = getServiceRequestedOptions(serviceRequestedConcept);
            String configurationError = getServiceRequestedConceptError(
                    serviceRequestedConcept, serviceRequestedOptions);
            if (configurationError != null) {
                throw new IllegalStateException(configurationError);
            }
            Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
            Location servicePoint = getLocation(servicePointId);
            if (concept == null) {
                throw new IllegalArgumentException("Service Requested concept was not found");
            }
            if (!ServiceRequestedConceptOptions.contains(serviceRequestedConcept, concept)) {
                throw new IllegalArgumentException(
                        "Service Requested must be an answer of the configured coded concept");
            }
            if (servicePoint == null) {
                throw new IllegalArgumentException("Login Location service point was not found");
            }
            QueueServicePointConceptMap conceptMap = new QueueServicePointConceptMap();
            conceptMap.setServiceRequestedConcept(concept);
            conceptMap.setServicePoint(servicePoint);
            queueService.saveServicePointConceptMap(conceptMap);
            setToast(sessionContext, "Queue service mapping saved");
        }
        catch (Exception e) {
            setError(sessionContext, e.getMessage());
        }
        return redirect(ui, "queue/queueServicePointConfig");
    }

    protected Concept getServiceRequestedConcept() {
        String configuredConcept = Context.getAdministrationService().getGlobalProperty(
                SERVICE_REQUESTED_CONCEPT_GP, DEFAULT_SERVICE_REQUESTED_CONCEPT);
        configuredConcept = StringUtils.isBlank(configuredConcept)
                ? DEFAULT_SERVICE_REQUESTED_CONCEPT : configuredConcept;
        try {
            return HtmlFormEntryUtil.getConcept(configuredConcept);
        }
        catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<Concept> getServiceRequestedOptions(Concept serviceRequestedConcept) {
        return serviceRequestedConcept == null
                ? Collections.<Concept>emptyList()
                : ServiceRequestedConceptOptions.from(serviceRequestedConcept);
    }

    private String getServiceRequestedConceptError(Concept serviceRequestedConcept,
                                                    List<Concept> serviceRequestedOptions) {
        if (serviceRequestedConcept == null) {
            return "The concept configured by " + SERVICE_REQUESTED_CONCEPT_GP + " was not found";
        }
        if (!ServiceRequestedConceptOptions.isCoded(serviceRequestedConcept)) {
            return "The concept configured by " + SERVICE_REQUESTED_CONCEPT_GP + " must be coded";
        }
        if (serviceRequestedOptions.isEmpty()) {
            return "The configured Service Requested concept has no active answers";
        }
        return null;
    }
}
