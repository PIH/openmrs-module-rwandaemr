package org.openmrs.module.rwandaemr.radiology.queue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * One row of the radiology queue: a radiology study, which is either a single test or several tests of the same
 * modality sent to PACS together as one combined study
 */
public class RadiologyQueueRow {

	public enum PacsStatus {
		NOT_SENT("Not sent"),
		SENT("Sent"),
		IMAGES_RECEIVED("Images received"),
		REPORTED("Reported"),
		CANCELLED("Cancelled");

		private final String label;

		PacsStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	public enum PaymentStatus {
		PAID("Paid"),
		PARTIALLY_PAID("Partially paid"),
		NOT_PAID("Not paid"),
		NOT_BILLED("Not billed");

		private final String label;

		PaymentStatus(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	private String accessionNumber;
	private List<Integer> orderIds = new ArrayList<Integer>();
	private List<String> orderNumbers = new ArrayList<String>();
	private Integer patientId;
	private String patientIdentifier;
	private String patientName;
	private Integer age;
	private String sex;
	private String modality;
	private String typeOfStudy;
	private String clinicalHistory;
	private String requestingPhysician;
	private String requestingDepartment;
	private Date timeRequested;
	private PacsStatus pacsStatus;
	private PaymentStatus paymentStatus;
	private Date timeOfPayment;
	private boolean processed;

	public String getAccessionNumber() {
		return accessionNumber;
	}

	public void setAccessionNumber(String accessionNumber) {
		this.accessionNumber = accessionNumber;
	}

	public List<Integer> getOrderIds() {
		return orderIds;
	}

	public void setOrderIds(List<Integer> orderIds) {
		this.orderIds = orderIds;
	}

	public List<String> getOrderNumbers() {
		return orderNumbers;
	}

	public void setOrderNumbers(List<String> orderNumbers) {
		this.orderNumbers = orderNumbers;
	}

	public Integer getPatientId() {
		return patientId;
	}

	public void setPatientId(Integer patientId) {
		this.patientId = patientId;
	}

	public String getPatientIdentifier() {
		return patientIdentifier;
	}

	public void setPatientIdentifier(String patientIdentifier) {
		this.patientIdentifier = patientIdentifier;
	}

	public String getPatientName() {
		return patientName;
	}

	public void setPatientName(String patientName) {
		this.patientName = patientName;
	}

	public Integer getAge() {
		return age;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	public String getModality() {
		return modality;
	}

	public void setModality(String modality) {
		this.modality = modality;
	}

	public String getTypeOfStudy() {
		return typeOfStudy;
	}

	public void setTypeOfStudy(String typeOfStudy) {
		this.typeOfStudy = typeOfStudy;
	}

	public String getClinicalHistory() {
		return clinicalHistory;
	}

	public void setClinicalHistory(String clinicalHistory) {
		this.clinicalHistory = clinicalHistory;
	}

	public String getRequestingPhysician() {
		return requestingPhysician;
	}

	public void setRequestingPhysician(String requestingPhysician) {
		this.requestingPhysician = requestingPhysician;
	}

	public String getRequestingDepartment() {
		return requestingDepartment;
	}

	public void setRequestingDepartment(String requestingDepartment) {
		this.requestingDepartment = requestingDepartment;
	}

	public Date getTimeRequested() {
		return timeRequested;
	}

	public void setTimeRequested(Date timeRequested) {
		this.timeRequested = timeRequested;
	}

	public PacsStatus getPacsStatus() {
		return pacsStatus;
	}

	public void setPacsStatus(PacsStatus pacsStatus) {
		this.pacsStatus = pacsStatus;
	}

	public PaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(PaymentStatus paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	/**
	 * @return true if the exam has been done: PACS returned images or a report, or the order was marked completed
	 */
	public boolean isProcessed() {
		return processed;
	}

	public void setProcessed(boolean processed) {
		this.processed = processed;
	}

	public String getProcessedLabel() {
		return processed ? "Processed" : "Not processed";
	}

	public Date getTimeOfPayment() {
		return timeOfPayment;
	}

	public void setTimeOfPayment(Date timeOfPayment) {
		this.timeOfPayment = timeOfPayment;
	}
}
