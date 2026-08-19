package org.openmrs.module.rwandaemr.labs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmrs.Location;
import org.openmrs.module.rwandaemr.LabIdSequenceValue;
import org.openmrs.module.rwandaemr.RwandaEmrService;
import org.openmrs.module.rwandaemr.integration.IntegrationConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RwandaLabIdGeneratorTest {

    IntegrationConfig integrationConfig;
    RwandaEmrService rwandaEmrService;
    RwandaLabIdGenerator generator;
    Location location;

    @BeforeEach
    public void setup() {
        integrationConfig = mock(IntegrationConfig.class);
        rwandaEmrService = mock(RwandaEmrService.class);
        generator = new RwandaLabIdGenerator(integrationConfig, rwandaEmrService);
        location = mock(Location.class);
        when(location.getName()).thenReturn("Kibogora Hospital");
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
    public void generateLabId_shouldThrowWhenNoFosaIdConfigured() {
        when(integrationConfig.getFosaId(location)).thenReturn(null);

        Exception e = assertThrows(IllegalStateException.class, () -> generator.generateLabId(location));

        assertThat(e.getMessage(), containsString("Kibogora Hospital"));
        verify(rwandaEmrService, never()).getNextLabIdSequenceValueForToday();
    }

    @Test
    public void generateLabId_shouldCombineFosaIdDateAndPaddedSequence() {
        when(integrationConfig.getFosaId(location)).thenReturn("KIB");
        when(rwandaEmrService.getNextLabIdSequenceValueForToday()).thenReturn(new LabIdSequenceValue("20260819", 7));

        String labId = generator.generateLabId(location);

        assertThat(labId, equalTo("KIB-20260819-0007"));
    }

    @Test
    public void generateLabId_shouldTrimFosaId() {
        when(integrationConfig.getFosaId(location)).thenReturn("  KIB  ");
        when(rwandaEmrService.getNextLabIdSequenceValueForToday()).thenReturn(new LabIdSequenceValue("20260819", 1));

        String labId = generator.generateLabId(location);

        assertThat(labId, equalTo("KIB-20260819-0001"));
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
