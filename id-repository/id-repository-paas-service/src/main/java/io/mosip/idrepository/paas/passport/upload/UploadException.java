package io.mosip.idrepository.paas.passport.upload;

import java.util.Optional;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/**
 * An uploaded image failed a file check: ENR-PSP-001. Handled like any {@link EnrolmentException}. The message
 * names only the reason and the part ({@code frontImage} / {@code backImage}), never file content.
 */
public class UploadException extends EnrolmentException {

	private static final long serialVersionUID = 1L;

	private final UploadError error;
	private final String part;

	public UploadException(UploadError error) {
		this(error, null);
	}

	private UploadException(UploadError error, String part) {
		super(EnrolmentErrorCode.PSP_001, error.getHttpStatus(), part == null ? error.name() : error.name() + " (" + part + ")");
		this.error = error;
		this.part = part;
	}

	/** The same failure, naming the request part it came from. */
	UploadException forPart(String part) {
		return new UploadException(error, part);
	}

	public UploadError getError() {
		return error;
	}

	public Optional<String> getPart() {
		return Optional.ofNullable(part);
	}
}
