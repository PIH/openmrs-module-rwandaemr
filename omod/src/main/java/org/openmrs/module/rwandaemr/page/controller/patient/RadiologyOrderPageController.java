package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadiologyOrderPageController {

    public void get(PageModel model, UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "orderId") Order order,
                    @SpringBean("radiologyConfig") RadiologyConfig radiologyConfig,
                    @SpringBean("rwandaEmrService") RwandaEmrService rwandaEmrService,
                    @SpringBean("conceptService") ConceptService conceptService,
                    @SpringBean("orderService") OrderService orderService) throws IOException {

        model.addAttribute("order", order);

        patientDomainWrapper.setPatient(order.getPatient());
        model.addAttribute("patient", patientDomainWrapper);

        Map<Modality, Concept> modalityConceptSets = radiologyConfig.getModalityConceptSets();
        model.addAttribute("modalityConceptSets", modalityConceptSets);

        Map<Concept, Modality> orderables = radiologyConfig.getOrderables(modalityConceptSets);
        model.addAttribute("orderables", orderables);

        Set<OrderType> testOrderTypes = new HashSet<>();
        for (OrderType orderType : orderService.getOrderTypes(true)) {
            if (TestOrder.class.isAssignableFrom(orderType.getJavaClass())) {
                testOrderTypes.add(orderType);
            }
        }
        model.addAttribute("testOrderTypes", testOrderTypes);

        Concept trueConcept = conceptService.getTrueConcept();
        model.addAttribute("imageUrl", null);
        model.addAttribute("reportDate", null);
        model.addAttribute("reportStatus", null);
        model.addAttribute("reportText", null);

        List<Obs> obs = rwandaEmrService.getObsByOrder(order);
        Obs studyGroup = getObs(obs, radiologyConfig.getRadiologyStudyConstruct());
        if (studyGroup != null) {
            Obs imagesObs = getObs(studyGroup.getGroupMembers(), radiologyConfig.getRadiologyImagesAvailable());
            boolean imagesAvailable = imagesObs != null && OpenmrsUtil.nullSafeEquals(imagesObs.getValueCoded(), trueConcept);
            if (imagesAvailable) {
                model.addAttribute("imageUrl", imagesObs.getComment());
            }
        }
        Obs reportGroup = getObs(obs, radiologyConfig.getRadiologyReportConstruct());
        if (reportGroup != null) {
            model.addAttribute("reportDate", reportGroup.getEncounter().getEncounterDatetime());
            Obs reportStatus = getObs(reportGroup.getGroupMembers(), radiologyConfig.getRadiologyReportType());
            model.addAttribute("reportStatus", reportStatus == null ? null : reportStatus.getValueCoded());
            Obs reportText = getObs(reportGroup.getGroupMembers(), radiologyConfig.getRadiologyReportComments());
            model.addAttribute("reportText", reportText == null ? null : reportText.getValueText());
        }
    }

    Obs getObs(Collection<Obs> obsList, Concept c) {
        if (obsList != null) {
            for (Obs obs : obsList) {
                if (obs.getConcept().equals(c)) {
                    return obs;
                }
            }
        }
        return null;
    }
}
