package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RwandaLabIdGeneratorTest {

    IntegrationConfig integrationConfig;
    RwandaEmrService rwandaEmrService;
    MessageSourceService messageSourceService;
    RwandaLabIdGenerator generator;
    Location location;

    @BeforeEach
    public void setup() {
        Context.setUserContext(mock(UserContext.class));

        integrationConfig = mock(IntegrationConfig.class);
        rwandaEmrService = mock(RwandaEmrService.class);
        messageSourceService = mock(MessageSourceService.class);
        generator = new RwandaLabIdGenerator(integrationConfig, rwandaEmrService, messageSourceService);
        location = mock(Location.class);
        when(location.getName()).thenReturn("Kibogora Hospital");
    }

    @AfterEach
    public void tearDown() {
        Context.clearUserContext();
    }

    @Test
    public void isEnabled_shouldReturnTrueWhenFosaIdResolves() {
        when(integrationConfig.getFosaId(location)).thenReturn("KIB");

        assertThat(generator.isEnabled(location), equalTo(true));
    }

    @Test
    public void isEnabled_shouldReturnFalseWhenNoFosaIdConfigured() {
        when(integrationConfig.getFosaId(location)).thenReturn(null);

        assertThat(generator.isEnabled(location), equalTo(false));
    }

    @Test
    public void isEnabled_shouldReturnFalseWhenFosaIdIsBlank() {
        when(integrationConfig.getFosaId(location)).thenReturn("   ");

        assertThat(generator.isEnabled(location), equalTo(false));
    }

    @Test
    public void generateLabId_shouldThrowWithLocalizedMessageWhenNoFosaIdConfigured() {
        when(integrationConfig.getFosaId(location)).thenReturn(null);
        when(messageSourceService.getMessage("rwandaemr.labId.noFosaId", new Object[]{ "Kibogora Hospital" }, Context.getLocale()))
            .thenReturn("Unable to generate Lab ID: no FOSA ID is configured for location Kibogora Hospital");

        Exception e = assertThrows(IllegalStateException.class, () -> generator.generateLabId(location));

        assertThat(e.getMessage(), containsString("Kibogora Hospital"));
    }

    @Test
    public void generateLabId_shouldCombineFosaIdDateAndPaddedSequence() {
        when(integrationConfig.getFosaId(location)).thenReturn("KIB");
        when(rwandaEmrService.getNextLabIdSequenceValueForToday()).thenReturn(7);

        String labId = generator.generateLabId(location);

        assertThat(labId, containsString("KIB-"));
        assertThat(labId, containsString("-0007"));
    }

    @Test
    public void pad_shouldLeftPadToFourDigits() {
        assertThat(RwandaLabIdGenerator.pad(1), equalTo("0001"));
        assertThat(RwandaLabIdGenerator.pad(42), equalTo("0042"));
        assertThat(RwandaLabIdGenerator.pad(9999), equalTo("9999"));
    }

    @Test
    public void pad_shouldNotPadAtOrAboveTenThousand() {
        assertThat(RwandaLabIdGenerator.pad(10000), equalTo("10000"));
        assertThat(RwandaLabIdGenerator.pad(123456), equalTo("123456"));
    }
}
