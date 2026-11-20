package io.mosip.idrepository.paas.exception;

import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;

/**
 * A request failed with one of the spec's error codes. The exception message is for logs and never contains
 * personal data; the user-facing text comes from {@link EnrolmentErrorCode#getMessage()}.
 */
public class EnrolmentException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final EnrolmentErrorCode errorCode;
	private final HttpStatus httpStatus;

	public EnrolmentException(EnrolmentErrorCode errorCode) {
		this(errorCode, errorCode.getHttpStatus(), errorCode.getCode());
	}

	public EnrolmentException(EnrolmentErrorCode errorCode, String logMessage) {
		this(errorCode, errorCode.getHttpStatus(), logMessage);
	}

	/** For codes with more than one HTTP status in the spec, e.g. ENR-PSP-001 (200 / 413). */
	public EnrolmentException(EnrolmentErrorCode errorCode, HttpStatus httpStatus, String logMessage) {
		super(logMessage);
		this.errorCode = errorCode;
		this.httpStatus = httpStatus;
	}

	public EnrolmentErrorCode getErrorCode() {
		return errorCode;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
