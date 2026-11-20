package io.mosip.idrepository.paas.spi.stub;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.spi.ImageQualityChecker;

/** Until the quality check is decided (likely from the OCR/capture SDK): every check fails with ENR-GEN-003 (503). */
@Component
public class StubImageQualityChecker implements ImageQualityChecker {

	private static final String WHAT = "Image quality check";

	public StubImageQualityChecker() {
		NotConfigured.warn(getClass(), WHAT);
	}

	@Override
	public void check(byte[] image) {
		throw NotConfigured.fail(WHAT);
	}
}
