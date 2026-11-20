package io.mosip.idrepository.paas.spi;

import io.mosip.idrepository.paas.exception.EnrolmentException;

/** Server-side repeat of the app's image quality check: focus, glare, whole page in frame (MP-256). */
public interface ImageQualityChecker {

	/** @throws EnrolmentException ENR-PSP-002 if the image is blurry, dark or cut off */
	void check(byte[] image);
}
