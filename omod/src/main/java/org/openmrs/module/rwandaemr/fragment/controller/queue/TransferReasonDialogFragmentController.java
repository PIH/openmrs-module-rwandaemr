package org.openmrs.module.rwandaemr.fragment.controller.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.api.ProviderService;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.fragment.action.FragmentActionResult;
import org.openmrs.ui.framework.fragment.action.ObjectResult;

public class TransferReasonDialogFragmentController {

    public void controller() {
        // The fragment has no initial model; providers are loaded only when the dialog opens.
    }

    public FragmentActionResult getProviders(@SpringBean("providerService") ProviderService providerService) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("providers", toProviderOptions(providerService.getAllProviders(false)));
        return new ObjectResult(response);
    }

    static List<Map<String, Object>> toProviderOptions(List<Provider> providers) {
        List<Map<String, Object>> options = new ArrayList<Map<String, Object>>();
        if (providers == null) {
            return options;
        }
        for (Provider provider : providers) {
            if (provider == null || provider.getId() == null || Boolean.TRUE.equals(provider.getRetired())) {
                continue;
            }
            String label = StringUtils.trimToNull(provider.getName());
            if (label == null) {
                label = StringUtils.trimToNull(provider.getIdentifier());
            }
            if (label == null) {
                label = "Unnamed provider";
            }
            Map<String, Object> option = new LinkedHashMap<String, Object>();
            option.put("id", provider.getId());
            option.put("label", label);
            options.add(option);
        }
        Collections.sort(options, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                return String.valueOf(left.get("label")).compareToIgnoreCase(String.valueOf(right.get("label")));
            }
        });
        return options;
    }
}
