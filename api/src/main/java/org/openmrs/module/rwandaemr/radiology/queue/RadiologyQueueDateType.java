package org.openmrs.module.rwandaemr.radiology.queue;

import org.apache.commons.lang.StringUtils;

import java.util.Date;

/**
 * Which time of a study the radiology queue's date range applies to
 */
public enum RadiologyQueueDateType {

	REQUESTED("Time requested"),
	IMAGES_RECEIVED("Images received"),
	REPORTED("Reported");

	private final String label;

	RadiologyQueueDateType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	/**
	 * @return the time of the row this date type refers to, or null if it has not happened yet
	 */
	public Date getDate(RadiologyQueueRow row) {
		switch (this) {
			case IMAGES_RECEIVED:
				return row.getImagesReceivedAt();
			case REPORTED:
				return row.getReportedAt();
			default:
				return row.getTimeRequested();
		}
	}

	/**
	 * @return the date type with the given name, or {@link #REQUESTED} if it is blank or not known
	 */
	public static RadiologyQueueDateType parse(String name) {
		if (StringUtils.isNotBlank(name)) {
			for (RadiologyQueueDateType type : values()) {
				if (type.name().equalsIgnoreCase(name.trim())) {
					return type;
				}
			}
		}
		return REQUESTED;
	}
}
