package org.openmrs.module.rwandaemr.labreports;

import java.util.Date;

public class LabTurnaroundTimeRow {

	private Integer orderId;

	private String orderUuid;

	private String test;

	private String location;

	private String urgency;

	private String fulfillerStatus;

	private Date orderedAt;

	private Date specimenReceivedAt;

	private Date resultedAt;

	private Double orderToReceivedHours;

	private Double receivedToResultHours;

	private Double orderToResultHours;

	public Integer getOrderId() {
		return orderId;
	}

	public void setOrderId(Integer orderId) {
		this.orderId = orderId;
	}

	public String getOrderUuid() {
		return orderUuid;
	}

	public void setOrderUuid(String orderUuid) {
		this.orderUuid = orderUuid;
	}

	public String getTest() {
		return test;
	}

	public void setTest(String test) {
		this.test = test;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getUrgency() {
		return urgency;
	}

	public void setUrgency(String urgency) {
		this.urgency = urgency;
	}

	public String getFulfillerStatus() {
		return fulfillerStatus;
	}

	public void setFulfillerStatus(String fulfillerStatus) {
		this.fulfillerStatus = fulfillerStatus;
	}

	public Date getOrderedAt() {
		return orderedAt;
	}

	public void setOrderedAt(Date orderedAt) {
		this.orderedAt = orderedAt;
	}

	public Date getSpecimenReceivedAt() {
		return specimenReceivedAt;
	}

	public void setSpecimenReceivedAt(Date specimenReceivedAt) {
		this.specimenReceivedAt = specimenReceivedAt;
	}

	public Date getResultedAt() {
		return resultedAt;
	}

	public void setResultedAt(Date resultedAt) {
		this.resultedAt = resultedAt;
	}

	public Double getOrderToReceivedHours() {
		return orderToReceivedHours;
	}

	public void setOrderToReceivedHours(Double orderToReceivedHours) {
		this.orderToReceivedHours = orderToReceivedHours;
	}

	public Double getReceivedToResultHours() {
		return receivedToResultHours;
	}

	public void setReceivedToResultHours(Double receivedToResultHours) {
		this.receivedToResultHours = receivedToResultHours;
	}

	public Double getOrderToResultHours() {
		return orderToResultHours;
	}

	public void setOrderToResultHours(Double orderToResultHours) {
		this.orderToResultHours = orderToResultHours;
	}
}
