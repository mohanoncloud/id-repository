package io.mosip.idrepository.paas.passport.upload;

/**
 * An uploaded image that passed the file checks, with metadata removed. Pixels are unchanged (no re-encoding),
 * so OCR quality isn't affected.
 *
 * @param data            image bytes without EXIF (incl. GPS), XMP, comments or text chunks
 * @param format          JPEG or PNG, from the file's contents
 * @param exifOrientation EXIF orientation (1-8) read before the metadata was removed, so the OCR step can rotate
 *                        the image; 1 if there was none
 */
public record SanitisedImage(byte[] data, Format format, int width, int height, int exifOrientation) {

	public enum Format {
		JPEG,
		PNG
	}

	/** Redacted: the image is personal data. */
	@Override
	public String toString() {
		return "SanitisedImage[" + format + ", " + width + "x" + height + ", orientation=" + exifOrientation + ", "
				+ data.length + " bytes]";
	}
}
