package org.openmrs.module.rwandaemr.appointment;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;

public final class ProviderLicenseUtil {

    public static final String ATTRIBUTE_TYPE_NAME = "Provider License";

    private ProviderLicenseUtil() {
    }

    public static String getLicense(Provider provider) {
        if (provider == null) {
            return null;
        }
        for (ProviderAttribute attribute : provider.getActiveAttributes()) {
            ProviderAttributeType attributeType = attribute.getAttributeType();
            if (attributeType != null
                    && !Boolean.TRUE.equals(attributeType.getRetired())
                    && ATTRIBUTE_TYPE_NAME.equalsIgnoreCase(StringUtils.trim(attributeType.getName()))) {
                String license = StringUtils.trimToNull(attribute.getValueReference());
                if (license != null) {
                    return license;
                }
            }
        }
        return null;
    }
}
