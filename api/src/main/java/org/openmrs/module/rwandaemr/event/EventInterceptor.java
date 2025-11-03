/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.event;

import org.apache.commons.lang.BooleanUtils;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Transaction;
import org.hibernate.type.Type;
import org.openmrs.OpenmrsObject;
import org.openmrs.Voidable;
import org.openmrs.api.context.Context;
import org.openmrs.util.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.transaction.Synchronization;
import java.io.Serializable;

/**
 * The goal of this interceptor is to listen for all data being saved via Hibernate, and to trigger events
 * that can participate at various points of the transaction lifecycle.  In particular, this enables watching for
 * changes to OpenmrsObjects that are being saved to the database, and modifying or extending the data to be saved
 * within the same transaction.
 */
@Component
public class EventInterceptor extends EmptyInterceptor {

	private static final Logger log = LoggerFactory.getLogger(EventInterceptor.class);

	/**
	 * For each transaction, ensure all handlers are invoked at the appropriate place in the transaction lifecycle
	 */
	@Override
	public void afterTransactionBegin(Transaction tx) {
		log.trace("afterTransactionBegin");
		for (EventHandler<?> handler : Context.getRegisteredComponents(EventHandler.class)) {
			handler.afterTransactionBegin();
		}
		tx.registerSynchronization(new Synchronization() {

			@Override
			public void beforeCompletion() {
				log.trace("beforeTransactionCompletion");
				for (EventHandler<?> handler : Context.getRegisteredComponents(EventHandler.class)) {
					handler.beforeTransactionCompletion();
				}
			}

			@Override
			public void afterCompletion(int status) {
				log.trace("afterTransactionCompletion");
				for (EventHandler<?> handler : Context.getRegisteredComponents(EventHandler.class)) {
					handler.afterTransactionCompletion(status);
				}
			}
		});
	}

	/**
	 * This is called when an entity is created, not when it is updated
	 */
	@Override
	public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		log.trace("onSave: {}", entity);
		handleCreatedEntity(entity);
		return false;
	}

	/**
	 * This is called only when an entity is updated, not when it is created
	 * The voided property is a special case that we consider generally as representing a delete/undelete operation
	 */
	@Override
	public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState, String[] propertyNames, Type[] types) {
		log.trace("onFlushDirty: {}", entity);
		boolean wasVoided = false;
		boolean isVoided = false;
		if (entity instanceof Voidable) {
			for (int i=0; i<propertyNames.length; i++) {
				String propertyName = propertyNames[i];
				if (propertyName.equals("voided")) {
					wasVoided = BooleanUtils.isTrue((Boolean) previousState[i]);
					isVoided = BooleanUtils.isTrue((Boolean) currentState[i]);
				}
			}
		}
		if (isVoided) {
			if (!wasVoided) {
				handleDeletedEntity(entity);
			}
		}
		else {
			if (wasVoided) {
				handleCreatedEntity(entity);
			}
			else {
				handleUpdatedEntity(entity);
			}
		}
		return false;
	}

	/**
	 * This is called when an entity is deleted
	 */
	@Override
	public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		log.trace("onDelete: {}", entity);
		handleDeletedEntity(entity);
	}

	/**
	 * Called when a new entity is created or an existing entity is unvoided
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected void handleCreatedEntity(Object entity) {
		log.trace("handleCreatedEntity: {}", entity);
		for (EventHandler handler : HandlerUtil.getHandlersForType(EventHandler.class, entity.getClass())) {
			handler.handleCreatedEntity((OpenmrsObject) entity);
		}
	}

	/**
	 * Called when a new non-voided entity is updated
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected void handleUpdatedEntity(Object entity) {
		log.trace("handleUpdatedEntity: {}", entity);
		for (EventHandler handler : HandlerUtil.getHandlersForType(EventHandler.class, entity.getClass())) {
			handler.handleUpdatedEntity((OpenmrsObject) entity);
		}
	}

	/**
	 * Called when an existing entity is either deleted or voided
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected void handleDeletedEntity(Object entity) {
		log.trace("handleDeletedEntity: {}", entity);
		for (EventHandler handler : HandlerUtil.getHandlersForType(EventHandler.class, entity.getClass())) {
			handler.handleDeletedEntity((OpenmrsObject) entity);
		}
	}
}
