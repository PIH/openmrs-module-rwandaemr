/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration.insurance;

import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RhipApprovalStatusCheckTask implements Runnable {

	private static final AtomicBoolean IS_EXECUTING = new AtomicBoolean(false);

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public void run() {
		if (!IS_EXECUTING.compareAndSet(false, true)) {
			log.warn(getClass().getSimpleName() + " is still executing, skipping this run to prevent overlap");
			return;
		}
		try {
			List<RhipApprovalStatusService> services = Context.getRegisteredComponents(RhipApprovalStatusService.class);
			if (services == null || services.isEmpty()) {
				log.debug("RhipApprovalStatusService not found, skipping " + getClass().getSimpleName());
				return;
			}
			int checked = services.get(0).updatePendingApprovalStatuses(25);
			if (checked > 0) {
				log.info("Checked {} pending RHIP approval request statuses", checked);
			}
			else {
				log.debug("No pending RHIP approval request statuses to check");
			}
		}
		catch (Exception e) {
			log.error("Error in " + getClass().getSimpleName(), e);
		}
		finally {
			IS_EXECUTING.set(false);
		}
	}
}
