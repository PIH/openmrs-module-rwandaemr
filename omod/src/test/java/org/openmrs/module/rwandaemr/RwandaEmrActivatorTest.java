package org.openmrs.module.rwandaemr;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class RwandaEmrActivatorTest {

    @Test
    public void shouldDisableInitializerModuleLoadingDirectly() {
        RwandaEmrActivator activator = new RwandaEmrActivator();
        assertThat(System.getProperty("initializer.startup.load"), equalTo("disabled"));
    }

}
