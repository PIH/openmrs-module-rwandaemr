package org.openmrs.module.rwandaemr.htmlformentry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.DropdownWidget;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.Option;
import org.openmrs.module.rwandaemr.pathology.PathologyCase;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseRepository;
import org.openmrs.module.rwandaemr.pathology.PathologyCaseService;
import org.openmrs.module.rwandaemr.pathology.PathologyConfig;
import org.openmrs.module.rwandaemr.pathology.PathologyStatus;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueRow;
import org.openmrs.module.rwandaemr.pathology.queue.PathologyQueueService;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Links a pathology report to the case of the pathology lab it reports on.  Saving the report moves the case to the
 * steps the report has results for, given by the questions of each step:
 * <pre>
 * &lt;pathologyCase grossedConcepts="..." processedConcepts="..." additionalStainsConcepts="..."
 *                sentForReviewConcepts="..." signedOutConcepts="..."/&gt;
 * </pre>
 * Each attribute lists concepts by uuid, mapping or id, separated by commas.  A report opened from the pathology queue
 * chooses its case from the pathologyRequestId parameter of the page.  A report of a specimen examined elsewhere is not a
 * case of the lab, and is saved without one.
 * <p>
 * Choosing the case fills the questions of the report the request already answers, given by the id of each question's
 * obs tag: accessionNumberField, surgeryDateField, receivedDateField, physicianField, facilityField and
 * clinicalInformationField.  Only empty questions are filled, so nothing typed or saved on the report is replaced.
 * <p>
 * A signed out report is final: only a pathologist can change it.
 */
public class PathologyCaseElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	public static final String REQUEST_PARAMETER = "pathologyRequestId";

	/**
	 * The questions of the report filled from the request, by the attribute giving the id of each
	 */
	private static final Map<String, String> FILL_ATTRIBUTES = new LinkedHashMap<String, String>();
	static {
		FILL_ATTRIBUTES.put("accessionNumberField", "accessionNumber");
		FILL_ATTRIBUTES.put("surgeryDateField", "surgeryDate");
		FILL_ATTRIBUTES.put("receivedDateField", "receivedDate");
		FILL_ATTRIBUTES.put("physicianField", "physician");
		FILL_ATTRIBUTES.put("facilityField", "facility");
		FILL_ATTRIBUTES.put("clinicalInformationField", "clinicalInformation");
	}

	private static final Map<String, PathologyStatus> STEP_ATTRIBUTES = new LinkedHashMap<String, PathologyStatus>();
	static {
		STEP_ATTRIBUTES.put("grossedConcepts", PathologyStatus.GROSSED);
		STEP_ATTRIBUTES.put("processedConcepts", PathologyStatus.PROCESSED);
		STEP_ATTRIBUTES.put("additionalStainsConcepts", PathologyStatus.ADDITIONAL_STAINS);
		STEP_ATTRIBUTES.put("sentForReviewConcepts", PathologyStatus.SENT_FOR_REVIEW);
		STEP_ATTRIBUTES.put("signedOutConcepts", PathologyStatus.SIGNED_OUT);
	}

	private final DropdownWidget caseWidget = new DropdownWidget();

	private final ErrorWidget errorWidget = new ErrorWidget();

	private final Map<PathologyStatus, Set<Concept>> resultsOfSteps = new EnumMap<PathologyStatus, Set<Concept>>(PathologyStatus.class);

	/**
	 * The case the report was saved for, if it is being viewed or edited
	 */
	private final PathologyCase linkedCase;

	private final Map<String, String> caseLabels = new LinkedHashMap<String, String>();

	/**
	 * The id of each question of the report filled from the request, by what fills it
	 */
	private final Map<String, String> fieldsToFill = new LinkedHashMap<String, String>();

	/**
	 * What the request of each case to choose fills on the report, by the case's request encounter
	 */
	private final Map<String, Map<String, String>> requestDetails = new LinkedHashMap<String, Map<String, String>>();

	/**
	 * True if the report is signed out and the user cannot change it
	 */
	private final boolean locked;

	public PathologyCaseElement(FormEntryContext context, Map<String, String> parameters) {
		for (Map.Entry<String, PathologyStatus> attribute : STEP_ATTRIBUTES.entrySet()) {
			resultsOfSteps.put(attribute.getValue(), getConcepts(parameters, attribute.getKey()));
		}
		for (Map.Entry<String, String> attribute : FILL_ATTRIBUTES.entrySet()) {
			String fieldId = StringUtils.trimToNull(parameters.get(attribute.getKey()));
			if (fieldId != null) {
				fieldsToFill.put(attribute.getValue(), fieldId);
			}
		}
		Encounter existing = context.getExistingEncounter();
		linkedCase = existing == null || existing.getEncounterId() == null ? null :
				getCaseRepository().getCaseByReport(existing.getEncounterId());
		locked = linkedCase != null && linkedCase.getStatus() == PathologyStatus.SIGNED_OUT
				&& !Context.hasPrivilege(PathologyConfig.PRIVILEGE_SIGN_OUT);
		for (PathologyCase pathologyCase : getCasesToChoose(context.getExistingPatient())) {
			caseLabels.put(pathologyCase.getEncounterId().toString(), getLabel(pathologyCase));
		}
		if (!fieldsToFill.isEmpty() && !caseLabels.isEmpty() && FormEntryContext.Mode.VIEW != context.getMode()) {
			for (PathologyQueueRow request : getQueueService().getRequests(context.getExistingPatient())) {
				String key = request.getEncounterId().toString();
				if (caseLabels.containsKey(key)) {
					requestDetails.put(key, getRequestDetails(request));
				}
			}
		}

		context.registerWidget(caseWidget);
		context.registerErrorWidget(caseWidget, errorWidget);
		caseWidget.addOption(new Option("None: not a case of the pathology lab", "", false));
		for (Map.Entry<String, String> caseLabel : caseLabels.entrySet()) {
			caseWidget.addOption(new Option(caseLabel.getValue(), caseLabel.getKey(), false));
		}
		if (linkedCase != null) {
			caseWidget.setInitialValue(linkedCase.getEncounterId().toString());
		}
	}

	@Override
	public String generateHtml(FormEntryContext context) {
		if (FormEntryContext.Mode.VIEW == context.getMode()) {
			String label = linkedCase == null ? "None: not a case of the pathology lab" : getLabel(linkedCase);
			return "<span class=\"value\">" + StringEscapeUtils.escapeHtml4(label) + "</span>";
		}
		String fieldName = context.getFieldName(caseWidget);
		// Chooses the case the report was opened for from the queue, or the only case the patient has in the lab, then
		// fills the empty questions the request answers.  A question keeps what was typed or saved, but takes the details
		// of another case chosen in place of the one that filled it.
		String script = "<script type=\"text/javascript\">jQuery(function() {" +
				"var select = jQuery('select[name=\"" + fieldName + "\"]');" +
				"if (!select.val()) {" +
				"var match = /[?&]" + REQUEST_PARAMETER + "=(\\d+)/.exec(window.location.search);" +
				"if (match && select.find('option[value=\"' + match[1] + '\"]').length) { select.val(match[1]); }" +
				"else if (!match && select.find('option').length === 2) { select.val(select.find('option').eq(1).val()); }" +
				"}" +
				"var details = " + toJson(requestDetails) + ";" +
				"var fields = " + toJson(fieldsToFill) + ";" +
				"var filled = {};" +
				"var fill = function() {" +
				"var request = details[select.val()] || {};" +
				"jQuery.each(fields, function(detail, fieldId) {" +
				"var property = fieldId + '.value';" +
				"if (typeof propertyAccessorInfo === 'undefined' || !propertyAccessorInfo[property]) { return; }" +
				"var current = getValue(property) || '';" +
				"if (current !== '' && current !== filled[property]) { return; }" +
				"var value = request[detail] || '';" +
				"if (value !== current) { setValue(property, value); }" +
				"filled[property] = value;" +
				"});" +
				"};" +
				"select.change(fill);" +
				"fill();" +
				"});</script>";
		String help = "<div class=\"pathology-case-help\" style=\"font-size: 0.9em; color: #607d8b;\">Saving the report moves " +
				"the case: gross description &rarr; Grossed, blocks and slides &rarr; Processed, additional stains &rarr; " +
				"Additional stains, Send for pathologist review &rarr; Sent for review, Pathologist reviewed &rarr; Signed out " +
				"(pathologists only).</div>";
		String lockedWarning = locked ? "<div class=\"pathology-case-locked\" style=\"color: #9f1d1d; font-weight: bold;\">" +
				"This report is signed out. Only a pathologist can change it.</div>" : "";
		return lockedWarning + caseWidget.generateHtml(context) + errorWidget.generateHtml(context) + help + script;
	}

	@Override
	public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest request) {
		List<FormSubmissionError> errors = new ArrayList<FormSubmissionError>();
		if (locked) {
			errors.add(new FormSubmissionError(caseWidget, "This report is signed out. Only a pathologist can change it."));
			return errors;
		}
		String value = getSubmittedValue(context, request);
		if (value == null) {
			return errors;
		}
		if (!caseLabels.containsKey(value)) {
			errors.add(new FormSubmissionError(caseWidget, "This case is no longer in the lab"));
		}
		else if (!Context.hasPrivilege(PathologyConfig.PRIVILEGE_PROCESS_SPECIMENS)
				&& !Context.hasPrivilege(PathologyConfig.PRIVILEGE_SIGN_OUT)) {
			errors.add(new FormSubmissionError(caseWidget, "You do not have permission to record the results of the pathology lab"));
		}
		return errors;
	}

	@Override
	public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
		String value = getSubmittedValue(session.getContext(), request);
		if (value != null) {
			session.getSubmissionActions().addCustomFormSubmissionAction(
					new PathologyReportSubmissionAction(Integer.valueOf(value), resultsOfSteps));
		}
	}

	private String getSubmittedValue(FormEntryContext context, HttpServletRequest request) {
		Object value = caseWidget.getValue(context, request);
		return value == null ? null : StringUtils.trimToNull(value.toString());
	}

	/**
	 * @return the case the report was saved for, or else the patient's cases in the lab that have no report yet
	 */
	private List<PathologyCase> getCasesToChoose(Patient patient) {
		List<PathologyCase> ret = new ArrayList<PathologyCase>();
		if (linkedCase != null) {
			ret.add(linkedCase);
			return ret;
		}
		if (patient == null || patient.getPatientId() == null) {
			return ret;
		}
		for (PathologyCase pathologyCase : getCaseRepository().getCasesOfPatient(patient.getPatientId())) {
			if (pathologyCase.getStatus().isInLab() && pathologyCase.getReportEncounterId() == null) {
				ret.add(pathologyCase);
			}
		}
		return ret;
	}

	/**
	 * @return what the request fills on the report.  The date of surgery is the one on the request form, or else the date
	 * of the request.
	 */
	private Map<String, String> getRequestDetails(PathologyQueueRow request) {
		Map<String, String> ret = new LinkedHashMap<String, String>();
		ret.put("accessionNumber", request.getAccessionNumber());
		ret.put("surgeryDate", formatDate(request.getSurgeryDate() != null ? request.getSurgeryDate() : request.getTimeRequested()));
		ret.put("receivedDate", formatDate(request.getTimeReceived()));
		ret.put("physician", request.getRequestingPhysician());
		ret.put("facility", request.getHospitalOfOrigin());
		ret.put("clinicalInformation", request.getClinicalInformation());
		return ret;
	}

	/**
	 * @return the date as the date widgets of a form take it
	 */
	private String formatDate(Date date) {
		return date == null ? null : new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(date);
	}

	/**
	 * @return the value as JavaScript to put in a script element
	 */
	private String toJson(Object value) {
		try {
			return new ObjectMapper().writeValueAsString(value).replace("</", "<\\/");
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Unable to write the details of the pathology requests", e);
		}
	}

	private String getLabel(PathologyCase pathologyCase) {
		List<String> tests = new ArrayList<String>();
		Encounter request = Context.getEncounterService().getEncounter(pathologyCase.getEncounterId());
		for (TestOrder order : getCaseService().getActiveOrders(request)) {
			tests.add(order.getConcept().getDisplayString());
		}
		return StringUtils.defaultString(pathologyCase.getAccessionNumber()) + " - " + StringUtils.join(tests, ", ")
				+ " (" + pathologyCase.getStatus().getLabel() + ")";
	}

	private Set<Concept> getConcepts(Map<String, String> parameters, String attribute) {
		Set<Concept> ret = new HashSet<Concept>();
		for (String reference : StringUtils.split(StringUtils.defaultString(parameters.get(attribute)), ",")) {
			Concept concept = HtmlFormEntryUtil.getConcept(reference.trim());
			if (concept == null) {
				throw new IllegalArgumentException("Cannot find concept " + reference.trim() + " in the " + attribute
						+ " attribute of pathologyCase");
			}
			ret.add(concept);
		}
		return ret;
	}

	private PathologyCaseRepository getCaseRepository() {
		return Context.getRegisteredComponents(PathologyCaseRepository.class).get(0);
	}

	private PathologyCaseService getCaseService() {
		return Context.getRegisteredComponents(PathologyCaseService.class).get(0);
	}

	private PathologyQueueService getQueueService() {
		return Context.getRegisteredComponents(PathologyQueueService.class).get(0);
	}
}
