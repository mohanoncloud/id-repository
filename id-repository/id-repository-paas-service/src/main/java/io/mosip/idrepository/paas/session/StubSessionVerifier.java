package io.mosip.idrepository.paas.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.kernel.core.logger.spi.Logger;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Placeholder until the session API exists: accepts any non-blank token and device signature, without
 * checking either. Loaded only when {@code mosip.idpaas.session.stub-enabled=true} (tests and dev config);
 * otherwise a real {@link SessionVerifier} must be present or the service does not start.
 */
@Component
@ConditionalOnProperty(name = StubSessionVerifier.ENABLED_PROPERTY, havingValue = "true")
public class StubSessionVerifier implements SessionVerifier {

	public static final String ENABLED_PROPERTY = "mosip.idpaas.session.stub-enabled";

	private static final Logger LOGGER = IdRepoLogger.getLogger(StubSessionVerifier.class);

	public StubSessionVerifier() {
		LOGGER.warn("-", "StubSessionVerifier", "init",
				"STUB session verifier in use: session tokens and device signatures are NOT verified");
	}

	@Override
	public EnrolmentSession verify(String sessionToken, String deviceSignature, HttpServletRequest request) {
		return new EnrolmentSession("stub-" + shortHash(sessionToken), null);
	}

	/** A stable id per token that doesn't expose the token itself. */
	static String shortHash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
