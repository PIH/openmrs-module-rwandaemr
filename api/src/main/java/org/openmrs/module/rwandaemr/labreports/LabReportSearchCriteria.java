package org.openmrs.module.rwandaemr.labreports;

import java.util.Date;

public class LabReportSearchCriteria {

	private Date startDate;

	private Date endDate;

	private Integer locationId;

	private Integer categoryConceptId;

	private Integer testConceptId;

	private String resultType;

	private Double targetHours;

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public Integer getLocationId() {
		return locationId;
	}

	public void setLocationId(Integer locationId) {
		this.locationId = locationId;
	}

	public Integer getCategoryConceptId() {
		return categoryConceptId;
	}

	public void setCategoryConceptId(Integer categoryConceptId) {
		this.categoryConceptId = categoryConceptId;
	}

	public Integer getTestConceptId() {
		return testConceptId;
	}

	public void setTestConceptId(Integer testConceptId) {
		this.testConceptId = testConceptId;
	}

	public String getResultType() {
		return resultType;
	}

	public void setResultType(String resultType) {
		this.resultType = resultType;
	}

	public Double getTargetHours() {
		return targetHours;
	}

	public void setTargetHours(Double targetHours) {
		this.targetHours = targetHours;
	}
}
