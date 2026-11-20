package io.mosip.idrepository.paas.spi.stub;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.spi.PassportHashService;

/** Until the keymanager HMAC key exists: every hash fails with ENR-GEN-003 (503). Never a local fallback key. */
@Component
public class StubPassportHashService implements PassportHashService {

	private static final String WHAT = "Passport hash (keymanager HMAC key)";

	public StubPassportHashService() {
		NotConfigured.warn(getClass(), WHAT);
	}

	@Override
	public String hash(String issuingState, String passportNumber) {
		throw NotConfigured.fail(WHAT);
	}
}
