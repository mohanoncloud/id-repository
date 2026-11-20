package io.mosip.idrepository.paas.spi;

import java.util.Map;

import io.mosip.idrepository.paas.exception.EnrolmentException;

/**
 * Server-side OCR of the passport data page: the MRZ text and the printed (visual zone) fields.
 * The library is not chosen yet (proposal: PaddleOCR vs a commercial MRZ SDK), so the signature is
 * provisional and may change with MP-261 / MP-257.
 */
public interface PassportImageReader {

	/**
	 * @param frontImage the data page image, already through the file checks
	 * @throws EnrolmentException ENR-PSP-003 if no MRZ can be found
	 */
	PassportImageReading read(byte[] frontImage);

	/**
	 * @param mrzText       the two MRZ lines as read, separated by a line break
	 * @param printedFields printed field name (e.g. "surname", "dateOfBirth") to the text read and its confidence
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
