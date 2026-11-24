package io.mosip.idrepository.paas.spi;

import java.util.Map;

import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.upload.SanitisedImage;

/**
 * Server-side OCR of the passport data page: the MRZ text and the printed (visual zone) fields.
 * The library is not chosen yet (MP-397: PaddleOCR vs a commercial MRZ SDK), so the signature is
 * provisional.
 */
public interface PassportImageReader {

	/**
	 * @param frontImage the data page image, already through the file checks: metadata removed, with the EXIF
	 *                   orientation kept so the image can be rotated before OCR
	 * @throws EnrolmentException ENR-PSP-003 if no MRZ can be found
	 */
	PassportImageReading read(SanitisedImage frontImage);

	/**
	 * @param mrzText       the two MRZ lines as read, separated by a line break
	 * @param printedFields printed fields keyed by the API response field names ({@code surname},
	 *                      {@code givenNames}, {@code passportNumber}, {@code nationality}, {@code dateOfBirth},
	 *                      {@code sex}, {@code dateOfExpiry}), with the text read and its confidence
	 */
	record PassportImageReading(String mrzText, Map<String, OcrField> printedFields) {

		@Override
		public String toString() {
			return "PassportImageReading[redacted]";
		}
	}

	/** A printed field as read by OCR, with a confidence from 0.0 to 1.0. */
	record OcrField(String text, double confidence) {

		@Override
		public String toString() {
			return "OcrField[redacted, confidence=" + confidence + "]";
		}
	}
}
