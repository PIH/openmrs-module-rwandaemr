package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.icd11.Icd11ApiException;
import org.openmrs.module.rwandaemr.icd11.Icd11Service;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RwandaEMR REST endpoints used by ICD-11 diagnosis capture clients.
 */
@Controller
@RequestMapping("/rest/v1/rwandaemr/icd11")
public class Icd11RestController {

	protected final Log log = LogFactory.getLog(getClass());

	@Autowired
	private Icd11Service icd11Service;

	@Autowired
	private PatientService patientService;

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private ConceptService conceptService;

	@RequestMapping(value = "/search", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> search(@RequestParam("q") String query) {
		if (!Context.hasPrivilege(PrivilegeConstants.GET_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.GET_DIAGNOSES);
		}
		try {
			return ResponseEntity.ok(icd11Service.search(query));
		}
		catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		}
		catch (IllegalStateException | Icd11ApiException e) {
			log.warn("Unable to search ICD-11 diagnoses", e);
			return serviceUnavailable(e.getMessage());
		}
	}

	@RequestMapping(value = "/concepts", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> searchConcepts(@RequestParam("q") String query) {
		if (!Context.hasPrivilege(PrivilegeConstants.GET_DIAGNOSES)
				&& !Context.hasPrivilege(PrivilegeConstants.GET_CONCEPTS)) {
			return forbidden(PrivilegeConstants.GET_DIAGNOSES);
		}
		String trimmedQuery = StringUtils.trimToNull(query);
		if (trimmedQuery == null || trimmedQuery.length() < 2) {
			return badRequest("At least two characters are required");
		}
		ConceptClass icd11Class = getIcd11ConceptClass();
		if (icd11Class == null) {
			return serviceUnavailable("Concept class ICD11 was not found");
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Concept concept : conceptService.getConceptsByClass(icd11Class)) {
			if (concept != null && !Boolean.TRUE.equals(concept.getRetired()) && matchesDiagnosisQuery(concept, trimmedQuery)) {
				Map<String, Object> result = toDiagnosisConceptJson(concept);
				if (StringUtils.isNotBlank((String) result.get("code"))) {
					results.add(result);
				}
			}
			if (results.size() >= 50) {
				break;
			}
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("results", results);
		return ResponseEntity.ok(response);
	}

	@RequestMapping(value = "/code/{code}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> getCode(@PathVariable("code") String code) {
		if (!Context.hasPrivilege(PrivilegeConstants.GET_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.GET_DIAGNOSES);
		}
		try {
			Object result = icd11Service.getCode(code);
			return result == null ? notFound("ICD-11 code was not found") : ResponseEntity.ok(result);
		}
		catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		}
		catch (IllegalStateException | Icd11ApiException e) {
			log.warn("Unable to retrieve ICD-11 code", e);
			return serviceUnavailable(e.getMessage());
		}
	}

	@RequestMapping(value = "/browse", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> browse(@RequestParam(value = "parent", required = false) String parentCode) {
		if (!Context.hasPrivilege(PrivilegeConstants.GET_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.GET_DIAGNOSES);
		}
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("parent", StringUtils.trimToNull(parentCode));
		response.put("results", icd11Service.browse(parentCode));
		return ResponseEntity.ok(response);
	}

	@RequestMapping(value = "/diagnosis", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> saveDiagnosis(@RequestBody Icd11DiagnosisRequest request) {
		if (!Context.hasPrivilege(PrivilegeConstants.EDIT_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.EDIT_DIAGNOSES);
		}
		try {
			Patient patient = requirePatient(request.getPatient());
			Encounter encounter = requireEncounter(request.getEncounter());
			if (!patient.equals(encounter.getPatient())) {
				return badRequest("Encounter does not belong to patient");
			}
			Icd11PatientDiagnosis diagnosis = new Icd11PatientDiagnosis();
			diagnosis.setPatient(patient);
			diagnosis.setEncounter(encounter);
			diagnosis.setIcd11Code(StringUtils.trimToNull(request.getIcd11Code()));
			diagnosis.setEntityUri(StringUtils.trimToNull(request.getEntityUri()));
			diagnosis.setFoundationUri(StringUtils.trimToNull(request.getFoundationUri()));
			diagnosis.setTitle(StringUtils.trimToNull(request.getTitle()));
			diagnosis.setLinearization(StringUtils.trimToNull(request.getLinearization()));
			diagnosis.setDiagnosisType(StringUtils.trimToNull(request.getDiagnosisType()));
			diagnosis.setCertainty(StringUtils.trimToNull(request.getCertainty()));
			return ResponseEntity.status(HttpStatus.CREATED).body(toJson(icd11Service.saveDiagnosis(diagnosis)));
		}
		catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		}
	}

	@RequestMapping(value = "/diagnosis", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> getDiagnoses(@RequestParam("patient") String patientUuid,
										 @RequestParam(value = "encounter", required = false) String encounterUuid) {
		if (!Context.hasPrivilege(PrivilegeConstants.GET_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.GET_DIAGNOSES);
		}
		try {
			Patient patient = requirePatient(patientUuid);
			Encounter encounter = StringUtils.isBlank(encounterUuid) ? null : requireEncounter(encounterUuid);
			if (encounter != null && !patient.equals(encounter.getPatient())) {
				return badRequest("Encounter does not belong to patient");
			}
			List<Map<String, Object>> results = new ArrayList<>();
			for (Icd11PatientDiagnosis diagnosis : icd11Service.getDiagnoses(patient, encounter)) {
				results.add(toJson(diagnosis));
			}
			Map<String, Object> response = new LinkedHashMap<>();
			response.put("results", results);
			return ResponseEntity.ok(response);
		}
		catch (IllegalArgumentException e) {
			return badRequest(e.getMessage());
		}
	}

	@RequestMapping(value = "/diagnosis/{uuid}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public ResponseEntity<?> deleteDiagnosis(@PathVariable("uuid") String uuid) {
		if (!Context.hasPrivilege(PrivilegeConstants.DELETE_DIAGNOSES)) {
			return forbidden(PrivilegeConstants.DELETE_DIAGNOSES);
		}
		Icd11PatientDiagnosis diagnosis = icd11Service.getDiagnosisByUuid(uuid);
		if (diagnosis == null || Boolean.TRUE.equals(diagnosis.getVoided())) {
			return notFound("ICD-11 diagnosis was not found");
		}
		return ResponseEntity.ok(toJson(icd11Service.voidDiagnosis(diagnosis,
				"Deleted through RwandaEMR ICD-11 REST API")));
	}

	private Patient requirePatient(String uuid) {
		if (StringUtils.isBlank(uuid)) {
			throw new IllegalArgumentException("Patient UUID is required");
		}
		Patient patient = patientService.getPatientByUuid(uuid.trim());
		if (patient == null) {
			throw new IllegalArgumentException("Patient was not found");
		}
		return patient;
	}

	private Encounter requireEncounter(String uuid) {
		if (StringUtils.isBlank(uuid)) {
			throw new IllegalArgumentException("Encounter UUID is required");
		}
		Encounter encounter = encounterService.getEncounterByUuid(uuid.trim());
		if (encounter == null) {
			throw new IllegalArgumentException("Encounter was not found");
		}
		return encounter;
	}

	private Map<String, Object> toJson(Icd11PatientDiagnosis diagnosis) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("uuid", diagnosis.getUuid());
		result.put("patient", diagnosis.getPatient().getUuid());
		result.put("encounter", diagnosis.getEncounter().getUuid());
		result.put("icd11Code", diagnosis.getIcd11Code());
		result.put("entityUri", diagnosis.getEntityUri());
		result.put("foundationUri", diagnosis.getFoundationUri());
		result.put("title", diagnosis.getTitle());
		result.put("linearization", diagnosis.getLinearization());
		result.put("diagnosisType", diagnosis.getDiagnosisType());
		result.put("certainty", diagnosis.getCertainty());
		result.put("voided", diagnosis.getVoided());
		return result;
	}

	private ConceptClass getIcd11ConceptClass() {
		ConceptClass conceptClass = conceptService.getConceptClassByName("ICD11");
		if (conceptClass == null) {
			conceptClass = conceptService.getConceptClassByName("ICD-11");
		}
		if (conceptClass == null) {
			conceptClass = conceptService.getConceptClassByName("ICD 11");
		}
		if (conceptClass == null) {
			for (ConceptClass candidate : conceptService.getAllConceptClasses(false)) {
				if (candidate != null && isIcd11ConceptClassName(candidate.getName())) {
					return candidate;
				}
			}
		}
		return conceptClass;
	}

	private boolean isIcd11ConceptClassName(String name) {
		if (StringUtils.isBlank(name)) {
			return false;
		}
		String normalized = name.replaceAll("[^A-Za-z0-9]", "");
		return "ICD11".equalsIgnoreCase(normalized);
	}

	private boolean matchesDiagnosisQuery(Concept concept, String query) {
		String normalizedQuery = query.toLowerCase();
		String code = getIcd11Code(concept);
		if (StringUtils.isNotBlank(code) && code.toLowerCase().contains(normalizedQuery)) {
			return true;
		}
		String display = getConceptDisplay(concept, code);
		if (StringUtils.isNotBlank(display) && display.toLowerCase().contains(normalizedQuery)) {
			return true;
		}
		Collection<ConceptName> names = concept.getNames(false);
		if (names != null) {
			for (ConceptName name : names) {
				if (name != null && StringUtils.isNotBlank(name.getName())
						&& name.getName().toLowerCase().contains(normalizedQuery)) {
					return true;
				}
			}
		}
		return false;
	}

	private Map<String, Object> toDiagnosisConceptJson(Concept concept) {
		String code = getIcd11Code(concept);
		String display = getConceptDisplay(concept, code);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("uuid", concept.getUuid());
		result.put("code", code);
		result.put("display", display);
		return result;
	}

	private String getIcd11Code(Concept concept) {
		if (concept == null) {
			return null;
		}
		for (ConceptMap mapping : concept.getConceptMappings()) {
			if (mapping.getConceptReferenceTerm() != null
					&& mapping.getConceptReferenceTerm().getConceptSource() != null
					&& StringUtils.isNotBlank(mapping.getConceptReferenceTerm().getCode())) {
				String source = mapping.getConceptReferenceTerm().getConceptSource().getName();
				if (StringUtils.containsIgnoreCase(source, "ICD")
						|| StringUtils.containsIgnoreCase(source, "RHIP")) {
					return mapping.getConceptReferenceTerm().getCode();
				}
			}
		}
		String display = getConceptDisplay(concept, null);
		int separator = display.indexOf(" - ");
		if (separator < 0) {
			separator = display.indexOf(":");
		}
		String candidate = separator > 0 ? StringUtils.trimToNull(display.substring(0, separator)) : null;
		return isDiagnosisCode(candidate) ? candidate : null;
	}

	private String getConceptDisplay(Concept concept, String code) {
		ConceptName name = concept.getName(Context.getLocale(), false);
		if (name == null) {
			name = concept.getName(Locale.ENGLISH, false);
		}
		String display = name == null ? concept.getDisplayString() : name.getName();
		if (StringUtils.isBlank(display)) {
			display = concept.getUuid();
		}
		if (StringUtils.isNotBlank(code) && !StringUtils.startsWithIgnoreCase(display, code)) {
			return code + " - " + display;
		}
		return display;
	}

	private boolean isDiagnosisCode(String candidate) {
		if (StringUtils.isBlank(candidate) || candidate.length() < 2 || candidate.length() > 13) {
			return false;
		}
		for (int i = 0; i < candidate.length(); i++) {
			char c = candidate.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '.') {
				return false;
			}
		}
		return Character.isLetterOrDigit(candidate.charAt(0));
	}

	private ResponseEntity<Map<String, String>> badRequest(String message) {
		return error(HttpStatus.BAD_REQUEST, message);
	}

	private ResponseEntity<Map<String, String>> notFound(String message) {
		return error(HttpStatus.NOT_FOUND, message);
	}

	private ResponseEntity<Map<String, String>> serviceUnavailable(String message) {
		return error(HttpStatus.SERVICE_UNAVAILABLE, message);
	}

	private ResponseEntity<Map<String, String>> forbidden(String privilege) {
		log.warn("User does not have sufficient privilege: " + privilege);
		return error(HttpStatus.FORBIDDEN, "Missing required privilege: " + privilege);
	}

	private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		Map<String, String> response = new LinkedHashMap<>();
		response.put("error", status.getReasonPhrase());
		response.put("message", message);
		return ResponseEntity.status(status).body(response);
	}
}
