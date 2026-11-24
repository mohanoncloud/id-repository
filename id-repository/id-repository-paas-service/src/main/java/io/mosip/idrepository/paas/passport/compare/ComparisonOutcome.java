package io.mosip.idrepository.paas.passport.compare;

import java.util.Optional;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;

/** Overall result of the printed vs MRZ comparison, and the error returned to the app. */
public enum ComparisonOutcome {

	/** All compared fields agree: continue. */
	MATCH(null),

	/**
	 * No reliable mismatch, but some fields couldn't be read: ask for a retake (ENR-PSP-002), not the assisted
	 * desk. MP-257 asks to confirm this rule.
	 */
	RETAKE(EnrolmentErrorCode.PSP_002),

	/** At least one reliably read field disagrees: the passport may be altered. ENR-PSP-007, assisted desk. */
	ASSISTED_DESK(EnrolmentErrorCode.PSP_007);

	private final EnrolmentErrorCode errorCode;

	ComparisonOutcome(EnrolmentErrorCode errorCode) {
		this.errorCode = errorCode;
	}

	/** The error for the app; empty for {@link #MATCH}. */
	public Optional<EnrolmentErrorCode> errorCode() {
		return Optional.ofNullable(errorCode);
	}
}
