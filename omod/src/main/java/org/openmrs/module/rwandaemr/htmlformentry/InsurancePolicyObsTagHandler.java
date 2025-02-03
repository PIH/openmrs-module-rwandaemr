
package org.openmrs.module.rwandaemr.htmlformentry;

import lombok.Setter;
import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;
import org.openmrs.ui.framework.UiUtils;

import java.util.Map;

@Setter
public class InsurancePolicyObsTagHandler extends SubstitutionTagHandler {

    private UiUtils uiUtils;

    @Override
    protected String getSubstitution(FormEntrySession session, FormSubmissionController controller, Map<String, String> attributes) throws BadFormDesignException {
        InsurancePolicyObsElement element = new InsurancePolicyObsElement(session.getContext(), attributes);
        element.setUiUtils(session.getAttribute("uiUtils") != null ? (UiUtils) session.getAttribute("uiUtils") : uiUtils);  // pull from the session if available, otherwise fall back to basic ui utils
        controller.addAction(element);
        return element.generateHtml(session.getContext());
    }
}
