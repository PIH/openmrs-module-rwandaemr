package org.openmrs.module.rwandaemr.labs;

import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The concepts of the bacteriology exams, defined in the distribution's concepts/bacteriology.csv.  Each exam (Urines,
 * CSF, Blood culture...) is a lab set in the BACTERIOLOGY category, whose members are the tests that apply to the
 * sample: wet mount, Gram stain, Indian ink, culture and the antimicrobial susceptibility of the organisms isolated.
 * The tests are entered as any other result, except the antimicrobial susceptibility, recorded as:
 * <pre>
 * Antimicrobial susceptibility
 *   Isolated organism susceptibility (one per organism)
 *     Isolated organism
 *     Antibiotic susceptibility result (one per antibiotic tested)
 *       Antibiotic tested
 *       Susceptibility interpretation (sensitive, intermediate or resistant)
 * </pre>
 * It is a member of the exams, so it counts as a result of the order, but it is entered with its own widget.
 */
@Component
public class BacteriologyConfig {

	public static final String CULTURE = "e4f7a701-60b5-52f8-aabf-fd10d9e1c045";
	public static final String ORGANISM_ISOLATED = "a2e60961-48c1-5b51-b645-c86dcc8e9f60";

	public static final String SUSCEPTIBILITY = "6f65e869-9395-5c8b-8a65-05ad3e19bcb4";
	public static final String ISOLATE = "b606710c-ee65-514b-b395-77629a1c1204";
	public static final String ORGANISM = "63cd7913-9e0c-58ed-a4c9-31d3134d5d62";
	public static final String ANTIBIOTIC_RESULT = "7876a350-21bb-59c8-874d-2deec23b850d";
	public static final String ANTIBIOTIC = "7d2d0d97-4c19-53f2-928b-5a6e7455b27c";
	public static final String INTERPRETATION = "e11de02b-3000-52ed-8ded-cf9553fe792f";

	public static final String SENSITIVE = "3cda50de-26fe-102b-80cb-0017a47871b2";
	public static final String INTERMEDIATE = "d7655e8a-117a-55fc-b993-6332652070d8";
	public static final String RESISTANT = "3cda5552-26fe-102b-80cb-0017a47871b2";

	private final ConceptService conceptService;

	public BacteriologyConfig(@Autowired ConceptService conceptService) {
		this.conceptService = conceptService;
	}

	public Concept getConcept(String uuid) {
		return conceptService.getConceptByUuid(uuid);
	}

	/**
	 * @return S, I or R for the interpretation answer, or null
	 */
	public static String getInterpretationCode(Concept interpretation) {
		if (interpretation == null) {
			return null;
		}
		if (SENSITIVE.equals(interpretation.getUuid())) {
			return "S";
		}
		if (INTERMEDIATE.equals(interpretation.getUuid())) {
			return "I";
		}
		return RESISTANT.equals(interpretation.getUuid()) ? "R" : null;
	}
}
