package org.openmrs.module.rwandaemr.config;

import org.openmrs.module.dbevent.DbEventListenerConfig;
import org.openmrs.module.dbevent.listener.EventModulePublisher;
import org.openmrs.module.rwandaemr.event.ExampleDbEventListener;

/**
 * Setup event listeners
 */
public class DbEventSetup {

    public static void setup() {
        loadLibraries();
        setupDbEventPublisher();
        setupExampleDbEventListener();
    }

    public static void loadLibraries() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Class.forName("com.mysql.jdbc.Driver");
        }
        catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find MySQL driver", e);
        }
    }

    public static void setupDbEventPublisher() {
        EventModulePublisher eventListener = new EventModulePublisher("dbevent");
        String sourceName = "EventModulePublisher";
        int sourceId = sourceName.hashCode();
        DbEventListenerConfig listenerConfig = new DbEventListenerConfig(sourceId, sourceName);

        // This configures no initial data snapshot to occur, as we just want to stream new changes for this example
        listenerConfig.setDebeziumProperty("snapshot.mode", "schema_only");
        listenerConfig.setEnabled(true);

        // Fire it up
        eventListener.init(listenerConfig);
    }

    public static void setupExampleDbEventListener() {
        ExampleDbEventListener eventListener = new ExampleDbEventListener();
        String sourceName = "ExampleEventSource";
        int sourceId = sourceName.hashCode();
        DbEventListenerConfig listenerConfig = new DbEventListenerConfig(sourceId, sourceName);

        // This configures no initial data snapshot to occur, as we just want to stream new changes for this example
        listenerConfig.setDebeziumProperty("snapshot.mode", "schema_only");
        listenerConfig.setEnabled(true);

        // Fire it up
        eventListener.init(listenerConfig);
    }
}
