package org.openmrs.module.rwandaemr.page.controller.patient;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.TestOrder;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.radiology.HL7Utils;
import org.openmrs.module.rwandaemr.radiology.Modality;
import org.openmrs.module.rwandaemr.radiology.RadiologyConfig;
import org.openmrs.module.rwandaemr.radiology.RadiologyHl7MessageLog;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderCancelService;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportText;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RadiologyOrderPageController {

    private static final int MAX_PACS_MESSAGES = 50;

    public void get(PageModel model, UiUtils ui,
                    @InjectBeans PatientDomainWrapper patientDomainWrapper,
                    @RequestParam(value = "orderId") Order order,
                    @SpringBean("radiologyConfig") RadiologyConfig radiologyConfig,
                    @SpringBean("rwandaEmrService") RwandaEmrService rwandaEmrService,
                    @SpringBean("conceptService") ConceptService conceptService,
                    @SpringBean("orderService") OrderService orderService,
                    @SpringBean RadiologyReportService radiologyReportService,
                    @SpringBean RadiologyOrderCancelService radiologyOrderCancelService,
                    @SpringBean RadiologyHl7MessageLog radiologyHl7MessageLog) throws IOException {

        model.addAttribute("order", order);
        model.addAttribute("canCancel", Context.hasPrivilege(RadiologyOrderCancelService.PRIVILEGE_CANCEL_ORDER)
                && radiologyOrderCancelService.canCancel(order));
        model.addAttribute("cancellation", radiologyOrderCancelService.getCancellation(order));
        model.addAttribute("cancellationSent", RadiologyOrderEventListener.CANCELLATION_SENT.equals(order.getFulfillerComment()));
        model.addAttribute("pacsMessages", radiologyHl7MessageLog.getMessagesForOrder(order, MAX_PACS_MESSAGES));
        // Reports for some modalities, such as ultrasound, are entered here by a radiologist
        boolean mayEnterReports = Context.hasPrivilege(RadiologyReportService.PRIVILEGE_ENTER_REPORT);
        model.addAttribute("canEnterReport", mayEnterReports && radiologyReportService.canEnterReport(order));
        // A draft report is only shown to radiologists, and never to clinicians
        model.addAttribute("draft", mayEnterReports ? radiologyReportService.getDraft(order) : null);
        model.addAttribute("reportWaitingForImages", mayEnterReports && radiologyReportService.requiresManualReport(order)
                && !Boolean.TRUE.equals(order.getVoided()) && order.getDateStopped() == null
                && !radiologyReportService.hasImagesFromPacs(order));

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
        model.addAttribute("studyDone", false);
        model.addAttribute("reportDate", null);
        model.addAttribute("reportStatus", null);
        model.addAttribute("reportText", null);
        model.addAttribute("reportHtml", null);

        List<Obs> obs = rwandaEmrService.getObsByOrder(order);
        Obs studyGroup = getObs(obs, radiologyConfig.getRadiologyStudyConstruct());
        if (studyGroup != null) {
            model.addAttribute("studyDone", true);
            Obs imagesObs = getObs(studyGroup.getGroupMembers(), radiologyConfig.getRadiologyImagesAvailable());
            boolean imagesAvailable = imagesObs != null && OpenmrsUtil.nullSafeEquals(imagesObs.getValueCoded(), trueConcept);
            String imageUrl = imagesAvailable ? HL7Utils.getViewerUrl(imagesObs.getComment()) : null;
            // PACS only sends the link with the report, so otherwise the study is opened in the viewer by its accession number
            model.addAttribute("imageUrl", imageUrl != null ? imageUrl : radiologyConfig.getImageViewerUrl(order.getAccessionNumber()));
        }
        Obs reportGroup = getObs(obs, radiologyConfig.getRadiologyReportConstruct());
        if (reportGroup != null) {
            model.addAttribute("reportDate", reportGroup.getEncounter().getEncounterDatetime());
            Obs reportStatus = getObs(reportGroup.getGroupMembers(), radiologyConfig.getRadiologyReportType());
            model.addAttribute("reportStatus", reportStatus == null ? null : reportStatus.getValueCoded());
            Obs reportText = getObs(reportGroup.getGroupMembers(), radiologyConfig.getRadiologyReportComments());
            model.addAttribute("reportText", reportText == null ? null : reportText.getValueText());
            model.addAttribute("reportHtml", reportText == null ? null : RadiologyReportText.toHtml(reportText.getValueText()));
        }
    }

    /**
     * Cancels the order, with the reason given
     */
    public String post(UiUtils ui, UiSessionContext sessionContext,
                       @RequestParam(value = "orderId") Order order,
                       @RequestParam(value = "action") String action,
                       @RequestParam(value = "cancelReason", required = false) String cancelReason,
                       @SpringBean RadiologyOrderCancelService radiologyOrderCancelService) {
        String error = null;
        if (!"cancel".equals(action)) {
            error = "Unknown action: " + action;
        }
        else if (!Context.hasPrivilege(RadiologyOrderCancelService.PRIVILEGE_CANCEL_ORDER)) {
            error = "You do not have permission to cancel radiology orders.";
        }
        else {
            try {
                radiologyOrderCancelService.cancel(order, cancelReason);
                sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_INFO_MESSAGE,
                        "Order " + order.getOrderNumber() + " cancelled. The cancellation is sent to PACS.");
                sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_TOAST_MESSAGE, "true");
            }
            catch (RuntimeException e) {
                error = e.getMessage();
            }
        }
        if (error != null) {
            sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE, error);
        }
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("orderId", order.getOrderId());
        return "redirect:" + ui.pageLink("rwandaemr", "patient/radiologyOrder", params);
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
