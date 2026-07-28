package org.openmrs.module.rwandaemr.page.controller.queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptDatatype;

public class ServiceRequestedConceptOptionsTest {

    @Test
    public void shouldReturnActiveCodedAnswersInConfiguredOrder() {
        Concept question = codedConcept(6702, "service-requested");
        Concept triage = concept(11, "triage");
        Concept retired = concept(12, "retired");
        retired.setRetired(true);
        Concept consultation = concept(13, "consultation");
        question.addAnswer(new ConceptAnswer(triage));
        question.addAnswer(new ConceptAnswer(retired));
        question.addAnswer(new ConceptAnswer(consultation));

        List<Concept> options = ServiceRequestedConceptOptions.from(question);

        assertThat(ids(options), contains(11, 13));
        assertThat(ServiceRequestedConceptOptions.contains(question, triage), is(true));
        assertThat(ServiceRequestedConceptOptions.contains(question, retired), is(false));
    }

    @Test
    public void shouldRejectAnswersFromANonCodedQuestion() {
        Concept question = concept(6702, "service-requested");
        Concept answer = concept(11, "triage");
        question.addAnswer(new ConceptAnswer(answer));

        assertThat(ServiceRequestedConceptOptions.from(question), empty());
        assertThat(ServiceRequestedConceptOptions.contains(question, answer), is(false));
    }

    private Concept codedConcept(int id, String uuid) {
        ConceptDatatype datatype = new ConceptDatatype();
        datatype.setHl7Abbreviation(ConceptDatatype.CODED);
        datatype.setUuid(ConceptDatatype.CODED_UUID);
        Concept concept = concept(id, uuid);
        concept.setDatatype(datatype);
        return concept;
    }

    private Concept concept(int id, String uuid) {
        Concept concept = new Concept(id);
        concept.setUuid(uuid);
        concept.setRetired(false);
        return concept;
    }

    private List<Integer> ids(List<Concept> concepts) {
        return concepts.stream().map(Concept::getId).collect(Collectors.toList());
    }
}
