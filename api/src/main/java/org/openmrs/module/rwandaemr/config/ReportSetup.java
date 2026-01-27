package org.openmrs.module.rwandaemr.config;

import org.openmrs.module.reporting.config.ReportLoader;
import org.springframework.stereotype.Component;

@Component
public class ReportSetup implements Setup {

    /**
     * Register reports from config
     */
    @Override
    public void initialize() {
        ReportLoader.loadReportsFromConfig();
    }
}
