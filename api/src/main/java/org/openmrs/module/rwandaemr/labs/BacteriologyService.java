package org.openmrs.module.rwandaemr.labs;

import org.hibernate.Criteria;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hibernate.criterion.Restrictions.eq;

/**
 * Reads the antimicrobial susceptibility of a bacteriology exam: the organisms isolated and, for each, the antibiotics
 * tested and whether the organism is sensitive, intermediate or resistant to them.  It is recorded as one obs group
 * linked to the order, in the encounter that fulfills the order, by the pihapps lab results forms (see
 * rwandaBacteriologyEditor in bacteriology.js), so that it is saved and validated with the order's other results.
 * See {@link BacteriologyConfig}.
 */
@Component
public class BacteriologyService {

	private final DbSessionFactory dbSessionFactory;
	private final BacteriologyConfig config;

	public BacteriologyService(@Autowired DbSessionFactory dbSessionFactory, @Autowired BacteriologyConfig config) {
		this.dbSessionFactory = dbSessionFactory;
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
