package org.openmrs.module.rwandaemr.fragment.controller.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openmrs.Provider;

public class TransferReasonDialogFragmentControllerTest {

    @Test
    public void shouldRenderWithoutLoadingProviders() {
        TransferReasonDialogFragmentController controller = new TransferReasonDialogFragmentController();

        assertDoesNotThrow(controller::controller);
    }

    @Test
    public void shouldBuildSortedOptionsFromAllActiveProviders() {
        Provider second = provider(2, "Zulu");
        Provider first = provider(1, "Alpha");
        Provider retired = provider(3, "Retired");
        retired.setRetired(true);

        List<Map<String, Object>> options = TransferReasonDialogFragmentController.toProviderOptions(
                Arrays.asList(second, retired, first));

        assertEquals(2, options.size());
        assertEquals(1, options.get(0).get("id"));
        assertEquals("Alpha", options.get(0).get("label"));
        assertEquals(2, options.get(1).get("id"));
        assertEquals("Zulu", options.get(1).get("label"));
    }

    private Provider provider(int id, String identifier) {
        Provider provider = new Provider(id);
        provider.setIdentifier(identifier);
        return provider;
    }
}
