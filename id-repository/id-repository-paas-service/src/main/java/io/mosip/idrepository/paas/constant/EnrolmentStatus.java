package io.mosip.idrepository.paas.constant;

/** Application status values (API spec section 5.2). */
public enum EnrolmentStatus {
	CONSENTED,
	EMAIL_VERIFIED,
	PASSPORT_VERIFIED,
	FACE_VERIFIED,
	SUBMITTED,
	PUBLISH_PENDING,
	ISSUED,
	UNDER_REVIEW,
	REJECTED,
	ASSISTED_DESK,
	EXPIRED
}
