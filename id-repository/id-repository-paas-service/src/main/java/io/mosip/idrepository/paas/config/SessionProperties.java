package io.mosip.idrepository.paas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Header names and token scheme for the session check, kept in config until the session API format is final. */
@Data
@ConfigurationProperties(prefix = "mosip.idpaas.session")
public class SessionProperties {

	private String tokenHeader = "Authorization";
	/** Scheme in front of the token in {@link #tokenHeader}, e.g. {@code Bearer} or {@code DPoP}. */
	private String tokenScheme = "Bearer";
	private String deviceSignatureHeader = "X-Device-Signature";
	/** Loads {@code StubSessionVerifier}, which accepts any token. Tests and dev only. */
	private boolean stubEnabled = false;
}
