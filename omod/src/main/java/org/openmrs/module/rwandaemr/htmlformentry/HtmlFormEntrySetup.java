package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.FormResource;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.customdatatype.datatype.FreeTextDatatype;
import org.openmrs.module.htmlformentry.HtmlForm;
import org.openmrs.module.htmlformentry.HtmlFormEntryConstants;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.module.htmlformentry.HtmlFormEntryUtil;
import org.openmrs.ui.framework.BasicUiUtils;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.util.OpenmrsUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.openmrs.module.htmlformentry.HtmlFormEntryConstants.HTML_FORM_TAG;

public class HtmlFormEntrySetup {

    protected static Log log = LogFactory.getLog(HtmlFormEntrySetup.class);

    public static final String INSURANCE_POLICY_OBS_TAG = "insurancePolicyObs";

    public static final String FORM_ENGINE_RESOURCE_NAME = "formEngine";

    public static final String FORM_ENGINE_RESOURCE_VALUE = "htmlformentry";

    public static final Map<String, String> CONFIG_FORMS = new HashMap<>();

    public static void setup() {
        UiUtils uiUtils = Context.getRegisteredComponent("uiUtils", BasicUiUtils.class);
        HtmlFormEntryService htmlFormEntryService = Context.getService(HtmlFormEntryService.class);
        FormService formService = Context.getService(FormService.class);

        // Register tag handlers
        InsurancePolicyObsTagHandler tagHandler = new InsurancePolicyObsTagHandler();
        tagHandler.setUiUtils(uiUtils);
        htmlFormEntryService.addHandler(INSURANCE_POLICY_OBS_TAG, tagHandler);
        log.warn("Registered " + INSURANCE_POLICY_OBS_TAG + " tag with htmlformentry");

        // Add form engine resources
        int numResourcesSaved = 0;
        for (HtmlForm htmlForm : htmlFormEntryService.getAllHtmlForms()) {
            FormResource formResource = formService.getFormResource(htmlForm.getForm(), FORM_ENGINE_RESOURCE_NAME);
            if (formResource == null) {
                FormResource resource = new FormResource();
                resource.setForm(htmlForm.getForm());
                resource.setName(FORM_ENGINE_RESOURCE_NAME);
                resource.setValueReferenceInternal(FORM_ENGINE_RESOURCE_VALUE);
                resource.setDatatypeClassname(FreeTextDatatype.class.getName());
                formService.saveFormResource(resource);
                numResourcesSaved++;
            }
        }
        log.warn("Registered " + numResourcesSaved + " resource(s) for htmlformentry");

        // Cache reference to configuration file that loaded each form
        File formDir = Paths.get(OpenmrsUtil.getApplicationDataDirectory(), "configuration", "htmlforms").toFile();
        for (File file : Objects.requireNonNull(formDir.listFiles())) {
            try {
                String xmlData = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                Document doc = HtmlFormEntryUtil.stringToDocument(xmlData);
                Node htmlFormNode = HtmlFormEntryUtil.findChild(doc, HTML_FORM_TAG);
                Map<String, String> nodeAttributes = HtmlFormEntryUtil.getNodeAttributes(htmlFormNode);
                String htmlFormUuid = nodeAttributes.get(HtmlFormEntryConstants.HTML_FORM_UUID_ATTRIBUTE);
                CONFIG_FORMS.put(htmlFormUuid, file.getName());
            }
            catch (Exception e) {
                throw new RuntimeException("Error loading htmlform attributes for " + file.getName(), e);
            }
        }
        log.warn("Registered " + CONFIG_FORMS.size() + " form(s) loaded from configuration for htmlformentry");
        log.warn("HTMLFormEntry Configured");
    }
}
