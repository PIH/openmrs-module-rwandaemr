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

import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.rwandaemr.config.AuthenticationSetup;
import org.openmrs.module.rwandaemr.config.EventSetup;
import org.openmrs.module.rwandaemr.config.GlobalResourceSetup;
import org.openmrs.module.rwandaemr.config.InitializerSetup;
import org.openmrs.module.rwandaemr.config.NameTemplateSetup;
import org.openmrs.module.rwandaemr.config.ProviderCleanup;
import org.openmrs.module.rwandaemr.config.ReportSetup;
import org.openmrs.module.rwandaemr.config.ServerSetup;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.module.rwandaemr.config.Setup;
import org.openmrs.module.rwandaemr.event.PatientEventListener;
import org.openmrs.module.rwandaemr.htmlformentry.HtmlFormEntrySetup;
import org.openmrs.module.rwandaemr.radiology.HL7ListenerSetup;
import org.openmrs.module.rwandaemr.radiology.ORUR01MessageListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;
import org.openmrs.module.rwandaemr.task.RwandaEmrTimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class RwandaEmrActivator extends BaseModuleActivator implements DaemonTokenAware {
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	// This will disable loading configuration in the initializer module activator, as we want control of it from here
	static {
		System.setProperty("initializer.startup.load", "disabled");
	}

	public static final List<Class<? extends  Setup>> setupClasses = Arrays.asList(
			ServerSetup.class,
			InitializerSetup.class,
			AuthenticationSetup.class,
			NameTemplateSetup.class,
			ReportSetup.class,
			GlobalResourceSetup.class,
			ProviderCleanup.class,
			HtmlFormEntrySetup.class,
			EventSetup.class,
			HL7ListenerSetup.class
	);

	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
        log.warn("Rwanda EMR Module Started.  Initiating configuration...");
		for (Setup setupComponent : getSetupComponents()) {
			log.warn("{} - initializing", setupComponent.getClass().getSimpleName());
			setupComponent.initialize();
		}
		RwandaEmrTimerTask.setEnabled(true);
		log.warn("Rwanda EMR configuration complete");
	}

	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {
		List<Setup> setups = getSetupComponents();
		Collections.reverse(setups);
		for (Setup setupComponent : setups) {
			setupComponent.teardown();
		}
		log.info("Rwanda EMR Module stopped");
	}

	public List<Setup> getSetupComponents() {
		List<Setup> ret = new ArrayList<>();
		for (Class<? extends Setup> setupClass : setupClasses) {
            ret.addAll(Context.getRegisteredComponents(setupClass));
		}
		return ret;
	}

	@Override
	public void setDaemonToken(DaemonToken daemonToken) {
		RwandaEmrTimerTask.setDaemonToken(daemonToken);
		PatientEventListener.setDaemonToken(daemonToken);
		HieEventListener.setDaemonToken(daemonToken);
		RadiologyOrderEventListener.setDaemonToken(daemonToken);
		ORUR01MessageListener.setDaemonToken(daemonToken);
	}
}
