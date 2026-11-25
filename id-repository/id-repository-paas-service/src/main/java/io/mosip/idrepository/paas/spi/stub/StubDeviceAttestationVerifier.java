package io.mosip.idrepository.paas.spi.stub;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.device.AttestationEvidence;
import io.mosip.idrepository.paas.dto.device.PublicKeyJwk;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier;

/** Until MP-246 is built: every attestation fails with ENR-GEN-003 (503). Never passes a device unverified. */
@Component
public class StubDeviceAttestationVerifier implements DeviceAttestationVerifier {

	private static final String WHAT = "Device attestation verification (MP-246)";

	public StubDeviceAttestationVerifier() {
		NotConfigured.warn(getClass(), WHAT);
	}

	@Override
	public AttestationResult verify(Platform platform, PublicKeyJwk publicKey, String jkt,
			AttestationEvidence evidence) {
		throw NotConfigured.fail(WHAT);
	}
}
