package io.mosip.idrepository.paas.service;

import io.mosip.idrepository.paas.dto.device.DeviceRegisterRequest;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/** Registers the device key the app created on first launch (MP-247, device binding MP-152). */
public interface DeviceService {

	/**
	 * Order: request checks, attestation (MP-246), supported-device policy, then the insert.
	 *
	 * @throws EnrolmentException ENR-GEN-001 (400) for a malformed request; ENR-DEV-004 attestation failed;
	 *                            ENR-DEV-001 device not supported; ENR-DEV-002 key already registered, revoked or not;
	 *                            ENR-GEN-003 (503) if a dependency is unavailable
	 */
	DeviceRegisterResponse register(DeviceRegisterRequest request);
}
