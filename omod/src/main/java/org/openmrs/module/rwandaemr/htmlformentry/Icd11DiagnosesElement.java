package org.openmrs.module.rwandaemr.htmlformentry;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.HiddenFieldWidget;
import org.openmrs.module.rwandaemr.htmlformentry.Icd11DiagnosesSubmissionAction;
import org.openmrs.module.rwandaemr.icd11.Icd11Service;
import org.openmrs.module.rwandaemr.icd11.model.Icd11DiagnosisSelection;
import org.openmrs.module.rwandaemr.icd11.model.Icd11PatientDiagnosis;
import org.openmrs.api.context.Context;
import org.openmrs.ui.framework.UiUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reusable HTML Form Entry element for selecting ICD-11 diagnoses.
 */
public class Icd11DiagnosesElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	public static final String FIELD_NAME = "icd11DiagnosesJson";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	@Setter
	private UiUtils uiUtils;

	private final String id;

	private final HiddenFieldWidget diagnosesWidget = new HiddenFieldWidget();

	private final ErrorWidget errorWidget = new ErrorWidget();

	private final String initialDiagnosesJson;

	public Icd11DiagnosesElement(FormEntryContext context, Map<String, String> parameters) {
		id = StringUtils.defaultIfBlank(parameters.get("id"), "icd11-diagnoses");
		if (!id.matches("[A-Za-z][A-Za-z0-9_-]*")) {
			throw new IllegalArgumentException("ICD-11 diagnoses widget id must be a valid HTML id");
		}
		context.registerWidget(diagnosesWidget, FIELD_NAME);
		context.registerErrorWidget(diagnosesWidget, errorWidget);
		initialDiagnosesJson = toJson(getInitialSelections(context));
		diagnosesWidget.setInitialValue(StringEscapeUtils.escapeHtml4(initialDiagnosesJson));
	}

	@Override
	public String generateHtml(FormEntryContext context) {
		Map<String, Object> fragmentConfig = new HashMap<>();
		fragmentConfig.put("id", id);
		fragmentConfig.put("hiddenInputHtml", diagnosesWidget.generateHtml(context));
		fragmentConfig.put("initialDiagnosesBase64", Base64.getEncoder().encodeToString(
				initialDiagnosesJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		fragmentConfig.put("readOnly", FormEntryContext.Mode.VIEW == context.getMode());
		try {
			return errorWidget.generateHtml(context)
					+ uiUtils.includeFragment("rwandaemr", "field/patient/icd11Diagnoses", fragmentConfig);
		}
		catch (Exception e) {
			return e.getMessage();
		}
	}

	@Override
	public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest request) {
		List<FormSubmissionError> errors = new ArrayList<>();
		try {
			Set<String> selectionKeys = new HashSet<>();
			int primaryDiagnoses = 0;
			for (Icd11DiagnosisSelection selection : parseSelections(request.getParameter(FIELD_NAME))) {
				validateSelection(selection);
				if ("primary".equalsIgnoreCase(selection.getDiagnosisType()) && ++primaryDiagnoses > 1) {
					throw new IllegalArgumentException("Only one primary ICD-11 diagnosis may be selected");
				}
				String selectionKey = selection.getEntityUri().trim().toLowerCase() + "|"
						+ selection.getDiagnosisType().toLowerCase() + "|" + selection.getCertainty().toLowerCase();
				if (!selectionKeys.add(selectionKey)) {
					throw new IllegalArgumentException("Duplicate ICD-11 diagnosis selection");
				}
			}
		}
		catch (IllegalArgumentException e) {
			errors.add(new FormSubmissionError(diagnosesWidget, e.getMessage()));
		}
		return errors;
	}

	@Override
	public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
		session.getSubmissionActions().addCustomFormSubmissionAction(
				new Icd11DiagnosesSubmissionAction(request.getParameter(FIELD_NAME)));
	}

	protected List<Icd11DiagnosisSelection> parseSelections(String diagnosesJson) {
		return Icd11DiagnosesSubmissionAction.parseSelections(diagnosesJson);
	}

	private List<Icd11DiagnosisSelection> getInitialSelections(FormEntryContext context) {
		List<Icd11DiagnosisSelection> selections = new ArrayList<>();
		if (context.getExistingPatient() == null || context.getExistingEncounter() == null) {
			return selections;
		}
		for (Icd11PatientDiagnosis diagnosis : Context.getService(Icd11Service.class)
				.getDiagnoses(context.getExistingPatient(), context.getExistingEncounter())) {
			Icd11DiagnosisSelection selection = new Icd11DiagnosisSelection();
			selection.setIcd11Code(diagnosis.getIcd11Code());
			selection.setEntityUri(diagnosis.getEntityUri());
			selection.setFoundationUri(diagnosis.getFoundationUri());
			selection.setTitle(diagnosis.getTitle());
			selection.setLinearization(diagnosis.getLinearization());
			selection.setDiagnosisType(diagnosis.getDiagnosisType());
			selection.setCertainty(diagnosis.getCertainty());
			selections.add(selection);
		}
		return selections;
	}

	private void validateSelection(Icd11DiagnosisSelection selection) {
		if (selection == null || StringUtils.isBlank(selection.getEntityUri()) || StringUtils.isBlank(selection.getTitle())) {
			throw new IllegalArgumentException("Each ICD-11 diagnosis requires an entity URI and title");
		}
		if (!"primary".equalsIgnoreCase(selection.getDiagnosisType())
				&& !"secondary".equalsIgnoreCase(selection.getDiagnosisType())) {
			throw new IllegalArgumentException("ICD-11 diagnosis type must be primary or secondary");
		}
		if (!"confirmed".equalsIgnoreCase(selection.getCertainty())
				&& !"presumed".equalsIgnoreCase(selection.getCertainty())) {
			throw new IllegalArgumentException("ICD-11 diagnosis certainty must be confirmed or presumed");
		}
	}

	private String toJson(List<Icd11DiagnosisSelection> selections) {
		try {
			return OBJECT_MAPPER.writeValueAsString(selections);
		}
		catch (Exception e) {
			throw new IllegalStateException("Unable to render ICD-11 diagnoses widget", e);
		}
	}
}
