package org.openmrs.module.rwandaemr.config;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.rwandaemr.event.CreateInsurancePatientListener;
import org.openmrs.module.rwandaemr.integration.UpdateClientRegistryPatientListener;

/**
 * Setup event listeners
 */
public class EventSetup {

    public static void setup() {
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getCreateInsurancePatientListener());
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getUpdateClientRegistryPatientListener());
        // TODO: Determine if we want to update the client registry on update, and if so, which updates
        //Event.subscribe(Patient.class, Event.Action.UPDATED.name(), getUpdateClientRegistryPatientListener());
    }

    public static void teardown() {
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getCreateInsurancePatientListener());
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getUpdateClientRegistryPatientListener());
        // TODO: See above
        // Event.unsubscribe(Patient.class, Event.Action.UPDATED, getUpdateClientRegistryPatientListener());
    }

    public static EventListener getCreateInsurancePatientListener() {
        return Context.getRegisteredComponents(CreateInsurancePatientListener.class).get(0);
    }

    public static EventListener getUpdateClientRegistryPatientListener() {
        return Context.getRegisteredComponents(UpdateClientRegistryPatientListener.class).get(0);
    }

}
