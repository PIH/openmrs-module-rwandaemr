package org.openmrs.module.rwandaemr.config;

/**
 * Components that implement this interface are executed at system startup and shutdown
 */
public interface Setup {

    void initialize();

    default void teardown() {}

}
