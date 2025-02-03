package org.openmrs.module.rwandaemr.htmlformentry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.ui.framework.BasicUiUtils;
import org.openmrs.ui.framework.UiUtils;

public class HtmlFormEntrySetup {

    protected static Log log = LogFactory.getLog(HtmlFormEntrySetup.class);

    public static final String INSURANCE_POLICY_OBS_TAG = "insurancePolicyObs";

    public static void setup() {
        UiUtils uiUtils = Context.getRegisteredComponent("uiUtils", BasicUiUtils.class);
        InsurancePolicyObsTagHandler tagHandler = new InsurancePolicyObsTagHandler();
        tagHandler.setUiUtils(uiUtils);
        Context.getService(HtmlFormEntryService.class).addHandler(INSURANCE_POLICY_OBS_TAG, tagHandler);
        log.warn("Registered " + INSURANCE_POLICY_OBS_TAG + " tag with htmlformentry");
    }
}
