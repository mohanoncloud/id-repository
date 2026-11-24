package io.mosip.idrepository.paas.passport.compare;

/** Result of comparing one printed field with the MRZ. */
public enum FieldStatus {

	/** The printed value agrees with the MRZ. */
	MATCH,

	/** The printed value was read reliably and disagrees with the MRZ. */
	MISMATCH,

	/** The printed value couldn't be read reliably (missing, low confidence or unknown format): can't tell. */
	UNCERTAIN,

	/**
	 * Not comparable by design: given names cut off by MRZ truncation, or a nationality word with no configured
	 * mapping (a retake can't fix missing config).
	 */
	NOT_COMPARED
}
