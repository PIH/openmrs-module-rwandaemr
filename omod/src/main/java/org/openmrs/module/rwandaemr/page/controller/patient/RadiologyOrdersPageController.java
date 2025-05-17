package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.OrderService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.parameter.OrderSearchCriteriaBuilder;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadiologyOrdersPageController {

    public void get(PageModel model, UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "patientId") Patient patient,
                    @SpringBean("radiologyConfig") RadiologyConfig radiologyConfig,
                    @SpringBean("orderService") OrderService orderService) throws IOException {

        patientDomainWrapper.setPatient(patient);
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

        List<Order> orders = new ArrayList<>();
        if (!orderables.isEmpty()) {
            OrderSearchCriteriaBuilder b = new OrderSearchCriteriaBuilder();
            b.setPatient(patient);
            b.setConcepts(orderables.keySet());
            b.setIncludeVoided(false);
            orders = orderService.getOrders(b.build());
        }
        model.addAttribute("orders", orders);
    }
}
