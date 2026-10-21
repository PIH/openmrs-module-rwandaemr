package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.rwandaemr.labs.AntibiogramService.Isolate;
import org.openmrs.module.rwandaemr.labs.AntibiogramService.Susceptibility;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AntibiogramServiceTest {

	private AntibiogramService service;
	private Concept ecoli;
	private Concept ampicillin;
	private Concept gentamicin;
	private Concept sensitive;
	private Concept resistant;

	@BeforeEach
	public void setUp() {
		AntibiogramConfig config = mock(AntibiogramConfig.class);
		when(config.getConcept(anyString())).thenAnswer(invocation -> concept(invocation.getArgument(0)));
		service = new AntibiogramService(mock(DbSessionFactory.class), mock(ObsService.class), mock(EncounterService.class), config);
		ecoli = concept("ecoli");
		ampicillin = concept("ampicillin");
		gentamicin = concept("gentamicin");
		sensitive = concept(AntibiogramConfig.SENSITIVE);
		resistant = concept(AntibiogramConfig.RESISTANT);
	}

	@Test
	public void shouldReadTheOrganismsAndTheirSusceptibilityFromTheAntibiogramGroup() {
		Obs antibiogram = obs(AntibiogramConfig.ANTIBIOGRAM, null);
		Obs isolate = obs(AntibiogramConfig.ISOLATE, null);
		isolate.addGroupMember(obs(AntibiogramConfig.ORGANISM, ecoli));
		isolate.addGroupMember(susceptibility(ampicillin, resistant));
		isolate.addGroupMember(susceptibility(gentamicin, sensitive));
		antibiogram.addGroupMember(isolate);

		List<Isolate> isolates = service.toIsolates(antibiogram);

		assertThat(isolates, hasSize(1));
		assertThat(isolates.get(0).getOrganism(), equalTo(ecoli));
		assertThat(isolates.get(0).getSusceptibilities(), hasSize(2));
		for (Susceptibility s : isolates.get(0).getSusceptibilities()) {
			assertThat(s.getInterpretation(), equalTo(s.getAntibiotic() == ampicillin ? resistant : sensitive));
		}
	}

	@Test
	public void shouldGiveTheCodeOfEachInterpretation() {
		assertThat(AntibiogramConfig.getInterpretationCode(sensitive), equalTo("S"));
		assertThat(AntibiogramConfig.getInterpretationCode(concept(AntibiogramConfig.INTERMEDIATE)), equalTo("I"));
		assertThat(AntibiogramConfig.getInterpretationCode(resistant), equalTo("R"));
	}

	@Test
	public void shouldRequireTheOrganismOfEachIsolate() {
		Patient patient = new Patient(1);
		Isolate isolate = new Isolate();
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, sensitive));
		APIException e = assertThrows(APIException.class, () ->
				service.saveAntibiogram(order(patient), encounter(patient), Collections.singletonList(isolate)));
		assertThat(e.getMessage(), containsString("organism"));
	}

	@Test
	public void shouldRequireAResultForEachAntibioticTested() {
		Patient patient = new Patient(1);
		Isolate isolate = new Isolate();
		isolate.setOrganism(ecoli);
		isolate.getSusceptibilities().add(new Susceptibility(ampicillin, null));
		assertThrows(APIException.class, () ->
				service.saveAntibiogram(order(patient), encounter(patient), Collections.singletonList(isolate)));
	}

	@Test
	public void shouldNotRecordTheAntibiogramOfAnOrderInAnotherPatientsEncounter() {
		assertThrows(APIException.class, () ->
				service.saveAntibiogram(order(new Patient(1)), encounter(new Patient(2)), Collections.emptyList()));
	}

	private Obs susceptibility(Concept antibiotic, Concept interpretation) {
		Obs group = obs(AntibiogramConfig.SUSCEPTIBILITY, null);
		group.addGroupMember(obs(AntibiogramConfig.ANTIBIOTIC, antibiotic));
		group.addGroupMember(obs(AntibiogramConfig.INTERPRETATION, interpretation));
		return group;
	}

	private Obs obs(String conceptUuid, Concept value) {
		Obs obs = new Obs();
		obs.setConcept(concept(conceptUuid));
		obs.setValueCoded(value);
		return obs;
	}

	private Concept concept(String uuid) {
		Concept concept = new Concept();
		concept.setUuid(uuid);
		return concept;
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
