package org.openmrs.module.rwandaemr.config;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.rwandaemr.event.CreateInsurancePatientListener;
import org.openmrs.module.rwandaemr.event.ExampleJmsEventListener;

/**
 * Setup event listeners
 */
public class EventSetup {

    private static final ExampleJmsEventListener jmsEventListener = new ExampleJmsEventListener();

    public static void setup() {
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getCreateInsurancePatientListener());
        Event.subscribe("dbevent", jmsEventListener);
    }

    public static void teardown() {
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getCreateInsurancePatientListener());
        Event.unsubscribe("dbevent", jmsEventListener);
    }

    public static EventListener getCreateInsurancePatientListener() {
        return Context.getRegisteredComponents(CreateInsurancePatientListener.class).get(0);
    }
}
