package org.openmrs.module.rwandaemr.labreports;

public class LabTurnaroundTimeSummary {

	private Integer totalOrders;

	private Integer completedOrders;

	private Integer delayedOrders;

	private Double averageHours;

	private Double medianHours;

	private Double percentile90Hours;

	private Double percentWithinTarget;

	public Integer getTotalOrders() {
		return totalOrders;
	}

	public void setTotalOrders(Integer totalOrders) {
		this.totalOrders = totalOrders;
	}

	public Integer getCompletedOrders() {
		return completedOrders;
	}

	public void setCompletedOrders(Integer completedOrders) {
		this.completedOrders = completedOrders;
	}

	public Integer getDelayedOrders() {
		return delayedOrders;
	}

	public void setDelayedOrders(Integer delayedOrders) {
		this.delayedOrders = delayedOrders;
	}

	public Double getAverageHours() {
		return averageHours;
	}

	public void setAverageHours(Double averageHours) {
		this.averageHours = averageHours;
	}

	public Double getMedianHours() {
		return medianHours;
	}

	public void setMedianHours(Double medianHours) {
		this.medianHours = medianHours;
	}

	public Double getPercentile90Hours() {
		return percentile90Hours;
	}

	public void setPercentile90Hours(Double percentile90Hours) {
		this.percentile90Hours = percentile90Hours;
	}

	public Double getPercentWithinTarget() {
		return percentWithinTarget;
	}

	public void setPercentWithinTarget(Double percentWithinTarget) {
		this.percentWithinTarget = percentWithinTarget;
	}
}
