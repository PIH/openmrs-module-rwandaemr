package org.openmrs.module.rwandaemr.htmlformentry;

import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;

import java.util.Map;

/**
 * &lt;insuranceApprovalCheck/&gt; (also registered as &lt;mmiApprovalCheck/&gt;, its former name), placed at the end of a
 * form where clinicians choose procedures: see {@link InsuranceApprovalCheckElement}
 */
public class InsuranceApprovalCheckTagHandler extends SubstitutionTagHandler {

	@Override
	protected String getSubstitution(FormEntrySession session, FormSubmissionController controller,
	                                 Map<String, String> attributes) throws BadFormDesignException {
		InsuranceApprovalCheckElement element = new InsuranceApprovalCheckElement(session);
		controller.addAction(element);
		return element.generateHtml(session.getContext());
	}
}
