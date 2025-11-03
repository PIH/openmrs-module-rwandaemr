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
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Transaction;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.type.Type;
import org.openmrs.OpenmrsObject;
import org.openmrs.Voidable;
import org.openmrs.api.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.transaction.Synchronization;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The goal of this interceptor is to listen for all data being saved via Hibernate, and to trigger events
 * that can participate at various points of the transaction lifecycle.  In particular, this enables watching for
 * changes to OpenmrsObjects that are being saved to the database, and modifying or extending the data to be saved
 * within the same transaction.
 */
@Component
public class OpenmrsObjectEventInterceptor extends EmptyInterceptor {

	private static final Logger log = LoggerFactory.getLogger(OpenmrsObjectEventInterceptor.class);

	private final ThreadLocal<Integer> transactionDepth = new ThreadLocal<>();
	private final ThreadLocal<List<OpenmrsObjectEvent>> events = new ThreadLocal<>();

	/**
	 * For each transaction, ensure all handlers are invoked at the appropriate place in the transaction lifecycle
	 */
	@Override
	public void afterTransactionBegin(Transaction tx) {
		log.trace("afterTransactionBegin");
		if (transactionDepth.get() == null) {
			transactionDepth.set(0);
		}
		transactionDepth.set(transactionDepth.get() + 1);
		if (events.get() == null) {
			events.set(new ArrayList<>());
		}
		for (OpenmrsObjectEventHandler handler : Context.getRegisteredComponents(OpenmrsObjectEventHandler.class)) {
			handler.afterTransactionBegin(transactionDepth.get(), events.get());
		}
		tx.registerSynchronization(new Synchronization() {

			@Override
			public void beforeCompletion() {
				log.trace("beforeTransactionCompletion");
				for (OpenmrsObjectEventHandler handler : Context.getRegisteredComponents(OpenmrsObjectEventHandler.class)) {
					log.trace("{}.beforeTransactionCompletion", handler.getClass().getSimpleName());
					handler.beforeTransactionCompletion(transactionDepth.get(), events.get());
				}
			}

			@Override
			public void afterCompletion(int status) {
				log.trace("afterTransactionCompletion");
				try {
					for (OpenmrsObjectEventHandler handler : Context.getRegisteredComponents(OpenmrsObjectEventHandler.class)) {
						log.trace("{}.afterTransactionCompletion", handler.getClass().getSimpleName());
						handler.afterTransactionCompletion(transactionDepth.get(), events.get(), status);
					}
				}
				finally {
					transactionDepth.set(transactionDepth.get() - 1);
					if (transactionDepth.get() <= 0) {
						transactionDepth.remove();
						events.remove();
					}
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
		handleEntity(entity, OpenmrsObjectEvent.Operation.CREATE);
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
				handleEntity(entity, OpenmrsObjectEvent.Operation.DELETE);
			}
		}
		else {
			if (wasVoided) {
				handleEntity(entity, OpenmrsObjectEvent.Operation.CREATE);
			}
			else {
				handleEntity(entity, OpenmrsObjectEvent.Operation.UPDATE);
			}
		}
		return false;
	}

	@Override
	public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
		log.trace("onCollectionRemove");
		if (collection instanceof PersistentCollection) {
			PersistentCollection persistentCollection = (PersistentCollection) collection;
			handleEntity(persistentCollection.getOwner(), OpenmrsObjectEvent.Operation.UPDATE);
		}
		else {
			log.trace("collection is not a PersistentCollection");
		}
	}

	@Override
	public void onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
		log.trace("onCollectionRecreate");
		if (collection instanceof PersistentCollection) {
			PersistentCollection persistentCollection = (PersistentCollection) collection;
			handleEntity(persistentCollection.getOwner(), OpenmrsObjectEvent.Operation.UPDATE);
		}
		else {
			log.trace("collection is not a PersistentCollection");
		}
	}

	@Override
	public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
		log.trace("onCollectionUpdate");
		if (collection instanceof PersistentCollection) {
			PersistentCollection persistentCollection = (PersistentCollection) collection;
			handleEntity(persistentCollection.getOwner(), OpenmrsObjectEvent.Operation.UPDATE);
		}
		else {
			log.trace("collection is not a PersistentCollection");
		}
	}

	/**
	 * This is called when an entity is deleted
	 */
	@Override
	public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		log.trace("onDelete: {}", entity);
		handleEntity(entity, OpenmrsObjectEvent.Operation.DELETE);
	}

	/**
	 * Called when an entity is created, updated, or deleted with the given operation
	 */
	protected void handleEntity(Object entity, OpenmrsObjectEvent.Operation operation) {
		log.trace("handleEntity {}: {}", operation, entity);
		if (entity instanceof OpenmrsObject) {
			Integer currentTransactionDepth = transactionDepth.get();
			if (currentTransactionDepth == null) {
				log.warn("No transaction depth found.  Not handling entity");
			}
			else {
				OpenmrsObject openmrsObject = (OpenmrsObject) entity;
				OpenmrsObjectEvent event = new OpenmrsObjectEvent(openmrsObject, operation, currentTransactionDepth);
				events.get().add(event);
				log.trace("{}", event);
			}
		}
		else {
			log.trace("entity is not an openmrsObject");
		}
	}
}
