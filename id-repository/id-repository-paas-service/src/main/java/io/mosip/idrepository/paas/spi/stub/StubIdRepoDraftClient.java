package io.mosip.idrepository.paas.spi.stub;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.spi.IdRepoDraftClient;

/** Until the ID schema update lands: every draft call fails with ENR-GEN-003 (503). */
@Component
public class StubIdRepoDraftClient implements IdRepoDraftClient {

	private static final String WHAT = "ID Repository draft client";

	public StubIdRepoDraftClient() {
		NotConfigured.warn(getClass(), WHAT);
	}

	@Override
	public void createDraft(String applicationId) {
		throw NotConfigured.fail(WHAT);
	}

	@Override
	public void updateDraft(String applicationId, Map<String, Object> identity) {
		throw NotConfigured.fail(WHAT);
	}

	@Override
	public void discardDraft(String applicationId) {
		throw NotConfigured.fail(WHAT);
	}
}
