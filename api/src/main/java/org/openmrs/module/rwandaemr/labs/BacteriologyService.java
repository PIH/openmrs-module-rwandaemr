package org.openmrs.module.rwandaemr.labs;

import org.hibernate.Criteria;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hibernate.criterion.Restrictions.eq;

/**
 * Reads and records the antimicrobial susceptibility of a bacteriology exam: the organisms isolated and, for each, the
 * antibiotics tested and whether the organism is sensitive, intermediate or resistant to them.  It is recorded as one obs
 * group linked to the order, in the encounter that fulfills the order.  See {@link BacteriologyConfig}.
 */
@Component
public class BacteriologyService {

	private final DbSessionFactory dbSessionFactory;
	private final ObsService obsService;
	private final EncounterService encounterService;
	private final BacteriologyConfig config;

	public BacteriologyService(@Autowired DbSessionFactory dbSessionFactory,
							   @Autowired ObsService obsService,
							   @Autowired EncounterService encounterService,
							   @Autowired BacteriologyConfig config) {
		this.dbSessionFactory = dbSessionFactory;
		this.obsService = obsService;
		this.encounterService = encounterService;
		this.config = config;
	}

	/**
	 * @return the organisms isolated for the order, with their susceptibility, or an empty list
	 */
	@Transactional(readOnly = true)
	public List<Isolate> getSusceptibility(Order order) {
		List<Obs> groups = getGroupObs(order, BacteriologyConfig.SUSCEPTIBILITY);
		return groups.isEmpty() ? Collections.<Isolate>emptyList() : toIsolates(groups.get(0));
	}

	/**
	 * Replaces the antimicrobial susceptibility of the order with the given isolates.  An empty list removes it.
	 */
	@Transactional
	public void saveSusceptibility(Order order, Encounter encounter, List<Isolate> isolates) {
		checkSamePatient(order, encounter);
		Concept organismQuestion = required(BacteriologyConfig.ORGANISM);
		Concept antibioticQuestion = required(BacteriologyConfig.ANTIBIOTIC);
		Concept interpretationQuestion = required(BacteriologyConfig.INTERPRETATION);
		Set<Concept> organisms = new HashSet<Concept>();
		for (Isolate isolate : isolates) {
			requireAnswer(organismQuestion, isolate.getOrganism(), "Each isolate needs the organism isolated");
			if (!organisms.add(isolate.getOrganism())) {
				throw new APIException("The same organism is entered twice: " + name(isolate.getOrganism()));
			}
			Set<Concept> antibiotics = new HashSet<Concept>();
			for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
				requireAnswer(antibioticQuestion, susceptibility.getAntibiotic(), "Each result needs the antibiotic tested");
				requireAnswer(interpretationQuestion, susceptibility.getInterpretation(),
						"Each antibiotic tested needs its result: sensitive, intermediate or resistant");
				if (!antibiotics.add(susceptibility.getAntibiotic())) {
					throw new APIException(name(susceptibility.getAntibiotic()) + " is entered twice for " + name(isolate.getOrganism()));
				}
			}
		}
		replaceGroup(order, encounter, BacteriologyConfig.SUSCEPTIBILITY, isolates.isEmpty() ? null : () -> {
			Obs group = newObs(required(BacteriologyConfig.SUSCEPTIBILITY), encounter, order);
			for (Isolate isolate : isolates) {
				Obs isolateObs = newObs(required(BacteriologyConfig.ISOLATE), encounter, order);
				isolateObs.addGroupMember(coded(organismQuestion, isolate.getOrganism(), encounter, order));
				for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
					Obs result = newObs(required(BacteriologyConfig.ANTIBIOTIC_RESULT), encounter, order);
					result.addGroupMember(coded(antibioticQuestion, susceptibility.getAntibiotic(), encounter, order));
					result.addGroupMember(coded(interpretationQuestion, susceptibility.getInterpretation(), encounter, order));
					isolateObs.addGroupMember(result);
				}
				group.addGroupMember(isolateObs);
			}
			return group;
		});
	}

	List<Isolate> toIsolates(Obs group) {
		List<Isolate> ret = new ArrayList<Isolate>();
		for (Obs isolateObs : group.getGroupMembers(false)) {
			if (!BacteriologyConfig.ISOLATE.equals(isolateObs.getConcept().getUuid())) {
				continue;
			}
			Isolate isolate = new Isolate();
			for (Obs member : isolateObs.getGroupMembers(false)) {
				String conceptUuid = member.getConcept().getUuid();
				if (BacteriologyConfig.ORGANISM.equals(conceptUuid)) {
					isolate.setOrganism(member.getValueCoded());
				}
				else if (BacteriologyConfig.ANTIBIOTIC_RESULT.equals(conceptUuid)) {
					Susceptibility susceptibility = new Susceptibility();
					for (Obs value : member.getGroupMembers(false)) {
						if (BacteriologyConfig.ANTIBIOTIC.equals(value.getConcept().getUuid())) {
							susceptibility.setAntibiotic(value.getValueCoded());
						}
						else if (BacteriologyConfig.INTERPRETATION.equals(value.getConcept().getUuid())) {
							susceptibility.setInterpretation(value.getValueCoded());
						}
					}
					isolate.getSusceptibilities().add(susceptibility);
				}
			}
			ret.add(isolate);
		}
		return ret;
	}

	/**
	 * Voids the existing groups of the concept for the order, and saves the new one if any
	 */
	private void replaceGroup(Order order, Encounter encounter, String groupConcept, GroupBuilder newGroup) {
		for (Obs existing : getGroupObs(order, groupConcept)) {
			voidWithMembers(existing, "Bacteriology result replaced");
		}
		if (newGroup != null) {
			encounter.addObs(newGroup.build());
			encounterService.saveEncounter(encounter);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Obs> getGroupObs(Order order, String conceptUuid) {
		Concept concept = config.getConcept(conceptUuid);
		if (concept == null) {
			return Collections.emptyList();
		}
		Criteria c = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("order", order));
		c.add(eq("concept", concept));
		c.add(eq("voided", false));
		c.addOrder(org.hibernate.criterion.Order.desc("obsId"));
		return c.list();
	}

	private void voidWithMembers(Obs obs, String reason) {
		for (Obs member : obs.getGroupMembers(false)) {
			voidWithMembers(member, reason);
		}
		obsService.voidObs(obs, reason);
	}

	private void checkSamePatient(Order order, Encounter encounter) {
		if (!encounter.getPatient().equals(order.getPatient())) {
			throw new APIException("The encounter and the order are not for the same patient");
		}
	}

	private void requireAnswer(Concept question, Concept answer, String message) {
		if (answer == null) {
			throw new APIException(message);
		}
		for (ConceptAnswer conceptAnswer : question.getAnswers(false)) {
			if (answer.equals(conceptAnswer.getAnswerConcept())) {
				return;
			}
		}
		throw new APIException(message);
	}

	private Obs coded(Concept question, Concept value, Encounter encounter, Order order) {
		Obs obs = newObs(question, encounter, order);
		obs.setValueCoded(value);
		return obs;
	}

	private Obs newObs(Concept concept, Encounter encounter, Order order) {
		Obs obs = new Obs();
		obs.setPerson(encounter.getPatient());
		obs.setEncounter(encounter);
		obs.setObsDatetime(encounter.getEncounterDatetime());
		obs.setLocation(encounter.getLocation());
		obs.setOrder(order);
		obs.setConcept(concept);
		return obs;
	}

	private Concept required(String uuid) {
		Concept concept = config.getConcept(uuid);
		if (concept == null) {
			throw new APIException("The bacteriology concept " + uuid + " is missing: load concepts/bacteriology.csv");
		}
		return concept;
	}

	private static String name(Concept concept) {
		return concept == null ? "" : concept.getDisplayString();
	}

	private interface GroupBuilder {
		Obs build();
	}

	/**
	 * An organism isolated from the sample and its susceptibility to the antibiotics tested
	 */
	public static class Isolate {

		private Concept organism;
		private final List<Susceptibility> susceptibilities = new ArrayList<Susceptibility>();

		public Concept getOrganism() {
			return organism;
		}

		public void setOrganism(Concept organism) {
			this.organism = organism;
		}

		public List<Susceptibility> getSusceptibilities() {
			return susceptibilities;
		}
	}

	/**
	 * Whether an organism is sensitive, intermediate or resistant to an antibiotic
	 */
	public static class Susceptibility {

		private Concept antibiotic;
		private Concept interpretation;

		public Susceptibility() {
		}

		public Susceptibility(Concept antibiotic, Concept interpretation) {
			this.antibiotic = antibiotic;
			this.interpretation = interpretation;
		}

		public Concept getAntibiotic() {
			return antibiotic;
		}

		public void setAntibiotic(Concept antibiotic) {
			this.antibiotic = antibiotic;
		}

		public Concept getInterpretation() {
			return interpretation;
		}

		public void setInterpretation(Concept interpretation) {
			this.interpretation = interpretation;
		}
	}
}
