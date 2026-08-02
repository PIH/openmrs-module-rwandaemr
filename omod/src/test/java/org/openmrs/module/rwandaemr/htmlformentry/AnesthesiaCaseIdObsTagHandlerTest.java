package org.openmrs.module.rwandaemr.htmlformentry;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AnesthesiaCaseIdObsTagHandlerTest {

    @Test
    public void shouldNormalizeAValidCaseId() {
        assertThat(AnesthesiaCaseIdObsTagHandler.normalizeCaseId(
                " 3A6CA730-DF91-4E4F-B647-5E62BCBFB4E5 "),
                is("3a6ca730-df91-4e4f-b647-5e62bcbfb4e5"));
    }

    @Test
    public void shouldRejectMissingOrInvalidCaseIds() {
        assertThat(AnesthesiaCaseIdObsTagHandler.normalizeCaseId(null), is(""));
        assertThat(AnesthesiaCaseIdObsTagHandler.normalizeCaseId("not-an-operation-id"), is(""));
    }
}
