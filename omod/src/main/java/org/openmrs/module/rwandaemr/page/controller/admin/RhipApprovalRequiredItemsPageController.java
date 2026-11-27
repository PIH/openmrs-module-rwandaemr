/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.page.controller.admin;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.api.context.Context;
import org.openmrs.module.appui.UiSessionContext;
import org.openmrs.module.mohbilling.integration.insurance.RhipApprovalRequiredProducts;
import org.openmrs.module.uicommons.UiCommonsConstants;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.page.PageModel;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

/**
 * The procedures each insurer must approve (moh_bill_approval_required_product, refreshed from RHIP every few hours)
 * with the billing item each is linked to, so it is easy to see which billing items need approval and which required
 * codes have no billing item
 */
public class RhipApprovalRequiredItemsPageController {

	public String get(PageModel model, UiUtils ui, UiSessionContext sessionContext,
	                  @RequestParam(value = "insuranceType", required = false) String insuranceType,
	                  @RequestParam(value = "show", required = false) String show,
	                  @RequestParam(value = "q", required = false) String q) {
		if (!canView()) {
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_ERROR_MESSAGE,
					"You do not have permission to view RHIP approvals.");
			return "redirect:" + ui.pageLink("coreapps", "systemadministration/systemAdministration");
		}
		RhipApprovalRequiredProducts products = products();
		List<RhipApprovalRequiredProducts.Row> all = products.getRows(insuranceType);
		List<RhipApprovalRequiredProducts.Row> rows = new ArrayList<RhipApprovalRequiredProducts.Row>();
		int linked = 0;
		int notLinked = 0;
		int removed = 0;
		java.util.Date lastRefresh = null;
		String search = StringUtils.trimToEmpty(q).toLowerCase();
		for (RhipApprovalRequiredProducts.Row row : all) {
			if (row.isRemoved()) {
				removed++;
			}
			else if (row.getFacilityServicePriceId() != null) {
				linked++;
			}
			else {
				notLinked++;
			}
			if (row.getLastSeen() != null && (lastRefresh == null || row.getLastSeen().after(lastRefresh))) {
				lastRefresh = row.getLastSeen();
			}
			boolean shown = "removed".equals(show) ? row.isRemoved()
					: !row.isRemoved() && ("unlinked".equals(show) ? row.getFacilityServicePriceId() == null
					: !"linked".equals(show) || row.getFacilityServicePriceId() != null);
			if (shown && !search.isEmpty()) {
				shown = StringUtils.containsIgnoreCase(row.getProductCode(), search)
						|| StringUtils.containsIgnoreCase(row.getProductName(), search)
						|| StringUtils.containsIgnoreCase(row.getFacilityServiceName(), search);
			}
			if (shown) {
				rows.add(row);
			}
		}
		model.addAttribute("rows", rows);
		model.addAttribute("insuranceTypes", products.getInsuranceTypes());
		model.addAttribute("insuranceType", StringUtils.defaultString(insuranceType));
		model.addAttribute("show", StringUtils.defaultIfBlank(show, "all"));
		model.addAttribute("q", StringUtils.defaultString(q));
		model.addAttribute("linkedCount", linked);
		model.addAttribute("notLinkedCount", notLinked);
		model.addAttribute("removedCount", removed);
		model.addAttribute("lastRefresh", lastRefresh);
		model.addAttribute("canRefresh", Context.hasPrivilege(RhipApprovalsPageController.PRIVILEGE_REQUEST)
				|| Context.hasPrivilege("Billing Configuration - View Billing Admin"));
		return null;
	}

	public String post(UiUtils ui, UiSessionContext sessionContext,
	                   @RequestParam(value = "insuranceType", required = false) String insuranceType) {
		if (Context.hasPrivilege(RhipApprovalsPageController.PRIVILEGE_REQUEST)
				|| Context.hasPrivilege("Billing Configuration - View Billing Admin")) {
			List<String> results = products().refreshAll();
			sessionContext.getSession().setAttribute(UiCommonsConstants.SESSION_ATTRIBUTE_INFO_MESSAGE,
					"Refreshed from RHIP: " + StringUtils.join(results, "; "));
		}
		return "redirect:" + ui.pageLink("rwandaemr", "admin/rhipApprovalRequiredItems")
				+ (StringUtils.isBlank(insuranceType) ? "" : "?insuranceType=" + insuranceType.trim());
	}

	private RhipApprovalRequiredProducts products() {
		return Context.getRegisteredComponent("rhipApprovalRequiredProducts", RhipApprovalRequiredProducts.class);
	}

	private boolean canView() {
		return Context.hasPrivilege(RhipApprovalsPageController.PRIVILEGE_VIEW)
				|| Context.hasPrivilege("Billing Configuration - View Billing Admin");
	}
}
