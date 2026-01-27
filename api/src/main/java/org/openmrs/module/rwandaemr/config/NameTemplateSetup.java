package org.openmrs.module.rwandaemr.config;

import org.openmrs.api.context.Context;
import org.openmrs.layout.name.NameSupport;
import org.openmrs.layout.name.NameTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NameTemplateSetup implements Setup {

    /**
     * There may be multiple name support beans registered incorrectly.  This ensures all are configured the same way if so
     */
    @Override
    public void initialize() {
        configureNameTemplate(NameSupport.getInstance());
        for (NameSupport nameSupport : Context.getRegisteredComponents(NameSupport.class)) {
            configureNameTemplate(nameSupport);
        }
    }

    protected static void configureNameTemplate(NameSupport nameSupport) {
        NameTemplate nameTemplate = new NameTemplate();
        nameTemplate.setCodeName("short");  // we are redefining the short name template for use in our context

        Map<String, String> nameMappings = new HashMap<String, String>();
        nameMappings.put("familyName", "PersonName.familyName");
        nameMappings.put("middleName", "PersonName.middleName");
        nameMappings.put("givenName", "PersonName.givenName");
        nameTemplate.setNameMappings(nameMappings);

        Map<String, String> sizeMappings = new HashMap<String, String>();
        sizeMappings.put("givenName", "30");
        sizeMappings.put("familyName", "30");
        sizeMappings.put("middleName", "30");
        nameTemplate.setSizeMappings(sizeMappings);

        List<String> lineByLineFormat = new ArrayList<String>();
        lineByLineFormat.add("givenName");
        lineByLineFormat.add("middleName");
        lineByLineFormat.add("familyName");

        nameTemplate.setLineByLineFormat(lineByLineFormat);
        nameTemplate.setMaxTokens(2);

        List<NameTemplate> templates = new ArrayList<NameTemplate>();
        templates.add(nameTemplate);

        // we blow away the other templates here, is that a bad thing?
        nameSupport.setLayoutTemplates(templates);
        nameSupport.setDefaultLayoutFormat("short");
    }
}
