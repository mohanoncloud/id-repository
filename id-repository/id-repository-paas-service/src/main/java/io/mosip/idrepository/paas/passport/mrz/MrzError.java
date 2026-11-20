package io.mosip.idrepository.paas.passport.mrz;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;

/** Why an MRZ was rejected, and the spec error code returned to the app (API spec section 3). */
public enum MrzError {

	/** No usable TD3 MRZ: wrong number of lines, wrong length or invalid characters. */
	MRZ_UNREADABLE(EnrolmentErrorCode.PSP_003),

	/** A check digit does not match (ICAO 9303, 7-3-1): the read is wrong or the document is not genuine. */
	CHECK_DIGIT_FAILED(EnrolmentErrorCode.PSP_003),

	/** Date of birth or expiry date is not a real calendar date. */
	INVALID_DATE(EnrolmentErrorCode.PSP_003),

	/** Document type is not {@code P} (passport), e.g. an ID card or visa. */
	UNSUPPORTED_DOCUMENT_TYPE(EnrolmentErrorCode.PSP_004);

	private final EnrolmentErrorCode errorCode;

	MrzError(EnrolmentErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	public EnrolmentErrorCode getErrorCode() {
		return errorCode;
	}
}
