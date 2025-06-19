package org.openmrs.module.rwandaemr.config;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.module.rwandaemr.event.CreateInsurancePatientListener;
import org.openmrs.module.rwandaemr.integration.UpdateClientRegistryPatientListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;

/**
 * Setup event listeners
 */
public class EventSetup {

    public static void setup() {
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getCreateInsurancePatientListener());
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getUpdateClientRegistryPatientListener());
        Event.subscribe(Patient.class, Event.Action.UPDATED.name(), getUpdateClientRegistryPatientListener());
        getRadiologyOrderEventListener().setup();
    }

    public static void teardown() {
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getCreateInsurancePatientListener());
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getUpdateClientRegistryPatientListener());
        Event.unsubscribe(Patient.class, Event.Action.UPDATED, getUpdateClientRegistryPatientListener());
        getRadiologyOrderEventListener().teardown();
    }

    public static CreateInsurancePatientListener getCreateInsurancePatientListener() {
        return Context.getRegisteredComponents(CreateInsurancePatientListener.class).get(0);
    }

    public static UpdateClientRegistryPatientListener getUpdateClientRegistryPatientListener() {
        return Context.getRegisteredComponents(UpdateClientRegistryPatientListener.class).get(0);
    }

    public static RadiologyOrderEventListener getRadiologyOrderEventListener() {
        return Context.getRegisteredComponents(RadiologyOrderEventListener.class).get(0);
    }

}
