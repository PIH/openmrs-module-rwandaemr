package org.openmrs.module.rwandaemr.rest;

import lombok.Data;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.labs.AntibiogramConfig;
import org.openmrs.module.rwandaemr.labs.AntibiogramService;
import org.openmrs.module.rwandaemr.labs.AntibiogramService.Isolate;
import org.openmrs.module.rwandaemr.labs.AntibiogramService.Susceptibility;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Antibiograms of culture orders, for the lab results pages
 */
@Controller
public class AntibiogramRestController {

	@Autowired
	AntibiogramConfig antibiogramConfig;

	@Autowired
	AntibiogramService antibiogramService;

	@Autowired
	OrderService orderService;

	@Autowired
	EncounterService encounterService;

	@Autowired
	ConceptService conceptService;

	/**
	 * @return the culture tests that have an antibiogram, and the organisms, antibiotics and interpretations to choose from
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/antibiogram/config", method = RequestMethod.GET)
	@ResponseBody
	public Object getConfig() {
		requirePrivilege(PrivilegeConstants.GET_CONCEPTS);
		List<String> tests = new ArrayList<String>();
		for (Concept test : antibiogramConfig.getAntibiogramTests()) {
			tests.add(test.getUuid());
		}
		SimpleObject ret = new SimpleObject();
		ret.put("tests", tests);
		ret.put("organisms", answers(AntibiogramConfig.ORGANISM));
		ret.put("antibiotics", answers(AntibiogramConfig.ANTIBIOTIC));
		ret.put("interpretations", answers(AntibiogramConfig.INTERPRETATION));
		return ret;
	}

	/**
	 * @return the antibiogram of each order, by order uuid
	 */
	@RequestMapping(value = "/rest/v1/rwandaemr/lab/antibiogram", method = RequestMethod.GET)
	@ResponseBody
	public Object getAntibiograms(@RequestParam(value = "order") List<String> orderUuids) {
		requirePrivilege(PrivilegeConstants.GET_OBS);
		SimpleObject ret = new SimpleObject();
		for (String orderUuid : orderUuids) {
			Order order = orderService.getOrderByUuid(orderUuid);
			if (order == null) {
				continue;
			}
			List<SimpleObject> isolates = new ArrayList<SimpleObject>();
			for (Isolate isolate : antibiogramService.getAntibiogram(order)) {
				List<SimpleObject> results = new ArrayList<SimpleObject>();
				for (Susceptibility susceptibility : isolate.getSusceptibilities()) {
					SimpleObject result = new SimpleObject();
					result.put("antibiotic", ref(susceptibility.getAntibiotic()));
					result.put("interpretation", ref(susceptibility.getInterpretation()));
					result.put("code", AntibiogramConfig.getInterpretationCode(susceptibility.getInterpretation()));
					results.add(result);
				}
				SimpleObject o = new SimpleObject();
				o.put("organism", ref(isolate.getOrganism()));
				o.put("results", results);
				isolates.add(o);
			}
			ret.put(orderUuid, isolates);
		}
		return ret;
	}

	@RequestMapping(value = "/rest/v1/rwandaemr/lab/antibiogram", method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> saveAntibiogram(@RequestBody AntibiogramRequest request) {
		try {
			requirePrivilege(PrivilegeConstants.ADD_OBS);
			requirePrivilege(PrivilegeConstants.EDIT_OBS);
			Order order = orderService.getOrderByUuid(request.getOrder());
			Encounter encounter = encounterService.getEncounterByUuid(request.getEncounter());
			if (order == null || encounter == null) {
				return ResponseEntity.badRequest().body(error("Order or encounter not found"));
			}
			List<Isolate> isolates = new ArrayList<Isolate>();
			if (request.getIsolates() != null) {
				for (IsolateRequest isolateRequest : request.getIsolates()) {
					Isolate isolate = new Isolate();
					isolate.setOrganism(concept(isolateRequest.getOrganism()));
					if (isolateRequest.getResults() != null) {
						for (ResultRequest result : isolateRequest.getResults()) {
							isolate.getSusceptibilities().add(new Susceptibility(concept(result.getAntibiotic()),
									concept(result.getInterpretation())));
						}
					}
					isolates.add(isolate);
				}
			}
			antibiogramService.saveAntibiogram(order, encounter, isolates);
			return ResponseEntity.ok(new SimpleObject().add("success", true));
		}
		catch (APIAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(e.getMessage()));
		}
		catch (APIException e) {
			return ResponseEntity.badRequest().body(error(e.getMessage()));
		}
	}

	private List<SimpleObject> answers(String questionUuid) {
		List<SimpleObject> ret = new ArrayList<SimpleObject>();
		Concept question = antibiogramConfig.getConcept(questionUuid);
		if (question != null) {
			for (ConceptAnswer answer : question.getAnswers(false)) {
				SimpleObject o = ref(answer.getAnswerConcept());
				o.put("code", AntibiogramConfig.getInterpretationCode(answer.getAnswerConcept()));
				ret.add(o);
			}
		}
		ret.sort((a, b) -> String.valueOf(a.get("display")).compareToIgnoreCase(String.valueOf(b.get("display"))));
		return ret;
	}

	private SimpleObject ref(Concept concept) {
		SimpleObject o = new SimpleObject();
		o.put("uuid", concept == null ? null : concept.getUuid());
		o.put("display", concept == null ? null : name(concept));
		return o;
	}

	private String name(Concept concept) {
		Locale locale = Context.getLocale();
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

	private SimpleObject error(String message) {
		return new SimpleObject().add("success", false).add("message", message);
	}

	@Data
	public static class AntibiogramRequest {
		private String order;
		private String encounter;
		private List<IsolateRequest> isolates;
	}

	@Data
	public static class IsolateRequest {
		private String organism;
		private List<ResultRequest> results;
	}

	@Data
	public static class ResultRequest {
		private String antibiotic;
		private String interpretation;
	}
}
