/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.rwandaemr.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.mohbilling.businesslogic.GlobalBillUtil;
import org.openmrs.module.mohbilling.integration.insurance.MmiClosurePreview;
import org.openmrs.module.mohbilling.integration.insurance.RhipVoucherService;
import org.openmrs.module.mohbilling.model.GlobalBill;
import org.openmrs.module.rwandaemr.page.controller.admin.RhipVoucherSubmissionsPageController;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows, before the Confirm button of the RHIP voucher submissions page closes an MMI bill, how MMI would split it
 * (RHIP /voucher-closure with preview true). Nothing is billed.
 */
@Controller
public class RhipVoucherClosureRestController {

	private final Log log = LogFactory.getLog(getClass());

	private static final String PRIVILEGE_BILLING_ADMIN = "Billing Configuration - View Billing Admin";

	@RequestMapping(value = "/rest/v1/rwandaemr/rhipVoucher/closurePreview", method = RequestMethod.GET)
	@ResponseBody
	public SimpleObject previewClosure(@RequestParam("globalBillId") Integer globalBillId) {
		SimpleObject ret = new SimpleObject();
		if (!canConfirm()) {
			ret.add("success", false);
			ret.add("message", "You do not have permission to confirm RHIP vouchers.");
			return ret;
		}
		try {
			GlobalBill globalBill = GlobalBillUtil.getGlobalBill(globalBillId);
			RhipVoucherService voucherService = Context.getRegisteredComponent("rhipVoucherService", RhipVoucherService.class);
			return toSimpleObject(voucherService.previewVoucherClosureForGlobalBill(globalBill));
		}
		catch (Exception e) {
			log.error("Unable to preview the MMI closure of global bill " + globalBillId, e);
			ret.add("success", false);
			ret.add("message", "Unable to get the MMI cost split: " + e.getMessage());
			return ret;
		}
	}

	private SimpleObject toSimpleObject(MmiClosurePreview preview) {
		SimpleObject ret = new SimpleObject();
		ret.add("success", preview.isSuccess());
		ret.add("message", preview.getMessage());
		ret.add("voucherCode", preview.getVoucherCode());
		ret.add("billStatus", preview.getBillStatus());
		ret.add("totalAmount", preview.getTotalAmount());
		ret.add("insuranceAmount", preview.getInsuranceAmount());
		ret.add("patientAmount", preview.getPatientAmount());
		ret.add("otherAmount", preview.getOtherAmount());
		ret.add("insurancePercentage", preview.getInsurancePercentage());
		ret.add("patientPercentage", preview.getPatientPercentage());
		ret.add("otherPercentage", preview.getOtherPercentage());
		ret.add("rejectedLineCount", preview.getRejectedLineCount());
		List<SimpleObject> lines = new ArrayList<SimpleObject>();
		for (MmiClosurePreview.Line line : preview.getLines()) {
			SimpleObject o = new SimpleObject();
			o.add("code", line.getCode());
			o.add("name", line.getName());
			o.add("lineTotal", line.getLineTotal());
			o.add("priceSource", line.getPriceSource());
			o.add("fromApproval", line.isFromApproval());
			lines.add(o);
		}
		ret.add("lines", lines);
		return ret;
	}

	private boolean canConfirm() {
		return Context.hasPrivilege(RhipVoucherSubmissionsPageController.PRIVILEGE_CONFIRM)
				|| Context.hasPrivilege(RhipVoucherSubmissionsPageController.PRIVILEGE_SEND)
				|| Context.hasPrivilege(PRIVILEGE_BILLING_ADMIN);
	}
}
