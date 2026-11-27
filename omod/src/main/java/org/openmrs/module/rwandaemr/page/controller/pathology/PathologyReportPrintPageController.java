package org.openmrs.module.rwandaemr.page.controller.pathology;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.rwandaemr.RwandaEmrConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyCase;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseRepository;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Printable pathology report of a signed out case, laid out as the report the lab gave before, for the patient to take
 * to their doctor.  The print dialog also saves it as a PDF.
 */
public class PathologyReportPrintPageController {

	// Questions of the pathology report (Pathology Report for Pathology System), as in its macros
	static final String ACCESSION_NUMBER = "0e466f59-8070-45b5-97d1-575ec5cfd85d";
	static final String SURGERY_DATE = "e0436a5b-dc19-4cb9-ac1b-acc005272c08";
	static final String RECEPTION_DATE = "3928b7fc-b3ae-40a5-846f-d480120b04f6";
	static final String SENDING_PHYSICIAN = "0a4a22db-0e82-4669-9aba-086d841505b5";
	static final String SENDING_FACILITY = "7c4650ee-45d5-435a-b968-511ef127a016";
	static final String ORGAN_SYSTEM = "b88f9524-6b39-41b3-9b3a-f9a7a4b08fed";
	static final String ORGAN = "488fe4a2-7903-41da-b21e-fb236a1ee356";
	static final String SPECIMEN_DETAIL = "e99170a5-f46b-490b-8fbb-bb9eacba3500";
	static final String PROCEDURE = "66ad3230-59b2-4232-821e-a9ae86819e03";
	static final String GROSS_DESCRIPTION = "1e97a4d3-1691-4fb7-be78-18e526e82b0b";
	static final String ADDITIONAL_STAINS = "49635425-9696-4a23-a65e-3f9463025d3e";
	static final String MICROSCOPIC_EXAMINATION = "5d149510-0767-4da7-82cd-be406b34877d";
	static final String CONCLUSION = "3cdc5938-26fe-102b-80cb-0017a47871b2";
	static final String COMMENT = "e85305cf-ad93-4d1a-881e-53faf715fa8f";

	public void get(PageModel model,
					@RequestParam(value = "request") String requestRef,
					@SpringBean PathologyCaseRepository caseRepository,
					@SpringBean PathologyConfig pathologyConfig,
					@SpringBean RwandaEmrConfig rwandaEmrConfig) {

		boolean authorized = Context.hasPrivilege(PathologyConfig.PRIVILEGE_VIEW_QUEUE)
				|| Context.hasPrivilege(PathologyConfig.PRIVILEGE_PATIENT_ORDERS)
				|| Context.hasPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS)
				|| Context.hasPrivilege(PathologyConfig.PRIVILEGE_SIGN_OUT);
		model.addAttribute("authorized", authorized);
		model.addAttribute("error", null);
		if (!authorized) {
			return;
		}

		Encounter request = getEncounter(requestRef);
		PathologyCase pathologyCase = request == null ? null : caseRepository.getCase(request.getEncounterId());
		Encounter report = pathologyCase == null || pathologyCase.getReportEncounterId() == null ? null :
				Context.getEncounterService().getEncounter(pathologyCase.getReportEncounterId());
		if (report == null || report.getVoided()) {
			model.addAttribute("error", "There is no report for this pathology request yet.");
			return;
		}
		// Only the final report leaves the lab
		if (pathologyCase.getStatus() != PathologyStatus.SIGNED_OUT) {
			model.addAttribute("error", "This report is not signed out yet. Only a signed out report can be printed.");
			return;
		}

		Patient patient = report.getPatient();
		model.addAttribute("patient", patient);
		model.addAttribute("patientName", patient.getPersonName() == null ? "" : patient.getPersonName().getFullName());
		model.addAttribute("patientIdentifier", getPrimaryCareId(patient, rwandaEmrConfig));
		model.addAttribute("birthdate", format(patient.getBirthdate()));
		model.addAttribute("gender", patient.getGender());

		String accessionNumber = StringUtils.isNotBlank(pathologyCase.getAccessionNumber()) ?
				pathologyCase.getAccessionNumber() : getValue(report, ACCESSION_NUMBER);
		model.addAttribute("accessionNumber", accessionNumber);
		model.addAttribute("labPhoneNumber", pathologyConfig.getLabPhoneNumber());
		model.addAttribute("logo", pathologyConfig.getReportLogo());
		model.addAttribute("facility", report.getLocation() == null ? null : report.getLocation().getName());

		model.addAttribute("surgeryDate", getValue(report, SURGERY_DATE));
		model.addAttribute("receptionDate", getValue(report, RECEPTION_DATE));
		model.addAttribute("signoutDate", format(report.getEncounterDatetime()));
		model.addAttribute("sendingPhysician", getValue(report, SENDING_PHYSICIAN));
		model.addAttribute("sendingFacility", getValue(report, SENDING_FACILITY));
		model.addAttribute("organSystem", getValue(report, ORGAN_SYSTEM));
		model.addAttribute("organ", getValue(report, ORGAN));
		model.addAttribute("specimenDetail", getValue(report, SPECIMEN_DETAIL));
		model.addAttribute("procedure", getValue(report, PROCEDURE));
		model.addAttribute("grossDescription", getValue(report, GROSS_DESCRIPTION));
		model.addAttribute("additionalStains", getValue(report, ADDITIONAL_STAINS));
		model.addAttribute("microscopicExamination", getValue(report, MICROSCOPIC_EXAMINATION));
		model.addAttribute("conclusion", getValue(report, CONCLUSION));
		model.addAttribute("comment", getValue(report, COMMENT));

		// The pathologist who last signed the case out, and when
		PathologyCaseRepository.StatusChange signOut = null;
		List<PathologyCaseRepository.StatusChange> history = caseRepository.getHistories(
				Collections.singleton(pathologyCase.getPathologyCaseId())).get(pathologyCase.getPathologyCaseId());
		if (history != null) {
			for (PathologyCaseRepository.StatusChange change : history) {
				if (change.getStatus() == PathologyStatus.SIGNED_OUT) {
					signOut = change;
				}
			}
		}
		String pathologist = null;
		if (signOut != null && signOut.getUserId() != null) {
			User user = Context.getUserService().getUser(signOut.getUserId());
			if (user != null && user.getPersonName() != null) {
				pathologist = user.getPersonName().getFullName();
			}
		}
		if (pathologist == null) {
			for (EncounterProvider provider : report.getActiveEncounterProviders()) {
				pathologist = provider.getProvider().getName();
				break;
			}
		}
		model.addAttribute("pathologist", pathologist);
		model.addAttribute("validatedOn", format(signOut != null ? signOut.getDate() : report.getEncounterDatetime()));
		model.addAttribute("printedOn", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ENGLISH).format(new Date()));
	}

	/**
	 * @return the request encounter, given by id or uuid
	 */
	private Encounter getEncounter(String ref) {
		ref = StringUtils.trimToNull(ref);
		if (ref == null) {
			return null;
		}
		return StringUtils.isNumeric(ref) ? Context.getEncounterService().getEncounter(Integer.valueOf(ref)) :
				Context.getEncounterService().getEncounterByUuid(ref);
	}

	private String getPrimaryCareId(Patient patient, RwandaEmrConfig rwandaEmrConfig) {
		PatientIdentifier identifier = rwandaEmrConfig.getPrimaryCareIdentifierType() == null ? null :
				patient.getPatientIdentifier(rwandaEmrConfig.getPrimaryCareIdentifierType());
		if (identifier == null) {
			identifier = patient.getPatientIdentifier();
		}
		return identifier == null ? null : identifier.getIdentifier();
	}

	/**
	 * @return the answer of the report to the question, or null if it has none.  The organ is asked once for each body
	 * system, and only the system chosen has an answer.
	 */
	static String getValue(Encounter report, String conceptUuid) {
		for (Obs obs : report.getAllObs(false)) {
			if (obs.getConcept() == null || !conceptUuid.equals(obs.getConcept().getUuid())) {
				continue;
			}
			String value;
			if (obs.getValueCoded() != null) {
				value = obs.getValueCoded().getDisplayString();
			}
			else if (obs.getValueDatetime() != null) {
				value = format(obs.getValueDatetime());
			}
			else if (obs.getValueText() != null) {
				value = obs.getValueText();
			}
			else {
				value = obs.getValueAsString(Context.getLocale());
			}
			if (StringUtils.isNotBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	static String format(Date date) {
		return date == null ? null : new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(date);
	}
}
