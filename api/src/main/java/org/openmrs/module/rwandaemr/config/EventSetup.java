package org.openmrs.module.rwandaemr.config;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.module.rwandaemr.event.CreateInsurancePatientListener;
import org.openmrs.module.rwandaemr.event.HieEventListener;
import org.openmrs.module.rwandaemr.integration.UpdateClientRegistryPatientListener;
import org.openmrs.module.rwandaemr.integration.UpdateShrEncounterListener;
import org.openmrs.module.rwandaemr.integration.UpdateShrObsListener;
import org.openmrs.module.rwandaemr.radiology.RadiologyOrderEventListener;

/**
 * Setup event listeners
 */
public class EventSetup {

    public static void setup() {
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getCreateInsurancePatientListener());
        Event.subscribe(Patient.class, Event.Action.CREATED.name(), getUpdateClientRegistryPatientListener());
        Event.subscribe(Patient.class, Event.Action.UPDATED.name(), getUpdateClientRegistryPatientListener());
        /**
         * Event for EMR to HIE encounter synchronization
         */
        Event.subscribe(Encounter.class, Event.Action.CREATED.name(), getUpdateShrEncounterEventListener());
        /**
         * Event for EMR to HIE OBS synchronization
         */
        Event.subscribe(Obs.class, Event.Action.CREATED.name(), getUpdateShrObservationEventListener());
        getRadiologyOrderEventListener().setup();
    }

    public static void teardown() {
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getCreateInsurancePatientListener());
        Event.unsubscribe(Patient.class, Event.Action.CREATED, getUpdateClientRegistryPatientListener());
        Event.unsubscribe(Patient.class, Event.Action.UPDATED, getUpdateClientRegistryPatientListener());
        Event.unsubscribe(Encounter.class, Event.Action.CREATED, getUpdateShrEncounterEventListener());
        Event.unsubscribe(Obs.class, Event.Action.CREATED, getUpdateShrObservationEventListener());
        getRadiologyOrderEventListener().teardown();
    }

    public static CreateInsurancePatientListener getCreateInsurancePatientListener() {
        return Context.getRegisteredComponents(CreateInsurancePatientListener.class).get(0);
    }

    public static UpdateClientRegistryPatientListener getUpdateClientRegistryPatientListener() {
        return Context.getRegisteredComponents(UpdateClientRegistryPatientListener.class).get(0);
    }

    public static HieEventListener getUpdateShrEncounterEventListener(){
        return Context.getRegisteredComponents(UpdateShrEncounterListener.class).get(0);
    }

    public static HieEventListener getUpdateShrObservationEventListener(){
        return Context.getRegisteredComponents(UpdateShrObsListener.class).get(0);
    }
    public static RadiologyOrderEventListener getRadiologyOrderEventListener() {
        return Context.getRegisteredComponents(RadiologyOrderEventListener.class).get(0);
    }

}
