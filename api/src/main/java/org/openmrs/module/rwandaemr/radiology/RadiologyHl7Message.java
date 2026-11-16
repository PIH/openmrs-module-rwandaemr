package org.openmrs.module.rwandaemr.radiology;

import java.util.Date;

/**
 * An HL7 message exchanged with PACS: an order sent to it, or a result received from it
 */
public class RadiologyHl7Message {

	public static final String DIRECTION_IN = "IN";
	public static final String DIRECTION_OUT = "OUT";

	// Results received from PACS
	public static final String STATUS_PROCESSED = "PROCESSED";
	public static final String STATUS_FAILED = "FAILED";

	// Orders sent to PACS
	public static final String STATUS_ACCEPTED = "ACCEPTED";
	public static final String STATUS_REJECTED = "REJECTED";
	public static final String STATUS_NO_RESPONSE = "NO_RESPONSE";

	private Integer id;
	private String direction;
	private String messageType;
	private String messageControlId;
	private String orderControl;
	private String resultStatus;
	private String accessionNumber;
	private String orderNumbers;
	private Boolean hasImageLink;
	private String status;
	private String errorMessage;
	private String message;
	private String ackMessage;
	private Integer creator;
	private Date dateCreated;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getDirection() {
		return direction;
	}

	public void setDirection(String direction) {
		this.direction = direction;
	}

	public String getMessageType() {
		return messageType;
	}

	public void setMessageType(String messageType) {
		this.messageType = messageType;
	}

	public String getMessageControlId() {
		return messageControlId;
	}

	public void setMessageControlId(String messageControlId) {
		this.messageControlId = messageControlId;
	}

	public String getOrderControl() {
		return orderControl;
	}

	public void setOrderControl(String orderControl) {
		this.orderControl = orderControl;
	}

	public String getResultStatus() {
		return resultStatus;
	}

	public void setResultStatus(String resultStatus) {
		this.resultStatus = resultStatus;
	}

	public String getAccessionNumber() {
		return accessionNumber;
	}

	public void setAccessionNumber(String accessionNumber) {
		this.accessionNumber = accessionNumber;
	}

	/**
	 * @return the order numbers of the tests the message is about, separated by commas
	 */
	public String getOrderNumbers() {
		return orderNumbers;
	}

	public void setOrderNumbers(String orderNumbers) {
		this.orderNumbers = orderNumbers;
	}

	public Boolean getHasImageLink() {
		return hasImageLink;
	}

	public void setHasImageLink(Boolean hasImageLink) {
		this.hasImageLink = hasImageLink;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getAckMessage() {
		return ackMessage;
	}

	public void setAckMessage(String ackMessage) {
		this.ackMessage = ackMessage;
	}

	public Integer getCreator() {
		return creator;
	}

	public void setCreator(Integer creator) {
		this.creator = creator;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}
}
