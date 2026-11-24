package io.mosip.idrepository.paas.passport.upload;

import org.springframework.http.HttpStatus;

/**
 * Why an uploaded passport image was rejected. The spec (section 3) uses one code and message for every file
 * problem, ENR-PSP-001; the reason is kept for logs only. HTTP 413 for an over-size file, 200 with
 * {@code errors[]} otherwise.
 */
public enum UploadError {

	EMPTY_FILE(HttpStatus.OK),

	/** Over the size limit (5 MB). */
	FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),

	/** Not JPEG or PNG, judged from the file's contents (e.g. a PDF renamed .jpg). */
	UNSUPPORTED_FILE_TYPE(HttpStatus.OK),

	/** Pixel count over the limit: possible decompression bomb. Rejected from the header, before decoding. */
	IMAGE_TOO_LARGE(HttpStatus.OK),

	/** Long side under 1200 px. */
	RESOLUTION_TOO_LOW(HttpStatus.OK),

	/** The file can't be decoded as an image. */
	MALFORMED_IMAGE(HttpStatus.OK);

	private final HttpStatus httpStatus;

	UploadError(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
