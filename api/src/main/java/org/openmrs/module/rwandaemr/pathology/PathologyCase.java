package org.openmrs.module.rwandaemr.pathology;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * A pathology request, from the time the lab receives its specimen.  There is one case for each request encounter, and
 * its accession number is recorded on each pathology order of the encounter.
 */
@Getter
@Setter
public class PathologyCase {

	private Integer pathologyCaseId;

	private Integer encounterId;

	/**
	 * Null for a specimen rejected before it was received, so that it uses no number
	 */
	private String accessionNumber;

	private PathologyStatus status;

	private String rejectionReason;

	private Integer creator;

	private Date dateCreated;

	private Integer changedBy;

	private Date dateChanged;
}
