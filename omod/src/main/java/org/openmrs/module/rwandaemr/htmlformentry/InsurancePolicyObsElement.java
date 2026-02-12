/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.htmlformentry;

import ca.uhn.hl7v2.util.StringUtil;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionActions;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.HiddenFieldWidget;
import org.openmrs.module.htmlformentry.widget.WidgetFactory;
import org.openmrs.module.mohbilling.model.InsurancePolicy;
import org.openmrs.module.mohbilling.service.BillingService;
import org.openmrs.ui.framework.UiUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InsurancePolicyObsElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

    @Setter
    private UiUtils uiUtils;

    private HiddenFieldWidget hiddenInsuranceConceptWidget;
    private HiddenFieldWidget hiddenInsuranceNumberWidget;

    private final String id;
    private final String initialEncounterDate;
    private final String encounterDateFieldId;
    private final String label;
    private final String classes;
    private final String hideEmptyLabel;
    private final String emptyOptionLabel;
    private final String showAll;
    private final boolean required;
    private final Concept insuranceTypeConcept;
    private final Concept insuranceNumberConcept;
    private final Obs initialInsuranceTypeObs;
    private final Obs initialInsuranceNumberObs;

    public InsurancePolicyObsElement(FormEntryContext context, Map<String, String> parameters) {

        insuranceTypeConcept = getConceptFromGlobalProperty("registration.insuranceTypeConcept");
        initialInsuranceTypeObs = getExistingObs(context, insuranceTypeConcept);

        insuranceNumberConcept = getConceptFromGlobalProperty("registration.insuranceNumberConcept");
        initialInsuranceNumberObs = getExistingObs(context, insuranceNumberConcept);

        // Configuration from tag attributes
        id = parameters.get("id");
        if (StringUtils.isBlank(id)) {
            throw new IllegalArgumentException("id attribute is required on an InsurancePolicyObsElement");
        }

        Date initialData = context.getExistingEncounter() == null ? new Date() : context.getExistingEncounter().getEncounterDatetime();
        initialEncounterDate = new SimpleDateFormat("yyyy-MM-dd").format(initialData);

        encounterDateFieldId = parameters.get("encounterDateFieldId");
        label = parameters.get("label");
        classes = parameters.get("classes");
        hideEmptyLabel = parameters.get("hideEmptyLabel");
        emptyOptionLabel = parameters.get("emptyOptionLabel");
        showAll = parameters.get("showAll");
        required = Boolean.parseBoolean(parameters.get("required"));
    }

    @Override
    public String generateHtml(FormEntryContext context) {
        if (FormEntryContext.Mode.VIEW == context.getMode()) {
            Concept insuranceType = initialInsuranceTypeObs == null ? null : initialInsuranceTypeObs.getValueCoded();
            String insuranceNumber = initialInsuranceNumberObs == null ? null : initialInsuranceNumberObs.getValueText();
            String insuranceName = insuranceType == null ? "" : insuranceType.getDisplayString();
            if (insuranceType != null && insuranceNumber != null) {
                InsurancePolicy p = getInsurancePolicy(context.getExistingPatient(), insuranceType, insuranceNumber);
                insuranceName = p.getInsurance().getName();
            }
            StringBuilder ret = new StringBuilder();
            if (StringUtil.isNotBlank(label)) {
                ret.append("<label for=\"").append(id).append("\">");
                ret.append(" ").append(getTranslation(label)).append(" ");
                ret.append("</label>");
            }
            ret.append("<div id=\"").append(id).append("\" class=\"insurance-field\">");
            ret.append("<div class=\"insurance-type\">");
            ret.append(WidgetFactory.displayValue(insuranceName));
            ret.append("</div>");
            ret.append("<div class=\"insurance-number\">");
            ret.append(WidgetFactory.displayValue(insuranceNumber == null ? "" : insuranceNumber));
            ret.append("</div>");
            return ret.toString();
        }
        else {
            hiddenInsuranceConceptWidget = new HiddenFieldWidget();
            ErrorWidget insuranceConceptErrorWidget = new ErrorWidget();
            context.registerWidget(hiddenInsuranceConceptWidget);
            context.registerErrorWidget(hiddenInsuranceConceptWidget, insuranceConceptErrorWidget);
            String insuranceConceptFieldName = context.getFieldName(hiddenInsuranceConceptWidget);

            hiddenInsuranceNumberWidget = new HiddenFieldWidget();
            ErrorWidget insuranceNumberErrorWidget = new ErrorWidget();
            context.registerWidget(hiddenInsuranceNumberWidget);
            context.registerErrorWidget(hiddenInsuranceNumberWidget, insuranceNumberErrorWidget);
            String insuranceNumberFieldName = context.getFieldName(hiddenInsuranceNumberWidget);

            Map<String, Object> fragmentConfig = new HashMap<>();
            fragmentConfig.put("id", id);
            fragmentConfig.put("patient", context.getExistingPatient());
            fragmentConfig.put("insuranceConceptFormFieldName", insuranceConceptFieldName);
            fragmentConfig.put("insuranceNumberFormFieldName", insuranceNumberFieldName);
            if (initialInsuranceTypeObs != null && initialInsuranceTypeObs.getValueCoded() != null) {
                fragmentConfig.put("initialInsuranceConcept", initialInsuranceTypeObs.getValueCoded().getConceptId().toString());
            }
            if (initialInsuranceNumberObs != null && initialInsuranceNumberObs.getValueText() != null) {
                fragmentConfig.put("initialInsuranceNumber", initialInsuranceNumberObs.getValueText());
            }
            fragmentConfig.put("initialEncounterDate", initialEncounterDate);
            fragmentConfig.put("encounterDateFieldId", encounterDateFieldId);
            fragmentConfig.put("label", label);
            fragmentConfig.put("classes", classes);
            fragmentConfig.put("hideEmptyLabel", hideEmptyLabel);
            fragmentConfig.put("emptyOptionLabel", emptyOptionLabel);
            fragmentConfig.put("showAll", showAll);

            try {
                StringBuilder sb = new StringBuilder();
                sb.append(insuranceConceptErrorWidget.generateHtml(context));
                sb.append(insuranceNumberErrorWidget.generateHtml(context));
                sb.append(uiUtils.includeFragment("rwandaemr", "field/patient/insuranceObs", fragmentConfig));
                if (required) {
                    sb.append("<span class='required'>*</span>");
                }
                return sb.toString();
            }
            catch (Exception e) {
                // if we are validating/submitting the form, then this method is being called from a fragment action method
                // and the UiUtils we have access to doesn't have a FragmentIncluder. That's okay, because we don't actually
                // need to generate the HTML, so we can pass on this exception.
                // (This is hacky, but I don't see a better way to do it.)
                return e.getMessage();
            }
        }
    }

    @Override
    public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest request) {
        List<FormSubmissionError> errors = new ArrayList<>();
        String insuranceType = request.getParameter(context.getFieldName(hiddenInsuranceConceptWidget));
        if (StringUtils.isEmpty(insuranceType) && required) {
            errors.add(new FormSubmissionError(hiddenInsuranceConceptWidget, "Required"));
        }
        if (StringUtils.isNotBlank(insuranceType)) {
            Concept insuranceTypeConcept = HtmlFormEntryUtil.getConcept(insuranceType);
            if (insuranceTypeConcept == null) {
                errors.add(new FormSubmissionError(hiddenInsuranceConceptWidget, "Invalid"));
            }
        }
        String insuranceNumber = request.getParameter(context.getFieldName(hiddenInsuranceNumberWidget));
        if (StringUtils.isEmpty(insuranceNumber) && required) {
            errors.add(new FormSubmissionError(hiddenInsuranceNumberWidget, "Required"));
        }
        return errors;
    }

    @Override
    public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
        FormSubmissionActions submissionActions = session.getSubmissionActions();
        String insuranceType = request.getParameter(session.getContext().getFieldName(hiddenInsuranceConceptWidget));
        if (StringUtils.isNotBlank(insuranceType)) {
            Concept insuranceTypeValue = HtmlFormEntryUtil.getConcept(insuranceType);
            if (initialInsuranceTypeObs != null) {
                if (!insuranceTypeValue.equals(initialInsuranceTypeObs.getValueCoded())) {
                    submissionActions.modifyObs(initialInsuranceTypeObs, insuranceTypeConcept, insuranceTypeValue, null, null);
                }
            }
            else {
                submissionActions.createObs(insuranceTypeConcept, insuranceTypeValue, null, null);
            }
        }
        String insuranceNumber = request.getParameter(session.getContext().getFieldName(hiddenInsuranceNumberWidget));
        if (StringUtils.isNotBlank(insuranceNumber)) {
            if (initialInsuranceNumberObs != null) {
                if (!insuranceNumber.equals(initialInsuranceNumberObs.getValueText())) {
                    submissionActions.modifyObs(initialInsuranceNumberObs, insuranceNumberConcept, insuranceNumber, null, null);
                }
            }
            else {
                submissionActions.createObs(insuranceNumberConcept, insuranceNumber, null, null);
            }
        }
    }

    protected Concept getConceptFromGlobalProperty(String globalPropertyName) {
        AdministrationService adminService = Context.getAdministrationService();
        String gpValue = adminService.getGlobalProperty(globalPropertyName);
        if (StringUtils.isBlank(gpValue)) {
            throw new IllegalStateException("Global property '" + globalPropertyName + "' was not found");
        }
        Concept concept = HtmlFormEntryUtil.getConcept(gpValue);
        if (concept == null) {
            throw new IllegalStateException("Global property '" + globalPropertyName + "' does not refer to a valid concept");
        }
        return concept;
    }

    protected Obs getExistingObs(FormEntryContext context, Concept concept) {
        List<Obs> existingList = context.removeExistingObs(concept);
        if (existingList != null && !existingList.isEmpty()) {
            if (existingList.size() > 1) {
                throw new IllegalStateException("Multiple observations of type '" + concept + "' found");
            }
            return existingList.get(0);
        }
        return null;
    }

    protected InsurancePolicy getInsurancePolicy(Patient patient, Concept insuranceType, String cardNumber) {
        for (InsurancePolicy p : Context.getService(BillingService.class).getAllInsurancePoliciesByPatient(patient)) {
            if (p.getInsurance().getConcept() != null && p.getInsurance().getConcept().equals(insuranceType)) {
                if (p.getInsuranceCardNo() != null && p.getInsuranceCardNo().equals(cardNumber)) {
                    return p;
                }
            }
        }
        return null;
    }

    protected String getTranslation(String key) {
        return Context.getMessageSourceService().getMessage(key);
    }
}
