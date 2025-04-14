package org.openmrs.module.rwandaemr.page.controller.patient;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.module.emrapi.patient.PatientDomainWrapper;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.InjectBeans;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DrugAdministrationsPageController {

    public static final String GROUPING_CONCEPT = "2a415d0a-4095-450e-bec3-a9afa624840c";
    public static final String DRUG_CONCEPT = "ad38288b-26a9-4337-b777-a289334ed5b4";
    public static final String FREQUENCY_CONCEPT = "5e152a81-8635-41d6-aef2-2527a5f584f3";
    public static final String DATE_CONCEPT = "249b6f2a-3f58-45ca-8a8f-a758ebfc9682";
    public static final String ROUTE_CONCEPT = "1e0eaaf0-3452-4bbf-8f05-420751d719d8";

    public void get(PageModel model, UiUtils ui,
                      @InjectBeans PatientDomainWrapper patientDomainWrapper,
                      @RequestParam(value = "patientId") Patient patient,
                      @SpringBean("conceptService") ConceptService conceptService,
                      @SpringBean("obsService") ObsService obsService) throws IOException {

        patientDomainWrapper.setPatient(patient);
        model.addAttribute("patient", patientDomainWrapper);

        // Drug administration is track as obs within an obs group
        Concept groupingConcept = conceptService.getConceptByReference(GROUPING_CONCEPT);
        Concept drugConcept = conceptService.getConceptByReference(DRUG_CONCEPT);
        Concept frequencyConcept = conceptService.getConceptByReference(FREQUENCY_CONCEPT);
        Concept dateConcept = conceptService.getConceptByReference(DATE_CONCEPT);
        Concept routeConcept = conceptService.getConceptByReference(ROUTE_CONCEPT);

        List<DrugAdministration> drugAdministrations = new ArrayList<>();
        List<Obs> groupObs = obsService.getObservationsByPersonAndConcept(patient, groupingConcept);
        for (Obs group : groupObs) {
            DrugAdministration drugAdministration = new DrugAdministration();
            drugAdministration.setGroup(group);
            for (Obs member : group.getGroupMembers()) {
                Concept memberConcept = member.getConcept();
                if (memberConcept.equals(drugConcept)) {
                    drugAdministration.setDrug(member);
                }
                else if (memberConcept.equals(frequencyConcept)) {
                    drugAdministration.setFrequency(member);
                }
                else if (memberConcept.equals(dateConcept)) {
                    drugAdministration.setDate(member);
                }
                else if (memberConcept.equals(routeConcept)) {
                    drugAdministration.setRoute(member);
                }
            }
            drugAdministrations.add(drugAdministration);
        }

        drugAdministrations.sort(Comparator
                .comparing((DrugAdministration a) -> a.getGroup().getEncounter().getEncounterDatetime())
                .thenComparingInt(a -> a.getGroup().getId()).reversed());

        model.put("drugAdministrations", drugAdministrations);
    }

    @Data
    public static class DrugAdministration {
        private Obs group;
        private Obs drug;
        private Obs frequency;
        private Obs date;
        private Obs route;
    }
}
