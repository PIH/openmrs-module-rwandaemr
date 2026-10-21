package org.openmrs.module.rwandaemr.labs;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.api.ConceptService;
import org.openmrs.util.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The concepts of an antibiogram, defined in the distribution's concepts/microbiology.csv, and the culture tests whose
 * results include one.  An antibiogram is recorded as an obs group linked to the culture order:
 * <pre>
 * Antibiogram
 *   Isolated organism susceptibility (one per organism)
 *     Isolated organism
 *     Antibiotic susceptibility result (one per antibiotic tested)
 *       Antibiotic tested
 *       Antibiotic susceptibility interpretation (sensitive, intermediate or resistant)
 * </pre>
 * The antibiogram is not a set member of the culture tests, so that the lab results lists that show each result as a
 * row do not show its parts as unrelated rows.
 */
@Component
public class AntibiogramConfig {

	public static final String ANTIBIOGRAM_TESTS_PROPERTY = "rwandaemr.labs.antibiogramTests";

	public static final String ANTIBIOGRAM = "9111a3f3-bdd3-4c81-a441-cb6b613e1f17";
	public static final String ISOLATE = "4a88faee-ea91-4a84-b820-97ba17a49647";
	public static final String ORGANISM = "da723b67-2ed4-4f98-92a0-37f7c375df73";
	public static final String SUSCEPTIBILITY = "ae0b3260-252b-4428-8887-4393ad93a38f";
	public static final String ANTIBIOTIC = "88c38722-75c2-4e54-b91c-5dd96a27ed5e";
	public static final String INTERPRETATION = "78b5fe2b-852b-4c3e-9560-2835dc7b56b5";

	public static final String SENSITIVE = "3cda50de-26fe-102b-80cb-0017a47871b2";
	public static final String INTERMEDIATE = "dc4cdd36-d352-4337-b881-03c2739469c1";
	public static final String RESISTANT = "3cda5552-26fe-102b-80cb-0017a47871b2";

	private final ConceptService conceptService;

	public AntibiogramConfig(@Autowired ConceptService conceptService) {
		this.conceptService = conceptService;
	}

	/**
	 * @return the culture tests whose results include an antibiogram
	 */
	public List<Concept> getAntibiogramTests() {
		List<Concept> ret = new ArrayList<Concept>();
		String value = ConfigUtil.getGlobalProperty(ANTIBIOGRAM_TESTS_PROPERTY);
		if (StringUtils.isNotBlank(value)) {
			for (String reference : value.split(",")) {
				Concept concept = StringUtils.isBlank(reference) ? null : conceptService.getConceptByReference(reference.trim());
				if (concept != null) {
					ret.add(concept);
				}
			}
		}
		return ret;
	}

	public Concept getConcept(String uuid) {
		return conceptService.getConceptByUuid(uuid);
	}

	/**
	 * @return the concepts an antibiogram is recorded with, which are among the results of a culture order
	 */
	public Set<Concept> getAntibiogramConcepts() {
		Set<Concept> ret = new HashSet<Concept>();
		for (String uuid : Arrays.asList(ANTIBIOGRAM, ISOLATE, ORGANISM, SUSCEPTIBILITY, ANTIBIOTIC, INTERPRETATION)) {
			Concept concept = getConcept(uuid);
			if (concept != null) {
				ret.add(concept);
			}
		}
		return ret;
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
