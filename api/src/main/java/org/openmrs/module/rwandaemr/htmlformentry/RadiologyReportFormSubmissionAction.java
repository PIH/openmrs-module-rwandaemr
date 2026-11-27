package org.openmrs.module.rwandaemr.htmlformentry;

import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.CustomFormSubmissionAction;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.rwandaemr.radiology.RadiologyReportService;

/**
 * Once HTML Form Entry has saved a radiology report form, records it as the report of the radiology test it was filled
 * in for
 */
public class RadiologyReportFormSubmissionAction implements CustomFormSubmissionAction {

	private final Integer orderId;

	public RadiologyReportFormSubmissionAction(Integer orderId) {
		this.orderId = orderId;
	}

	@Override
	public void applyAction(FormEntrySession session) {
		FormEntryContext.Mode mode = session.getContext().getMode();
		if (mode != FormEntryContext.Mode.ENTER && mode != FormEntryContext.Mode.EDIT) {
			return;
		}
		Encounter encounter = session.getEncounter();
		if (encounter == null) {
			throw new IllegalStateException("Unable to record the radiology report without an encounter");
		}
		Order order = Context.getOrderService().getOrder(orderId);
		if (order == null) {
			throw new IllegalStateException("Radiology order " + orderId + " not found");
		}
		getRadiologyReportService().saveFormReport(order, encounter, session.getXmlDefinition());
	}

	public Integer getOrderId() {
		return orderId;
	}

	protected RadiologyReportService getRadiologyReportService() {
		return Context.getRegisteredComponents(RadiologyReportService.class).get(0);
	}
}
