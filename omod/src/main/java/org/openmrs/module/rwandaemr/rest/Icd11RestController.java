package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Patient;
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
import java.util.LinkedHashMap;
import java.util.List;
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
