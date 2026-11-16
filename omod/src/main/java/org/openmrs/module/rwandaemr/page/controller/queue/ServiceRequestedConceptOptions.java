package org.openmrs.module.rwandaemr.page.controller.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;

final class ServiceRequestedConceptOptions {

    private ServiceRequestedConceptOptions() {
    }

    static List<Concept> from(Concept question) {
        if (!isCoded(question)) {
            return Collections.emptyList();
        }
        List<ConceptAnswer> configuredAnswers = new ArrayList<ConceptAnswer>(question.getAnswers());
        Collections.sort(configuredAnswers);
        Map<String, Concept> answers = new LinkedHashMap<String, Concept>();
        for (ConceptAnswer answer : configuredAnswers) {
            Concept answerConcept = answer.getAnswerConcept();
            String key = conceptKey(answerConcept);
            if (key != null && !Boolean.TRUE.equals(answerConcept.getRetired()) && !answers.containsKey(key)) {
                answers.put(key, answerConcept);
            }
        }
        return new ArrayList<Concept>(answers.values());
    }

    static boolean contains(Concept question, Concept concept) {
        String selectedKey = conceptKey(concept);
        if (selectedKey == null) {
            return false;
        }
        for (Concept answer : from(question)) {
            if (selectedKey.equals(conceptKey(answer))) {
                return true;
            }
        }
        return false;
    }

    static boolean isCoded(Concept concept) {
        return concept != null && concept.getDatatype() != null && concept.getDatatype().isCoded();
    }

    private static String conceptKey(Concept concept) {
        if (concept == null) {
            return null;
        }
        if (concept.getId() != null) {
            return "id:" + concept.getId();
        }
        return StringUtils.isBlank(concept.getUuid()) ? null : "uuid:" + concept.getUuid();
    }
}
