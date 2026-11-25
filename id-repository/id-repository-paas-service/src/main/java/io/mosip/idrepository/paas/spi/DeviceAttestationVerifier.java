package io.mosip.idrepository.paas.spi;

import java.util.Map;

import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.device.AttestationEvidence;
import io.mosip.idrepository.paas.dto.device.PublicKeyJwk;

/**
 * Checks the device attestation at registration (MP-246): the request comes from the genuine My Pass app, on a
 * real unmodified device, and the key is held in secure hardware. MP-247 calls it first, before the
 * supported-device policy and before anything is stored.
 *
 * <p>Any failed check is thrown as {@code EnrolmentException} with ENR-DEV-004 (HTTP 200); an attestation
 * service that can't be reached is ENR-GEN-003 (HTTP 503). Until MP-246 is built the stub fails every call with
 * ENR-GEN-003, so registration can't succeed in an environment without a real verifier.
 */
public interface DeviceAttestationVerifier {

	/**
	 * @param publicKey the key being registered, already checked to be a valid EC P-256 public key; the
	 *                  attestation must be bound to this key (Android: the attested key; iOS: the clientDataHash)
	 * @param jkt       RFC 7638 thumbprint of {@code publicKey}
	 */
	AttestationResult verify(Platform platform, PublicKeyJwk publicKey, String jkt, AttestationEvidence evidence);

	/**
	 * @param integrityLevel  short label for what was proven, e.g. {@code STRONGBOX}, {@code TEE}, {@code APP_ATTEST}
	 * @param appAttestKeyId  iOS: the verified App Attest key ID; null on Android
	 * @param summary         small JSON-friendly outcome stored in {@code idpaas.device.attestation_summary}. Must
	 *                        not contain raw tokens, certificates or anything personal.
	 * @param osVersion       the OS release version the attestation itself proves, as a dotted number ("14",
	 *                        "14.0.1"), or null if it carries none. Android Key Attestation has the OS version
	 *                        (an integer such as 140000: convert it to the dotted release version); iOS App
	 *                        Attest has none. The supported-device policy uses it instead of the version the
	 *                        phone reports, which a rooted phone can edit; with null it falls back to the
	 *                        reported one. Anything that is not a dotted number is a verifier fault (503).
	 */
	record AttestationResult(String integrityLevel, String appAttestKeyId, Map<String, Object> summary,
			String osVersion) {

		/** An attestation that proves no OS version. */
		public AttestationResult(String integrityLevel, String appAttestKeyId, Map<String, Object> summary) {
			this(integrityLevel, appAttestKeyId, summary, null);
		}
	}
}
