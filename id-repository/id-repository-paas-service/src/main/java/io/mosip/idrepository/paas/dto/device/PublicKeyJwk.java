package io.mosip.idrepository.paas.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * The device public key as a JWK (RFC 7517). Only EC P-256 ({@code kty} EC, {@code crv} P-256) is accepted;
 * see the session spec's open point on the key type. Other members ({@code alg}, {@code use}, {@code kid})
 * are ignored. {@code d} is read only to reject a key that carries private material.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeyJwk {

	@Schema(example = "EC")
	private String kty;

	@Schema(example = "P-256")
	private String crv;

	@Schema(description = "base64url, 32 bytes", example = "l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs")
	private String x;

	@Schema(description = "base64url, 32 bytes", example = "9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA")
	private String y;

	/** Private key member: must be absent. Never logged or echoed. */
	@Schema(hidden = true)
	private String d;

	@Override
	public String toString() {
		return "PublicKeyJwk[redacted]";
	}
}
