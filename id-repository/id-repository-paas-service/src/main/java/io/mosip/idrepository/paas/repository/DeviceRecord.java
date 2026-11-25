package io.mosip.idrepository.paas.repository;

import java.time.LocalDateTime;

/**
 * One row of {@code idpaas.device}.
 *
 * @param publicKeyJwk       the JWK as JSON text. The column is jsonb, so what is read back has its keys reordered
 *                           and spaces added: parse it, and use {@code jkt} (not this text) for the key's identity,
 *                           never compare two of these texts
 * @param appAttestKeyId     iOS only, else null
 * @param manufacturer       Android only, else null
 * @param policyVersion      policy in force when checked, or null if the platform had none
 * @param attestationSummary JSON text of the verifier's summary
 * @param statusCode         ACTIVE or REVOKED
 * @param createdAt          UTC
 */
public record DeviceRecord(String deviceId, String jkt, String publicKeyJwk, String appAttestKeyId, String platform,
		String osVersion, String manufacturer, String model, String appVersion, String policyVersion,
		String attestationSummary, String statusCode, LocalDateTime createdAt) {

	public static final String ACTIVE = "ACTIVE";
	public static final String REVOKED = "REVOKED";

	public boolean isRevoked() {
		return REVOKED.equals(statusCode);
	}
}
