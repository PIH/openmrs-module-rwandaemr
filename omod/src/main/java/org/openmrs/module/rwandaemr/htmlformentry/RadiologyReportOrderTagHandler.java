package org.openmrs.module.rwandaemr.htmlformentry;

import java.util.Map;

import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;

/**
 * Handles the radiologyReportOrder tag, which makes a form the report of a radiology test, see
 * {@link RadiologyReportOrderElement}
 */
public class RadiologyReportOrderTagHandler extends SubstitutionTagHandler {

	@Override
	protected String getSubstitution(FormEntrySession session, FormSubmissionController controller,
									 Map<String, String> attributes) throws BadFormDesignException {
		String formUuid = session.getForm() == null ? null : session.getForm().getUuid();
		RadiologyReportOrderElement element = new RadiologyReportOrderElement(session.getContext(), formUuid);
		controller.addAction(element);
		return element.generateHtml(session.getContext());
	}
}
