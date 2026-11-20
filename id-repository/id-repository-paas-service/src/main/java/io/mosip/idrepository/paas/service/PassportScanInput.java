package io.mosip.idrepository.paas.service;

import io.mosip.idrepository.paas.dto.passport.PassportScanRequest;

/**
 * Everything the passport scan received. Images are held in memory only; they are not stored (decision on
 * MP-90). {@code toString()} is redacted.
 */
public record PassportScanInput(byte[] frontImage, byte[] backImage, PassportScanRequest request) {

	@Override
	public String toString() {
		return "PassportScanInput[front=" + frontImage.length + " bytes, back=" + backImage.length + " bytes]";
	}
}
