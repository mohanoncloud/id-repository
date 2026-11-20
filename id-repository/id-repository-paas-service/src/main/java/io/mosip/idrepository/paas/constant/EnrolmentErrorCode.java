package io.mosip.idrepository.paas.constant;

import org.springframework.http.HttpStatus;

/**
 * Error codes and user-facing messages from the My Pass Enrolment API Specification (sections 3 and 6).
 * {@code errors[].message} is shown by the app word for word, so the text here must match the spec.
 *
 * <p>Business failures return HTTP 200 with {@code errors[]} filled; transport problems use HTTP codes.
 */
public enum EnrolmentErrorCode {

	// ---- Passport scan (section 3)
	PSP_001("ENR-PSP-001", HttpStatus.OK,
			"Please upload a clear JPG or PNG photo of both sides of your passport, under 5 MB each."),
	PSP_002("ENR-PSP-002", HttpStatus.OK,
			"We couldn’t read this image. Please retake or re-upload the front and back of your passport in good light, with the whole page in view."),
	PSP_003("ENR-PSP-003", HttpStatus.OK,
			"We couldn’t read the code at the bottom of your passport photo page. Please retake the photo with the whole page in view."),
	PSP_004("ENR-PSP-004", HttpStatus.OK, "This document isn’t a passport. Please use your passport."),
	PSP_005("ENR-PSP-005", HttpStatus.OK, "Passports from this country can’t be used for My Pass enrolment."),
	PSP_006("ENR-PSP-006", HttpStatus.OK,
			"The passport number doesn’t look right. Please retake the photo, or visit an assisted desk."),
	PSP_007("ENR-PSP-007", HttpStatus.OK, "The details on your passport don’t match. Please visit an assisted desk."),
	PSP_008("ENR-PSP-008", HttpStatus.OK, "Your passport has expired. Please renew it before you enrol."),
	PSP_009("ENR-PSP-009", HttpStatus.OK,
			"This passport is already linked to a My Pass identity. If you think this is a mistake, visit an assisted desk."),
	PSP_010("ENR-PSP-010", HttpStatus.OK, "Add your address to continue."),
	PSP_011("ENR-PSP-011", HttpStatus.SERVICE_UNAVAILABLE, "We couldn’t save your details. Please try again."),

	// ---- Common errors (section 6)
	SES_001("ENR-SES-001", HttpStatus.UNAUTHORIZED,
			"Your session has ended for your security. Verify your email to continue where you left off, or start again."),
	SES_002("ENR-SES-002", HttpStatus.OK, "Please complete the previous step first."),
	SES_003("ENR-SES-003", HttpStatus.OK,
			"We couldn’t verify this device. Please start again on the device you began with."),
	GEN_001("ENR-GEN-001", HttpStatus.BAD_REQUEST, "Something went wrong. Please try again."),
	GEN_002("ENR-GEN-002", HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment and try again."),
	GEN_003("ENR-GEN-003", HttpStatus.INTERNAL_SERVER_ERROR,
			"Something went wrong on our side. Please try again in a few minutes.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

	EnrolmentErrorCode(String code, HttpStatus httpStatus, String message) {
		this.code = code;
		this.httpStatus = httpStatus;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	/** Default HTTP status for this code; {@code EnrolmentException} can override it (e.g. 413, 503). */
	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

	public String getMessage() {
		return message;
	}
}
