package org.openmrs.module.rwandaemr.radiology;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.openmrs.Encounter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ORUToObsTranslatorTest extends BaseHL7TranslatorTest {

    @Test
    public void shouldTranslateFromORUToObs() throws Exception {
        String hl7Message = IOUtils.resourceToString("/orur01-example-1.txt", StandardCharsets.UTF_8);
        oruToObsTranslator.fromORU_R01(hl7Message);
        List<Encounter> encounters = rwandaEmrService.getEncounters();
        assertThat(encounters.size(), equalTo(2));
    }
}