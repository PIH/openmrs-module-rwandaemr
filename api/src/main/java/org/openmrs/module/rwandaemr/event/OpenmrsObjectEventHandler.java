/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The goal of this interface is to allow registering components that can respond to create/delete/change events
 * for a particular type of OpenmrsObject at various stages of the transaction lifecycle
 */
public interface OpenmrsObjectEventHandler {

	Logger log = LoggerFactory.getLogger(OpenmrsObjectEventHandler.class);

	/**
	 * Called after the start of a transaction.  Useful if the handler needs to aggregate operations within a tx
	 */
	default void afterTransactionBegin(int transactionDepth, List<OpenmrsObjectEvent> events) {
		log.trace("afterTransactionBegin.  transactionDepth = {}; events = {}", transactionDepth, events);
	}

	/**
	 * Called before the completion of a transaction.  Useful if the handler needs to aggregate operations within a tx
	 */
	default void beforeTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events) {
		log.trace("beforeTransactionCompletion.  transactionDepth = {}; events = {}", transactionDepth, events);
	}

	/**
	 * Called after the completion of a transaction.  Useful if the handler needs to aggregate operations within a tx
	 */
	default void afterTransactionCompletion(int transactionDepth, List<OpenmrsObjectEvent> events, int status) {
		log.trace("afterTransactionCompletion.  status = {}; transactionDepth = {}; events = {}", status, transactionDepth, events);
	}
}
