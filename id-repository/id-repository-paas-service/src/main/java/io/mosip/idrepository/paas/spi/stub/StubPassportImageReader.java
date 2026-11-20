package io.mosip.idrepository.paas.spi.stub;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.spi.PassportImageReader;

/** Until the OCR library is approved: every read fails with ENR-GEN-003 (503). */
@Component
public class StubPassportImageReader implements PassportImageReader {

	private static final String WHAT = "Passport OCR";

	public StubPassportImageReader() {
		NotConfigured.warn(getClass(), WHAT);
	}

	@Override
	public PassportImageReading read(byte[] frontImage) {
		throw NotConfigured.fail(WHAT);
	}
}
