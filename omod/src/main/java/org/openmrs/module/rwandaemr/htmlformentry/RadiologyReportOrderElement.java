package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.action.FormSubmissionControllerAction;
import org.openmrs.module.htmlformentry.element.HtmlGeneratorElement;
import org.openmrs.module.htmlformentry.widget.DropdownWidget;
import org.openmrs.module.htmlformentry.widget.ErrorWidget;
import org.openmrs.module.htmlformentry.widget.Option;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Makes a form the report of a radiology test, for tests reported with a form in place of the report editor (see the
 * rwandaemr.radiology.reportForms global property):
 * <pre>
 * &lt;radiologyReportOrder/&gt;
 * </pre>
 * Saving the form records it as the test's report, as a report from the editor is: final, signed by the radiologist and
 * visible to clinicians, with the answers of the form as its text.  The test is chosen from the patient's tests configured
 * for this form that have their images and no report yet, and a form opened from the radiology report page chooses its
 * test from the radiologyOrderId parameter of the page.  Once saved, the form stays the report of its test, and editing
 * it records a correction.  A form saved without a test is not a radiology report.
 */
public class RadiologyReportOrderElement implements HtmlGeneratorElement, FormSubmissionControllerAction {

	public static final String REQUEST_PARAMETER = "radiologyOrderId";

	private static final String NONE_LABEL = "None: not the report of a radiology test";

	private final DropdownWidget orderWidget = new DropdownWidget();

	private final ErrorWidget errorWidget = new ErrorWidget();

	/**
	 * The test the form was saved as the report of, if it is being viewed or edited
	 */
	private final Order linkedOrder;

	private final Map<String, String> orderLabels = new LinkedHashMap<String, String>();

	public RadiologyReportOrderElement(FormEntryContext context, String formUuid) {
		Encounter existing = context.getExistingEncounter();
		linkedOrder = existing == null || existing.getEncounterId() == null ? null :
				getReportService().getReportedOrder(existing);
		for (Order order : getOrdersToChoose(context.getExistingPatient(), formUuid)) {
			orderLabels.put(order.getOrderId().toString(), getLabel(order));
		}

		context.registerWidget(orderWidget);
		context.registerErrorWidget(orderWidget, errorWidget);
		// A saved report stays the report of its test
		if (linkedOrder == null) {
			orderWidget.addOption(new Option(NONE_LABEL, "", false));
		}
		for (Map.Entry<String, String> orderLabel : orderLabels.entrySet()) {
			orderWidget.addOption(new Option(orderLabel.getValue(), orderLabel.getKey(), false));
		}
		if (linkedOrder != null) {
			orderWidget.setInitialValue(linkedOrder.getOrderId().toString());
		}
	}

	@Override
	public String generateHtml(FormEntryContext context) {
		if (FormEntryContext.Mode.VIEW == context.getMode()) {
			String label = linkedOrder == null ? NONE_LABEL : getLabel(linkedOrder);
			return "<span class=\"value\">" + StringEscapeUtils.escapeHtml4(label) + "</span>";
		}
		String fieldName = context.getFieldName(orderWidget);
		// Chooses the test the form was opened for from the radiology report page, or the only test the patient has
		// waiting for this report
		String script = "<script type=\"text/javascript\">jQuery(function() {" +
				"var select = jQuery('select[name=\"" + fieldName + "\"]');" +
				"if (!select.val()) {" +
				"var match = /[?&]" + REQUEST_PARAMETER + "=(\\d+)/.exec(window.location.search);" +
				"if (match && select.find('option[value=\"' + match[1] + '\"]').length) { select.val(match[1]); }" +
				"else if (!match && select.find('option').length === 2) { select.val(select.find('option').eq(1).val()); }" +
				"}" +
				"});</script>";
		String help = linkedOrder != null ? "" : "<div class=\"radiology-report-order-help\" style=\"font-size: 0.9em; " +
				"color: #607d8b;\">Saving the form records it as the final report of this test, signed by you, and " +
				"clinicians can see it. Only tests whose images have been received are listed.</div>";
		return orderWidget.generateHtml(context) + errorWidget.generateHtml(context) + help + script;
	}

	@Override
	public Collection<FormSubmissionError> validateSubmission(FormEntryContext context, HttpServletRequest request) {
		List<FormSubmissionError> errors = new ArrayList<FormSubmissionError>();
		String value = getSubmittedValue(context, request);
		if (value == null) {
			if (linkedOrder != null) {
				errors.add(new FormSubmissionError(orderWidget, "This form is the report of "
						+ linkedOrder.getOrderNumber() + " and must stay linked to it"));
			}
			return errors;
		}
		if (!orderLabels.containsKey(value)) {
			errors.add(new FormSubmissionError(orderWidget, "This test can no longer be reported on this form"));
			return errors;
		}
		if (!Context.hasPrivilege(RadiologyReportService.PRIVILEGE_ENTER_REPORT)) {
			errors.add(new FormSubmissionError(orderWidget, "You do not have permission to enter radiology reports"));
			return errors;
		}
		Order order = Context.getOrderService().getOrder(Integer.valueOf(value));
		String reason = getReportService().getReasonReportCannotBeEntered(order);
		if (reason != null) {
			errors.add(new FormSubmissionError(orderWidget, reason));
		}
		else if (getReportService().getCurrentProvider() == null) {
			errors.add(new FormSubmissionError(orderWidget,
					"Your user account is not linked to a provider, so the report cannot be signed"));
		}
		return errors;
	}

	@Override
	public void handleSubmission(FormEntrySession session, HttpServletRequest request) {
		String value = getSubmittedValue(session.getContext(), request);
		if (value != null) {
			session.getSubmissionActions().addCustomFormSubmissionAction(
					new RadiologyReportFormSubmissionAction(Integer.valueOf(value)));
		}
	}

	private String getSubmittedValue(FormEntryContext context, HttpServletRequest request) {
		Object value = orderWidget.getValue(context, request);
		return value == null ? null : StringUtils.trimToNull(value.toString());
	}

	/**
	 * @return the test the form was saved as the report of, or else the patient's tests configured for this form that
	 * can be reported and have no report yet
	 */
	private List<Order> getOrdersToChoose(Patient patient, String formUuid) {
		List<Order> ret = new ArrayList<Order>();
		if (linkedOrder != null) {
			ret.add(linkedOrder);
			return ret;
		}
		if (patient == null || patient.getPatientId() == null || formUuid == null) {
			return ret;
		}
		RadiologyReportService reportService = getReportService();
		Map<Concept, Boolean> reportedOnThisForm = new HashMap<Concept, Boolean>();
		for (Order order : Context.getOrderService().getAllOrdersByPatient(patient)) {
			if (!(order instanceof TestOrder) || order.getAction() == Order.Action.DISCONTINUE
					|| order.getVoided() || order.getDateStopped() != null) {
				continue;
			}
			boolean onThisForm = reportedOnThisForm.computeIfAbsent(order.getConcept(),
					c -> Objects.equals(formUuid, reportService.getReportFormUuid(order)));
			if (onThisForm && reportService.canEnterReport(order) && reportService.getReport(order) == null
					&& reportService.getReportFormEncounter(order) == null) {
				ret.add(order);
			}
		}
		ret.sort((a, b) -> b.getDateCreated() == null || a.getDateCreated() == null ? 0 :
				b.getDateCreated().compareTo(a.getDateCreated()));
		return ret;
	}

	private String getLabel(Order order) {
		Date requested = order.getDateCreated() != null ? order.getDateCreated() : order.getDateActivated();
		return order.getConcept().getDisplayString() + " - " + StringUtils.defaultString(order.getOrderNumber())
				+ (requested == null ? "" : " (requested " + new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(requested) + ")");
	}

	private RadiologyReportService getReportService() {
		return Context.getRegisteredComponents(RadiologyReportService.class).get(0);
	}
}
