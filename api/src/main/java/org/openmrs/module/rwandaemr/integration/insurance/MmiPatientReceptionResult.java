package org.openmrs.module.rwandaemr.integration.insurance;

public class MmiPatientReceptionResult {

	private final boolean receptionRequired;
	private final boolean successful;
	private final String receptionNumber;
	private final String message;

	private MmiPatientReceptionResult(boolean receptionRequired, boolean successful, String receptionNumber, String message) {
		this.receptionRequired = receptionRequired;
		this.successful = successful;
		this.receptionNumber = receptionNumber;
		this.message = message;
	}

	public static MmiPatientReceptionResult notRequired() {
		return new MmiPatientReceptionResult(false, true, null, null);
	}

	public static MmiPatientReceptionResult success(String receptionNumber) {
		return new MmiPatientReceptionResult(true, true, receptionNumber, null);
	}

	public static MmiPatientReceptionResult failed(String message) {
		return new MmiPatientReceptionResult(true, false, null, message);
	}

	public boolean isReceptionRequired() {
		return receptionRequired;
	}

	public boolean isSuccessful() {
		return successful;
	}

	public String getReceptionNumber() {
		return receptionNumber;
	}

	public String getMessage() {
		return message;
	}
}
