package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.api.VisitService;
import org.openmrs.module.appframework.context.AppContextModel;
import org.openmrs.module.appframework.domain.Extension;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.coreapps.contextmodel.AppContextModelGenerator;
import org.openmrs.module.htmlformentry.HtmlForm;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.module.rwandaemr.htmlformentry.HtmlFormEntrySetup;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.representation.CustomRepresentation;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is intended to be a temporary solution as we work to implement https://openmrs.atlassian.net/browse/O3-4647
 */
@Controller
public class RwandaEmrFormRestController {

    protected final Log log = LogFactory.getLog(getClass());

    public static final String REPRESENTATION = "(uuid,name,display,encounterType:(uuid,name,viewPrivilege,editPrivilege),version,published,retired,resources:(uuid,name,dataType,valueReference))";

    @Autowired
    LocationService locationService;

    @Autowired
    ProviderService providerService;

    @Autowired
    PatientService patientService;

    @Autowired
    VisitService visitService;

    @Autowired
    HtmlFormEntryService htmlFormEntryService;

    @Autowired
    AppContextModelGenerator appContextModelGenerator;

    @Autowired
    AppFrameworkService appFrameworkService;

    @RequestMapping(value = "/rest/v1/rwandaemr/patientforms", method = RequestMethod.GET)
    @ResponseBody
    public Object getPatientForms(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        Representation defaultRepresentation = new CustomRepresentation(REPRESENTATION);
        RequestContext requestContext = RestUtil.getRequestContext(request, response, defaultRepresentation);
        Representation rep = requestContext.getRepresentation();

        SimpleObject ret = new SimpleObject();
        List<Object> formReps = new ArrayList<>();
        ret.put("results", formReps);

        Patient patient = patientService.getPatientByUuid(requestContext.getParameter("patientUuid"));
        if (patient != null) {
            Visit visit = null;
            String visitUuid = requestContext.getParameter("visitUuid");
            if (StringUtils.isNotBlank(visitUuid)) {
                visit = visitService.getVisitByUuid(visitUuid);
            }
            UiSessionContext ctx = new UiSessionContext(locationService, providerService, request);
            String extensionPoint = request.getParameter("extensionPoint");
            if (StringUtils.isBlank(extensionPoint)) {
                extensionPoint = "patientDashboard.visitActions";
            }
            AppContextModel contextModel = appContextModelGenerator.generateAppContextModel(ctx, patient, visit);
            List<Extension> extensions = appFrameworkService.getExtensionsForCurrentUser(extensionPoint, contextModel);
            Collections.sort(extensions);

            List<HtmlForm> htmlForms = htmlFormEntryService.getAllHtmlForms();
            for (Extension extension : extensions) {
                String url = extension.getUrl();
                if (url != null) {
                    for (HtmlForm htmlForm : htmlForms) {
                        if (isFormIncluded(htmlForm, url)) {
                            Object formRep = ConversionUtil.convertToRepresentation(htmlForm.getForm(), rep);
                            formReps.add(formRep);
                        }
                    }
                }
            }
        }

        return ret;
    }

    public boolean isFormIncluded(HtmlForm htmlForm, String extensionUrl) {
        if (extensionUrl.contains(htmlForm.getUuid())) {
            return true;
        }
        else if (extensionUrl.contains(htmlForm.getForm().getUuid())) {
            return true;
        }
        String configFileName = HtmlFormEntrySetup.CONFIG_FORMS.get(htmlForm.getUuid());
        return configFileName != null && extensionUrl.contains(configFileName);
    }
}