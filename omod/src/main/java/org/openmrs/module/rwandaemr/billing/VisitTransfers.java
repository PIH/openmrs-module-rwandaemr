package org.openmrs.module.rwandaemr.billing;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The transfers of the visit of a bill, from the Transfer app: the transfers that brought the patient here, whose HIE
 * transfer id was recorded on the visit, and the transfers made from this facility during the visit.
 */
public class VisitTransfers {

	/**
	 * A transfer that brought the patient to this facility, recorded on the visit by its HIE transfer id
	 */
	public static class Received {
		private final String hieTransferId;
		private final Date recordedAt;
		private String recordedBy;
		private boolean localCopy;
		private String sendingFacility;
		private String referringUnit;
		private Date decisionToTransferAt;
		private String transferType;
		private String formKind;
		private String reason;
		private String diagnosis;
		private String localUuid;
		private boolean counterReferralCompleted;
		private Date counterReferralCompletedAt;

		public Received(String hieTransferId, Date recordedAt) {
			this.hieTransferId = hieTransferId;
			this.recordedAt = recordedAt;
		}

		public String getHieTransferId() {
			return hieTransferId;
		}

		public Date getRecordedAt() {
			return recordedAt;
		}

		public String getRecordedBy() {
			return recordedBy;
		}

		public void setRecordedBy(String recordedBy) {
			this.recordedBy = recordedBy;
		}

		/**
		 * @return true if this facility has a copy of the transfer; else its details are in the HIE only
		 */
		public boolean isLocalCopy() {
			return localCopy;
		}

		public void setLocalCopy(boolean localCopy) {
			this.localCopy = localCopy;
		}

		public String getSendingFacility() {
			return sendingFacility;
		}

		public void setSendingFacility(String sendingFacility) {
			this.sendingFacility = sendingFacility;
		}

		public String getReferringUnit() {
			return referringUnit;
		}

		public void setReferringUnit(String referringUnit) {
			this.referringUnit = referringUnit;
		}

		public Date getDecisionToTransferAt() {
			return decisionToTransferAt;
		}

		public void setDecisionToTransferAt(Date decisionToTransferAt) {
			this.decisionToTransferAt = decisionToTransferAt;
		}

		public String getTransferType() {
			return transferType;
		}

		public void setTransferType(String transferType) {
			this.transferType = transferType;
		}

		public String getFormKind() {
			return formKind;
		}

		public void setFormKind(String formKind) {
			this.formKind = formKind;
		}

		public String getReason() {
			return reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

		public String getDiagnosis() {
			return diagnosis;
		}

		public void setDiagnosis(String diagnosis) {
			this.diagnosis = diagnosis;
		}

		/**
		 * @return the uuid of this facility's copy of the transfer, if any
		 */
		public String getLocalUuid() {
			return localUuid;
		}

		public void setLocalUuid(String localUuid) {
			this.localUuid = localUuid;
		}

		public boolean isCounterReferralCompleted() {
			return counterReferralCompleted;
		}

		public void setCounterReferralCompleted(boolean counterReferralCompleted) {
			this.counterReferralCompleted = counterReferralCompleted;
		}

		public Date getCounterReferralCompletedAt() {
			return counterReferralCompletedAt;
		}

		public void setCounterReferralCompletedAt(Date counterReferralCompletedAt) {
			this.counterReferralCompletedAt = counterReferralCompletedAt;
		}
	}

	/**
	 * A transfer made from this facility during the visit
	 */
	public static class Sent {
		private String uuid;
		private String hieTransferId;
		private Date decisionToTransferAt;
		private String createdBy;
		private String receivingFacility;
		private String receivingService;
		private String transferType;
		private String formKind;
		private String approvalStatus;
		private Date approvedAt;
		private boolean hieSent;

		/**
		 * @return the uuid of the transfer in the Transfer app
		 */
		public String getUuid() {
			return uuid;
		}

		public void setUuid(String uuid) {
			this.uuid = uuid;
		}

		/**
		 * @return the id of the transfer in the HIE, once it was sent there
		 */
		public String getHieTransferId() {
			return hieTransferId;
		}

		public void setHieTransferId(String hieTransferId) {
			this.hieTransferId = hieTransferId;
		}

		public Date getDecisionToTransferAt() {
			return decisionToTransferAt;
		}

		public void setDecisionToTransferAt(Date decisionToTransferAt) {
			this.decisionToTransferAt = decisionToTransferAt;
		}

		public String getCreatedBy() {
			return createdBy;
		}

		public void setCreatedBy(String createdBy) {
			this.createdBy = createdBy;
		}

		public String getReceivingFacility() {
			return receivingFacility;
		}

		public void setReceivingFacility(String receivingFacility) {
			this.receivingFacility = receivingFacility;
		}

		public String getReceivingService() {
			return receivingService;
		}

		public void setReceivingService(String receivingService) {
			this.receivingService = receivingService;
		}

		public String getTransferType() {
			return transferType;
		}

		public void setTransferType(String transferType) {
			this.transferType = transferType;
		}

		public String getFormKind() {
			return formKind;
		}

		public void setFormKind(String formKind) {
			this.formKind = formKind;
		}

		public String getApprovalStatus() {
			return approvalStatus;
		}

		public void setApprovalStatus(String approvalStatus) {
			this.approvalStatus = approvalStatus;
		}

		/**
		 * @return the approval status as words: "Approved", "Pending", or "" when no approval was needed
		 */
		public String getApprovalLabel() {
			if (StringUtils.isBlank(approvalStatus) || "NONE".equalsIgnoreCase(approvalStatus)) {
				return "";
			}
			return words(approvalStatus);
		}

		public Date getApprovedAt() {
			return approvedAt;
		}

		public void setApprovedAt(Date approvedAt) {
			this.approvedAt = approvedAt;
		}

		public boolean isHieSent() {
			return hieSent;
		}

		public void setHieSent(boolean hieSent) {
			this.hieSent = hieSent;
		}
	}

	private final Date start;
	private final Date end;
	private final List<Received> received = new ArrayList<Received>();
	private final List<Sent> sent = new ArrayList<Sent>();

	public VisitTransfers(Date start, Date end) {
		this.start = start;
		this.end = end;
	}

	/**
	 * @return the start of the visit, or of the admission day when the bill has no visit
	 */
	public Date getStart() {
		return start;
	}

	public Date getEnd() {
		return end;
	}

	public List<Received> getReceived() {
		return received;
	}

	public List<Sent> getSent() {
		return sent;
	}

	/**
	 * @return true if no transfer was recorded for the visit
	 */
	public boolean isEmpty() {
		return received.isEmpty() && sent.isEmpty();
	}

	/**
	 * @return the form kind as the Transfer app names it: General, Maternity or Neonatal
	 */
	public static String formKindLabel(String formKind) {
		return StringUtils.isBlank(formKind) ? "" : words(formKind);
	}

	static String words(String code) {
		String text = code.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
