package org.openmrs.module.rwandaemr.integration.insurance;

public final class MmiPatientReceptionContextHolder {

	private static final ThreadLocal<String> SUBMITTED_OTP_CODE = new ThreadLocal<>();

	private MmiPatientReceptionContextHolder() {
	}

	public static void setSubmittedOtpCode(String otpCode) {
		SUBMITTED_OTP_CODE.set(otpCode);
	}

	public static String getSubmittedOtpCode() {
		return SUBMITTED_OTP_CODE.get();
	}

	public static void clear() {
		SUBMITTED_OTP_CODE.remove();
	}
}
