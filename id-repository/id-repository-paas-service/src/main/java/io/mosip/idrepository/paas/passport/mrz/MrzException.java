package io.mosip.idrepository.paas.passport.mrz;

import java.util.Optional;

import io.mosip.idrepository.paas.exception.EnrolmentException;

/**
 * The MRZ can't be accepted. Handled like any {@link EnrolmentException}, so the app gets the spec code
 * (ENR-PSP-003 or ENR-PSP-004). The message names only the reason and, for check digits, the field; it
 * never contains MRZ content.
 */
public class MrzException extends EnrolmentException {

	private static final long serialVersionUID = 1L;

	private final MrzError error;
	private final MrzField field;

	public MrzException(MrzError error) {
		this(error, null);
	}

	public MrzException(MrzError error, MrzField field) {
		super(error.getErrorCode(), field == null ? error.name() : error.name() + " (" + field.name() + ")");
		this.error = error;
		this.field = field;
	}

	public MrzError getError() {
		return error;
	}

	/** The field whose check digit failed, when the error is {@link MrzError#CHECK_DIGIT_FAILED}. */
	public Optional<MrzField> getField() {
		return Optional.ofNullable(field);
	}
}
