package io.mosip.idrepository.paas.dto.device;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * What the app sends for the attestation checks (MP-246). The shape is a first proposal: Android sends the
 * Key Attestation certificate chain and the Play Integrity token, iOS the App Attest object and key ID. MP-246
 * owns the final fields and the verification; this service only passes them on.
 *
 * <p>These are secrets-adjacent and large: never logged, and not stored (only the verifier's summary is).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttestationEvidence {

	/** The one-time challenge the attestation was made against (MP-246 issues it). */
	private String challengeId;

	/** Android Key Attestation: base64 DER certificates, leaf first. */
	private List<String> certificateChain;

	/** Android Play Integrity token. */
	private String integrityToken;

	/** iOS App Attest: the key ID the app got from {@code generateKey}. */
	private String keyId;

	/** iOS App Attest: base64 CBOR attestation object. */
	private String attestationObject;

	@Override
	public String toString() {
		return "AttestationEvidence[redacted]";
	}
}
