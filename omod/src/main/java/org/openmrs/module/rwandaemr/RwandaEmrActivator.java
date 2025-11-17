/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.rwandaemr;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.reporting.config.ReportLoader;
import org.openmrs.module.rwandaemr.config.AuthenticationSetup;
import org.openmrs.module.rwandaemr.config.EventSetup;
import org.openmrs.module.rwandaemr.config.GlobalResourceSetup;
import org.openmrs.module.rwandaemr.config.InitializerSetup;
import org.openmrs.module.rwandaemr.config.NameTemplateSetup;
import org.openmrs.module.rwandaemr.config.ServerSetup;
import org.openmrs.module.rwandaemr.event.PatientEventListener;
import org.openmrs.module.rwandaemr.htmlformentry.HtmlFormEntrySetup;
import org.openmrs.module.rwandaemr.radiology.HL7ListenerSetup;
import org.openmrs.module.rwandaemr.radiology.ORUR01MessageListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;
import org.openmrs.module.rwandaemr.task.RwandaEmrTimerTask;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class RwandaEmrActivator extends BaseModuleActivator implements DaemonTokenAware {
	
	protected Log log = LogFactory.getLog(getClass());

	// This will disable loading configuration in the initializer module activator, as we want control of it from here
	static {
		System.setProperty("initializer.startup.load", "disabled");
	}

	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
        log.warn("Rwanda EMR Module Started.  Initiating configuration...");
		ServerSetup.setup();
		InitializerSetup.install();
		AuthenticationSetup.configureAuthenticationSchemes();
		NameTemplateSetup.setup();
		ReportLoader.loadReportsFromConfig();
		GlobalResourceSetup.includeGlobalResources();
		HtmlFormEntrySetup.setup();
		EventSetup.setup();
		HL7ListenerSetup.startup();
		RwandaEmrTimerTask.setEnabled(true);
		log.warn("Rwanda EMR configuration complete");
	}

	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {
		HL7ListenerSetup.shutdown();
		EventSetup.teardown();
		log.info("Rwanda EMR Module stopped");
	}

	@Override
	public void setDaemonToken(DaemonToken daemonToken) {
		RwandaEmrTimerTask.setDaemonToken(daemonToken);
		PatientEventListener.setDaemonToken(daemonToken);
		RadiologyOrderEventListener.setDaemonToken(daemonToken);
		ORUR01MessageListener.setDaemonToken(daemonToken);
	}
}
