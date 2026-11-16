package org.openmrs.module.rwandaemr.htmlformentry;

import org.openmrs.module.htmlformentry.BadFormDesignException;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionController;
import org.openmrs.module.htmlformentry.element.ObsSubmissionElement;
import org.openmrs.module.htmlformentry.handler.SubstitutionTagHandler;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnesthesiaCaseIdObsTagHandler extends SubstitutionTagHandler {

    static final String OPERATION_ID_CONCEPT = "c8641c43-5d65-4b6b-b6d3-2dbebf020ffc";

    @Override
    protected String getSubstitution(FormEntrySession session, FormSubmissionController controller,
                                     Map<String, String> attributes) throws BadFormDesignException {
        Map<String, String> obsAttributes = new HashMap<>(attributes);
        obsAttributes.put("conceptId", OPERATION_ID_CONCEPT);
        obsAttributes.put("defaultValue", getAnesthesiaCaseId());
        obsAttributes.put("required", "true");

        ObsSubmissionElement<?> element = new ObsSubmissionElement<>(session.getContext(), obsAttributes);
        controller.addAction(element);
        return element.generateHtml(session.getContext());
    }

    static String normalizeCaseId(String value) {
        if (value == null) {
            return "";
        }

        try {
            return UUID.fromString(value.trim()).toString();
        }
        catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String getAnesthesiaCaseId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return "";
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return normalizeCaseId(request.getParameter("anesthesiaCaseId"));
    }
}
