package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.FormService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.adt.AdtService;
import org.openmrs.module.emrapi.visit.VisitDomainWrapper;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestUtil;
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
import java.util.List;

/**
 * This is intended to be a temporary solution as we work to implement https://openmrs.atlassian.net/browse/O3-4647
 */
@Controller
public class RwandaEmrFormRestController {

    protected final Log log = LogFactory.getLog(getClass());

    @Autowired
    AdtService adtService;

    @Autowired
    LocationService locationService;

    @Autowired
    ProviderService providerService;

    @Autowired
    PatientService patientService;

    @Autowired
    FormService formService;

    @RequestMapping(value = "/rest/v1/rwandaemr/patientforms", method = RequestMethod.GET)
    @ResponseBody
    public Object getPatientForms(HttpServletRequest request, HttpServletResponse response) throws ResponseException {
        RequestContext requestContext = RestUtil.getRequestContext(request, response);
        Representation rep = requestContext.getRepresentation();

        SimpleObject ret = new SimpleObject();
        List<Object> formReps = new ArrayList<>();
        ret.put("results", formReps);

        Patient patient = patientService.getPatientByUuid(requestContext.getParameter("patientUuid"));
        if (patient != null) {
            UiSessionContext uiSessionContext = new UiSessionContext(locationService, providerService, request);
            Location location = uiSessionContext.getSessionLocation();

            // TODO: Review why we have both these tags and how we want to consolidate
            boolean atInpatientLocation = location.hasTag("Inpatient Location");
            boolean atAdmissionLocation = location.hasTag("Admission Location");
            boolean atMaternityLocation = location.hasTag("Maternity Location");
            boolean atNeonatologyLocation = location.hasTag("Neonatology Location");

            VisitDomainWrapper currentVisit = adtService.getActiveVisit(patient, location);
            boolean isAdmitted = currentVisit != null && currentVisit.isAdmitted();

            List<Form> forms = new ArrayList<>();

            // TODO: Get rid of this hard-coding, this is just a POC, this should ideally come from form configuration
            if (isAdmitted) {
                if (atInpatientLocation || atAdmissionLocation) {
                    forms.add(formService.getFormByUuid("79e1d4a0-a747-4431-be7f-dbad463a5c82")); // auto-vital-signs.xml
                    forms.add(formService.getFormByUuid("b248baa0-4093-4be7-8ef5-d542da648466")); // ipd-clinical-examination.xml
                    forms.add(formService.getFormByUuid("326aad65-e28a-41ca-b61e-76c6cc673f46")); // ipd-general-followup.xml
                    if (atMaternityLocation) {
                        forms.add(formService.getFormByUuid("88589514-deee-4e26-ad5a-512da2db9f05")); // ipd-maternity-admission-midwife.xml
                        forms.add(formService.getFormByUuid("544a9be9-f1a1-467c-a62a-72b98a81da6b")); // ipd-maternity-medical-assessment.xml
                        forms.add(formService.getFormByUuid("544a9be9-f1a1-467c-a62a-72b98a81da6b")); // ipd-maternity-clinical-examination.xml
                    }
                    if (atNeonatologyLocation) {
                        forms.add(formService.getFormByUuid("a9f70c7c-244d-450d-9285-429358254a0a")); // ipd-neo-triage-form.xml
                        forms.add(formService.getFormByUuid("10181c49-7050-4789-8ee3-a85f1c17e20b")); // ipd-neonatal-admission.xml
                        forms.add(formService.getFormByUuid("d5294453-d3f6-482a-a568-e07ea459b3f0")); // ipd-neo-clinical-examination.xml
                    }
                }
            }

            for (Form form : forms) {
                Object formRep = ConversionUtil.convertToRepresentation(form, rep);
                formReps.add(formRep);
            }
        }

        return ret;
    }
}