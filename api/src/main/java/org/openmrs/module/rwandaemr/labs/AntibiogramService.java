package org.openmrs.module.rwandaemr.labs;

import org.hibernate.Criteria;
import org.openmrs.Concept;
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
import java.util.List;

import static org.hibernate.criterion.Restrictions.eq;

/**
 * Reads and records the antibiogram of a culture order: the organisms isolated and, for each, the antibiotics tested and
 * whether the organism is sensitive, intermediate or resistant to them.  See {@link AntibiogramConfig}.
 */
@Component
public class AntibiogramService {

	private final DbSessionFactory dbSessionFactory;
	private final ObsService obsService;
	private final EncounterService encounterService;
	private final AntibiogramConfig config;

	public AntibiogramService(@Autowired DbSessionFactory dbSessionFactory,
							  @Autowired ObsService obsService,
							  @Autowired EncounterService encounterService,
							  @Autowired AntibiogramConfig config) {
		this.dbSessionFactory = dbSessionFactory;
		this.obsService = obsService;
		this.encounterService = encounterService;
		this.config = config;
	}

	/**
	 * @return the organisms isolated for the order, with their susceptibility, or an empty list
	 */
	@Transactional(readOnly = true)
	public List<Isolate> getAntibiogram(Order order) {
		List<Obs> antibiograms = getAntibiogramObs(order);
		if (antibiograms.isEmpty()) {
			return Collections.emptyList();
		}
		return toIsolates(antibiograms.get(0));
	}

	/**
	 * Replaces the antibiogram of the order with the given isolates, recorded in the encounter that fulfills the order.
	 * An empty list removes the antibiogram.
	 */
	@Transactional
	public void saveAntibiogram(Order order, Encounter encounter, List<Isolate> isolates) {
		if (!encounter.getPatient().equals(order.getPatient())) {
			throw new APIException("The encounter and the order are not for the same patient");
		}
		Concept antibiogramConcept = required(AntibiogramConfig.ANTIBIOGRAM);
		for (Isolate isolate : isolates) {
			if (isolate.getOrganism() == null) {
				throw new APIException("Each antibiogram needs the organism isolated");
			}
			for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
				if (susceptibility.getAntibiotic() == null || susceptibility.getInterpretation() == null) {
					throw new APIException("Each antibiotic tested needs its result: sensitive, intermediate or resistant");
				}
			}
		}
		for (Obs existing : getAntibiogramObs(order)) {
			voidWithMembers(existing, "Antibiogram replaced");
		}
		if (isolates.isEmpty()) {
			return;
		}
		Obs antibiogram = newObs(antibiogramConcept, encounter, order);
		for (Isolate isolate : isolates) {
			Obs isolateObs = newObs(required(AntibiogramConfig.ISOLATE), encounter, order);
			Obs organism = newObs(required(AntibiogramConfig.ORGANISM), encounter, order);
			organism.setValueCoded(isolate.getOrganism());
			isolateObs.addGroupMember(organism);
			for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
				Obs susceptibilityObs = newObs(required(AntibiogramConfig.SUSCEPTIBILITY), encounter, order);
				Obs antibiotic = newObs(required(AntibiogramConfig.ANTIBIOTIC), encounter, order);
				antibiotic.setValueCoded(susceptibility.getAntibiotic());
				Obs interpretation = newObs(required(AntibiogramConfig.INTERPRETATION), encounter, order);
				interpretation.setValueCoded(susceptibility.getInterpretation());
				susceptibilityObs.addGroupMember(antibiotic);
				susceptibilityObs.addGroupMember(interpretation);
				isolateObs.addGroupMember(susceptibilityObs);
			}
			antibiogram.addGroupMember(isolateObs);
		}
		encounter.addObs(antibiogram);
		encounterService.saveEncounter(encounter);
	}

	@SuppressWarnings("unchecked")
	private List<Obs> getAntibiogramObs(Order order) {
		Concept antibiogramConcept = config.getConcept(AntibiogramConfig.ANTIBIOGRAM);
		if (antibiogramConcept == null) {
			return Collections.emptyList();
		}
		Criteria c = dbSessionFactory.getHibernateSessionFactory().getCurrentSession().createCriteria(Obs.class);
		c.add(eq("order", order));
		c.add(eq("concept", antibiogramConcept));
		c.add(eq("voided", false));
		c.addOrder(org.hibernate.criterion.Order.desc("obsId"));
		return c.list();
	}

	List<Isolate> toIsolates(Obs antibiogram) {
		List<Isolate> ret = new ArrayList<Isolate>();
		for (Obs isolateObs : antibiogram.getGroupMembers(false)) {
			if (!AntibiogramConfig.ISOLATE.equals(isolateObs.getConcept().getUuid())) {
				continue;
			}
			Isolate isolate = new Isolate();
			for (Obs member : isolateObs.getGroupMembers(false)) {
				String conceptUuid = member.getConcept().getUuid();
				if (AntibiogramConfig.ORGANISM.equals(conceptUuid)) {
					isolate.setOrganism(member.getValueCoded());
				}
				else if (AntibiogramConfig.SUSCEPTIBILITY.equals(conceptUuid)) {
					Susceptibility susceptibility = new Susceptibility();
					for (Obs value : member.getGroupMembers(false)) {
						if (AntibiogramConfig.ANTIBIOTIC.equals(value.getConcept().getUuid())) {
							susceptibility.setAntibiotic(value.getValueCoded());
						}
						else if (AntibiogramConfig.INTERPRETATION.equals(value.getConcept().getUuid())) {
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

	private void voidWithMembers(Obs obs, String reason) {
		for (Obs member : obs.getGroupMembers(false)) {
			voidWithMembers(member, reason);
		}
		obsService.voidObs(obs, reason);
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
			throw new APIException("The antibiogram concept " + uuid + " is missing: load concepts/microbiology.csv");
		}
		return concept;
	}

	/**
	 * An organism isolated from the specimen and its susceptibility to the antibiotics tested
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
