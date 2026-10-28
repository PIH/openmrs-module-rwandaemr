package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Isolate;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Susceptibility;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
		service = new BacteriologyService(mock(DbSessionFactory.class), mock(ObsService.class), mock(EncounterService.class), config);
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

	@Test
	public void shouldRequireTheOrganismOfEachIsolate() {
		Patient patient = new Patient(1);
		Isolate isolate = new Isolate();
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, sensitive));
		APIException e = assertThrows(APIException.class, () ->
				service.saveSusceptibility(order(patient), encounter(patient), Collections.singletonList(isolate)));
		assertThat(e.getMessage(), containsString("organism"));
	}

	@Test
	public void shouldRequireAResultForEachAntibioticTested() {
		Patient patient = new Patient(1);
		Isolate isolate = new Isolate();
		isolate.setOrganism(ecoli);
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, null));
		assertThrows(APIException.class, () ->
				service.saveSusceptibility(order(patient), encounter(patient), Collections.singletonList(isolate)));
	}

	@Test
	public void shouldNotAcceptTheSameAntibioticTwiceForAnOrganism() {
		Patient patient = new Patient(1);
		Isolate isolate = new Isolate();
		isolate.setOrganism(ecoli);
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, sensitive));
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, resistant));
		assertThrows(APIException.class, () ->
				service.saveSusceptibility(order(patient), encounter(patient), Collections.singletonList(isolate)));
	}

	@Test
	public void shouldNotRecordTheSusceptibilityOfAnOrderInAnotherPatientsEncounter() {
		assertThrows(APIException.class, () ->
				service.saveSusceptibility(order(new Patient(1)), encounter(new Patient(2)), Collections.emptyList()));
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

	private TestOrder order(Patient patient) {
		TestOrder order = new TestOrder();
		order.setPatient(patient);
		return order;
	}

	private Encounter encounter(Patient patient) {
		Encounter encounter = new Encounter();
		encounter.setPatient(patient);
		return encounter;
	}
}
