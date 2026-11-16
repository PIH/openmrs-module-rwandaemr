package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Obs;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Isolate;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Susceptibility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BacteriologyServiceTest {

	private final Map<String, Concept> concepts = new HashMap<String, Concept>();
	private BacteriologyService service;
	private Concept ecoli;
	private Concept ampicillin;
	private Concept gentamicin;
	private Concept sensitive;
	private Concept resistant;

	@BeforeEach
	public void setUp() {
		BacteriologyConfig config = mock(BacteriologyConfig.class);
		when(config.getConcept(anyString())).thenAnswer(invocation -> concept(invocation.getArgument(0)));
		service = new BacteriologyService(mock(DbSessionFactory.class), config);
		ecoli = concept("ecoli");
		ampicillin = concept("ampicillin");
		gentamicin = concept("gentamicin");
		sensitive = concept(BacteriologyConfig.SENSITIVE);
		resistant = concept(BacteriologyConfig.RESISTANT);
		answers(BacteriologyConfig.ORGANISM, ecoli);
		answers(BacteriologyConfig.ANTIBIOTIC, ampicillin, gentamicin);
		answers(BacteriologyConfig.INTERPRETATION, sensitive, concept(BacteriologyConfig.INTERMEDIATE), resistant);
	}

	@Test
	public void shouldReadTheOrganismsAndTheirSusceptibilityFromTheGroup() {
		Obs group = obs(BacteriologyConfig.SUSCEPTIBILITY, null);
		Obs isolate = obs(BacteriologyConfig.ISOLATE, null);
		isolate.addGroupMember(obs(BacteriologyConfig.ORGANISM, ecoli));
		isolate.addGroupMember(susceptibility(ampicillin, resistant));
		isolate.addGroupMember(susceptibility(gentamicin, sensitive));
		group.addGroupMember(isolate);

		List<Isolate> isolates = service.toIsolates(group);

		assertThat(isolates, hasSize(1));
		assertThat(isolates.get(0).getOrganism(), equalTo(ecoli));
		assertThat(isolates.get(0).getSusceptibilities(), hasSize(2));
		for (Susceptibility s : isolates.get(0).getSusceptibilities()) {
			assertThat(s.getInterpretation(), equalTo(s.getAntibiotic() == ampicillin ? resistant : sensitive));
		}
	}

	@Test
	public void shouldGiveTheCodeOfEachInterpretation() {
		assertThat(BacteriologyConfig.getInterpretationCode(sensitive), equalTo("S"));
		assertThat(BacteriologyConfig.getInterpretationCode(concept(BacteriologyConfig.INTERMEDIATE)), equalTo("I"));
		assertThat(BacteriologyConfig.getInterpretationCode(resistant), equalTo("R"));
	}

	private Obs susceptibility(Concept antibiotic, Concept interpretation) {
		Obs group = obs(BacteriologyConfig.ANTIBIOTIC_RESULT, null);
		group.addGroupMember(obs(BacteriologyConfig.ANTIBIOTIC, antibiotic));
		group.addGroupMember(obs(BacteriologyConfig.INTERPRETATION, interpretation));
		return group;
	}

	private Obs obs(String conceptUuid, Concept value) {
		Obs obs = new Obs();
		obs.setConcept(concept(conceptUuid));
		obs.setValueCoded(value);
		return obs;
	}

	private void answers(String questionUuid, Concept... answers) {
		Concept question = concept(questionUuid);
		for (Concept answer : answers) {
			question.addAnswer(new ConceptAnswer(answer));
		}
	}

	private Concept concept(String uuid) {
		return concepts.computeIfAbsent(uuid, u -> {
			Concept concept = new Concept();
			concept.setUuid(u);
			return concept;
		});
	}
}
