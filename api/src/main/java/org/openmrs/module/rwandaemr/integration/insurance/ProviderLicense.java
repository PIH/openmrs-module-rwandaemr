/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.integration.insurance;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.ProviderAttributeType;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.util.ConfigUtil;

import java.util.Collection;

/**
 * The practitioner licence number sent to RHIP. Clinicians enter it on their profile in the transferapp module
 * (transferProfile page), which keeps it in the "Provider License" provider attribute. The attribute type configured
 * in mohbilling.rhipVoucher.providerLicenseAttributeTypeUuid is only read when that attribute is empty.
 */
public final class ProviderLicense {

	public static final String PROVIDER_LICENSE_ATTRIBUTE_TYPE_NAME = "Provider License";

	public static final String LEGACY_ATTRIBUTE_TYPE_UUID_PROPERTY = "mohbilling.rhipVoucher.providerLicenseAttributeTypeUuid";

	private ProviderLicense() {
	}

	/**
	 * @return the licence number on any of the user's providers, or null
	 */
	public static String forUser(User user) {
		if (user == null || user.getPerson() == null) {
			return null;
		}
		Collection<Provider> providers = Context.getProviderService().getProvidersByPerson(user.getPerson(), false);
		if (providers == null) {
			return null;
		}
		for (ProviderAttributeType type : attributeTypes()) {
			for (Provider provider : providers) {
				String value = value(provider, type);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	/**
	 * @return the licence number on this provider, or null
	 */
	public static String forProvider(Provider provider) {
		for (ProviderAttributeType type : attributeTypes()) {
			String value = value(provider, type);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static java.util.List<ProviderAttributeType> attributeTypes() {
		java.util.List<ProviderAttributeType> ret = new java.util.ArrayList<ProviderAttributeType>();
		for (ProviderAttributeType type : Context.getProviderService().getAllProviderAttributeTypes(false)) {
			if (type != null && PROVIDER_LICENSE_ATTRIBUTE_TYPE_NAME.equals(type.getName())) {
				ret.add(type);
			}
		}
		String legacyUuid = StringUtils.trimToNull(ConfigUtil.getProperty(LEGACY_ATTRIBUTE_TYPE_UUID_PROPERTY));
		if (legacyUuid != null) {
			ProviderAttributeType legacy = Context.getProviderService().getProviderAttributeTypeByUuid(legacyUuid);
			if (legacy != null && !ret.contains(legacy)) {
				ret.add(legacy);
			}
		}
		return ret;
	}

	private static String value(Provider provider, ProviderAttributeType type) {
		if (provider == null || type == null || provider.getActiveAttributes() == null) {
			return null;
		}
		for (ProviderAttribute attribute : provider.getActiveAttributes()) {
			if (attribute != null && type.equals(attribute.getAttributeType())) {
				String value = attribute.getValueReference();
				if (StringUtils.isBlank(value) && attribute.getValue() != null) {
					value = attribute.getValue().toString();
				}
				if (StringUtils.isNotBlank(value)) {
					return value.trim();
				}
			}
		}
		return null;
	}
}
