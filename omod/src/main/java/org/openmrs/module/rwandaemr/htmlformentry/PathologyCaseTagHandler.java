package org.openmrs.module.rwandaemr.htmlformentry;

import java.util.Map;

import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;

public class PathologyCaseTagHandler extends SubstitutionTagHandler {

    @Override
    protected String getSubstitution(FormEntrySession session, FormSubmissionController controller,
                                     Map<String, String> attributes) throws BadFormDesignException {
        PathologyCaseElement element = new PathologyCaseElement(session.getContext(), attributes);
        controller.addAction(element);
        return element.generateHtml(session.getContext());
    }
}
