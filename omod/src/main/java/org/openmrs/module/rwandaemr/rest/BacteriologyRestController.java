package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptName;
import org.openmrs.Order;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.labs.BacteriologyConfig;
import org.openmrs.module.rwandaemr.labs.BacteriologyService;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Isolate;
import org.openmrs.module.rwandaemr.labs.BacteriologyService.Susceptibility;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The antimicrobial susceptibility of bacteriology exams: its concepts, for the widget the pihapps lab results forms enter
 * it with (bacteriology.js), and the susceptibility of orders, for printing results
 */
@Controller
public class BacteriologyRestController {

	@Autowired
	BacteriologyService bacteriologyService;

	@Autowired
	OrderService orderService;

	@Autowired
	ConceptService conceptService;

	/**
	 * @return the concepts of the antimicrobial susceptibility, and the answers to choose from
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/bacteriology/config", method = RequestMethod.GET)
	@ResponseBody
	public Object getConfig() {
		requirePrivilege(PrivilegeConstants.GET_CONCEPTS);
		List<SimpleObject> interpretations = new ArrayList<SimpleObject>();
		for (Concept interpretation : answers(BacteriologyConfig.INTERPRETATION)) {
			interpretations.add(ref(interpretation).add("code", BacteriologyConfig.getInterpretationCode(interpretation)));
		}
		SimpleObject susceptibility = ref(concept(BacteriologyConfig.SUSCEPTIBILITY));
		susceptibility.put("culture", BacteriologyConfig.CULTURE);
		susceptibility.put("isolate", BacteriologyConfig.ISOLATE);
		susceptibility.put("organism", BacteriologyConfig.ORGANISM);
		susceptibility.put("antibioticResult", BacteriologyConfig.ANTIBIOTIC_RESULT);
		susceptibility.put("antibiotic", BacteriologyConfig.ANTIBIOTIC);
		susceptibility.put("interpretation", BacteriologyConfig.INTERPRETATION);
		susceptibility.put("organismIsolated", BacteriologyConfig.ORGANISM_ISOLATED);
		susceptibility.put("organisms", refs(answers(BacteriologyConfig.ORGANISM)));
		susceptibility.put("antibiotics", refs(answers(BacteriologyConfig.ANTIBIOTIC)));
		susceptibility.put("interpretations", interpretations);

		return new SimpleObject().add("susceptibility", susceptibility);
	}

	/**
	 * @return for each order, by uuid, the isolates
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/bacteriology", method = RequestMethod.GET)
	@ResponseBody
	public Object getResults(@RequestParam(value = "order") List<String> orderUuids) {
		requirePrivilege(PrivilegeConstants.GET_OBS);
		SimpleObject ret = new SimpleObject();
		for (String orderUuid : orderUuids) {
			Order order = orderService.getOrderByUuid(orderUuid);
			if (order == null) {
				continue;
			}
			List<SimpleObject> isolates = new ArrayList<SimpleObject>();
			for (Isolate isolate : bacteriologyService.getSusceptibility(order)) {
				List<SimpleObject> susceptibilities = new ArrayList<SimpleObject>();
				for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
					susceptibilities.add(new SimpleObject()
							.add("antibiotic", ref(susceptibility.getAntibiotic()))
							.add("interpretation", ref(susceptibility.getInterpretation()))
							.add("code", BacteriologyConfig.getInterpretationCode(susceptibility.getInterpretation())));
				}
				isolates.add(new SimpleObject().add("organism", ref(isolate.getOrganism())).add("results", susceptibilities));
			}
			ret.put(orderUuid, new SimpleObject().add("isolates", isolates));
		}
		return ret;
	}

	private List<Concept> answers(String questionUuid) {
		List<Concept> ret = new ArrayList<Concept>();
		Concept question = concept(questionUuid);
		if (question != null) {
			for (ConceptAnswer answer : question.getAnswers(false)) {
				ret.add(answer.getAnswerConcept());
			}
		}
		return ret;
	}

	/**
	 * @return the concepts, sorted by name
	 */
	private List<SimpleObject> refs(List<Concept> concepts) {
		List<SimpleObject> ret = new ArrayList<SimpleObject>();
		for (Concept concept : concepts) {
			ret.add(ref(concept));
		}
		// SimpleObject.get is generic: without the cast, String.valueOf(char[]) would be chosen
		ret.sort((a, b) -> String.valueOf((Object) a.get("display")).compareToIgnoreCase(String.valueOf((Object) b.get("display"))));
		return ret;
	}

	private SimpleObject ref(Concept concept) {
		SimpleObject o = new SimpleObject();
		o.put("uuid", concept == null ? null : concept.getUuid());
		o.put("display", concept == null ? null : name(concept));
		return o;
	}

	/**
	 * The new bacteriology concepts are named Bact_..., with the name used in the lab as short name.  Other concepts,
	 * such as the antibiotics of the dictionary, have abbreviations as short names (AMK), so their name is used, in
	 * sentence case when it is in capitals (AMIKACIN), to read as the others.
	 */
	private String name(Concept concept) {
		String name = bestName(concept);
		return name != null && name.equals(name.toUpperCase()) ? StringUtils.capitalize(name.toLowerCase()) : name;
	}

	private String bestName(Concept concept) {
		Locale locale = Context.getLocale();
		ConceptName fullySpecifiedName = concept.getFullySpecifiedName(Locale.ENGLISH);
		if (fullySpecifiedName != null && fullySpecifiedName.getName().startsWith("Bact_")) {
			ConceptName shortName = concept.getShortNameInLocale(locale);
			if (shortName == null && !locale.getLanguage().equals("en")) {
				shortName = concept.getShortNameInLocale(Locale.ENGLISH);
			}
			if (shortName != null) {
				return shortName.getName();
			}
		}
		return concept.getName(locale) != null ? concept.getName(locale).getName() : concept.getDisplayString();
	}

	private Concept concept(String uuid) {
		return uuid == null ? null : conceptService.getConceptByUuid(uuid);
	}

	private void requirePrivilege(String privilege) {
		if (!Context.hasPrivilege(privilege)) {
			throw new APIAuthenticationException("Privilege required: " + privilege);
		}
	}
}
