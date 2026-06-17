package org.openmrs.module.rwandaemr.icd11;

public class Icd11ApiException extends RuntimeException {

	public Icd11ApiException(String message) {
		super(message);
	}

	public Icd11ApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
