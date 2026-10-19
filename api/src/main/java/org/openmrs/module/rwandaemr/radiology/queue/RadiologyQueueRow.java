package org.openmrs.module.rwandaemr.radiology.queue;

import org.openmrs.module.rwandaemr.billing.PaymentStatus;

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
		// PACS reported the study as done but has not sent a link to view its images
		STUDY_DONE_NO_LINK("Study done \u2013 no image link yet"),
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

		/**
		 * @return true if PACS has reported the study as done
		 */
		public boolean isStudyDone() {
			return this == STUDY_DONE_NO_LINK || this == IMAGES_RECEIVED || this == REPORTED;
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
	private boolean stat;
	private String reason;
	private String imageUrl;
	private Date imagesReceivedAt;
	private String reportStatus;
	private Date reportedAt;
	private Integer reportOrderId;
	private List<Test> tests = new ArrayList<Test>();
	private Creatinine creatinine;

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

	/**
	 * @return true when the patient can have the exam now: the study is paid (or there is nothing to pay), it has been
	 * sent to PACS, and it has not been done yet
	 */
	public boolean isReadyForExam() {
		return paymentStatus == PaymentStatus.PAID && pacsStatus == PacsStatus.SENT && !processed;
	}

	public boolean isStat() {
		return stat;
	}

	public void setStat(boolean stat) {
		this.stat = stat;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public Date getImagesReceivedAt() {
		return imagesReceivedAt;
	}

	public void setImagesReceivedAt(Date imagesReceivedAt) {
		this.imagesReceivedAt = imagesReceivedAt;
	}

	public String getReportStatus() {
		return reportStatus;
	}

	public void setReportStatus(String reportStatus) {
		this.reportStatus = reportStatus;
	}

	public Date getReportedAt() {
		return reportedAt;
	}

	public void setReportedAt(Date reportedAt) {
		this.reportedAt = reportedAt;
	}

	/**
	 * @return the order whose page shows the report, if a report was received
	 */
	public Integer getReportOrderId() {
		return reportOrderId;
	}

	public void setReportOrderId(Integer reportOrderId) {
		this.reportOrderId = reportOrderId;
	}

	public List<Test> getTests() {
		return tests;
	}

	/**
	 * @return the patient's latest creatinine result, for CT studies, or null if there is none
	 */
	public Creatinine getCreatinine() {
		return creatinine;
	}

	public void setCreatinine(Creatinine creatinine) {
		this.creatinine = creatinine;
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

	/**
	 * One test of the study
	 */
	public static class Test {

		private final Integer orderId;
		private final String orderNumber;
		private final String name;
		private final boolean stat;

		public Test(Integer orderId, String orderNumber, String name, boolean stat) {
			this.orderId = orderId;
			this.orderNumber = orderNumber;
			this.name = name;
			this.stat = stat;
		}

		public Integer getOrderId() {
			return orderId;
		}

		public String getOrderNumber() {
			return orderNumber;
		}

		public String getName() {
			return name;
		}

		public boolean isStat() {
			return stat;
		}

		private boolean reported;
		private String reportStatus;

		/**
		 * @return true if this test has a report.  Tests reported in OpenMRS each have their own report.
		 */
		public boolean isReported() {
			return reported;
		}

		public void setReported(boolean reported) {
			this.reported = reported;
		}

		public String getReportStatus() {
			return reportStatus;
		}

		public void setReportStatus(String reportStatus) {
			this.reportStatus = reportStatus;
		}
	}

	/**
	 * The patient's latest creatinine result, shown for CT studies, as contrast may be given
	 */
	public static class Creatinine {

		private final String value;
		private final String units;
		private final Date date;
		private final boolean high;

		public Creatinine(String value, String units, Date date, boolean high) {
			this.value = value;
			this.units = units;
			this.date = date;
			this.high = high;
		}

		public String getValue() {
			return value;
		}

		public String getUnits() {
			return units;
		}

		public Date getDate() {
			return date;
		}

		/**
		 * @return true if the result is above the normal range of the creatinine concept
		 */
		public boolean isHigh() {
			return high;
		}

		/**
		 * @return the value with its units, for example "1.2 mg/dL"
		 */
		public String getDisplay() {
			return units == null ? value : value + " " + units;
		}
	}
}
