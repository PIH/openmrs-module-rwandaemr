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

import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;

/**
 * MMI allows one open reception per patient. When /patient-reception is called while one is still open, RHIP answers
 * HTTP 400 with success false and data describing the open reception (its visitReferenceNumber and status), so that
 * its bill can be settled first.
 */
public final class MmiPendingReception {

	private MmiPendingReception() {
	}

	/**
	 * @return true when a failed /patient-reception response carries the reception that is still open
	 */
	public static boolean isPendingReception(Integer responseCode, boolean success, String receptionNumber) {
		return !success && responseCode != null && responseCode == 400 && StringUtils.isNotBlank(receptionNumber);
	}

	/**
	 * @param receptionNumber the reception that is still open on MMI's side
	 * @param openedHere the successful reception log entry for that number, or null if this EMR has no record of it
	 * @return a message telling the user which reception is open and what to do about it
	 */
	public static String describe(String receptionNumber, MmiPatientReceptionLogEntry openedHere) {
		StringBuilder message = new StringBuilder("MMI already has an open reception for this patient: ")
				.append(receptionNumber.trim()).append(".");
		if (openedHere != null) {
			message.append(" It was opened from this EMR");
			if (openedHere.getDateCreated() != null) {
				message.append(" on ").append(new SimpleDateFormat("dd MMM yyyy").format(openedHere.getDateCreated()));
			}
			if (openedHere.getVisitId() != null) {
				message.append(" (visit ").append(openedHere.getVisitId()).append(")");
			}
			message.append(". Close that visit's MMI bill (voucher closure) before opening a new reception.");
		}
		else {
			message.append(" This EMR has no record of it, so it was opened at another facility or by a request")
					.append(" that did not get a reply. Ask RHIP or MMI support to close it before opening a new reception.");
		}
		return message.toString();
	}
}
