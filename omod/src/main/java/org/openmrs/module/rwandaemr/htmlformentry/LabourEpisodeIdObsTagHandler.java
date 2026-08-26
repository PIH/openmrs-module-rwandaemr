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

public class LabourEpisodeIdObsTagHandler extends SubstitutionTagHandler {

    public static final String EPISODE_ID_CONCEPT = "7b7b62d3-6e42-42c4-a0c8-e0e1b5d0c8f4";

    @Override
    protected String getSubstitution(FormEntrySession session, FormSubmissionController controller,
                                     Map<String, String> attributes) throws BadFormDesignException {
        Map<String, String> obsAttributes = new HashMap<>(attributes);
        obsAttributes.put("conceptId", EPISODE_ID_CONCEPT);
        obsAttributes.put("defaultValue", getLabourEpisodeId());
        obsAttributes.put("required", "true");

        ObsSubmissionElement<?> element = new ObsSubmissionElement<>(session.getContext(), obsAttributes);
        controller.addAction(element);
        return element.generateHtml(session.getContext());
    }

    public static String normalizeEpisodeId(String value) {
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

    private String getLabourEpisodeId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            return "";
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        return normalizeEpisodeId(request.getParameter("labourEpisodeId"));
    }
}
