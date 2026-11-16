package org.openmrs.module.rwandaemr.labreports;

public class AggregateLabReportRow {

	private Integer categoryConceptId;

	private String category;

	private Integer testConceptId;

	private String test;

	private Integer positiveCount;

	private Integer negativeCount;

	private Integer totalResultedCount;

	public Integer getCategoryConceptId() {
		return categoryConceptId;
	}

	public void setCategoryConceptId(Integer categoryConceptId) {
		this.categoryConceptId = categoryConceptId;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Integer getTestConceptId() {
		return testConceptId;
	}

	public void setTestConceptId(Integer testConceptId) {
		this.testConceptId = testConceptId;
	}

	public String getTest() {
		return test;
	}

	public void setTest(String test) {
		this.test = test;
	}

	public Integer getPositiveCount() {
		return positiveCount;
	}

	public void setPositiveCount(Integer positiveCount) {
		this.positiveCount = positiveCount;
	}

	public Integer getNegativeCount() {
		return negativeCount;
	}

	public void setNegativeCount(Integer negativeCount) {
		this.negativeCount = negativeCount;
	}

	public Integer getTotalResultedCount() {
		return totalResultedCount;
	}

	public void setTotalResultedCount(Integer totalResultedCount) {
		this.totalResultedCount = totalResultedCount;
	}
}
