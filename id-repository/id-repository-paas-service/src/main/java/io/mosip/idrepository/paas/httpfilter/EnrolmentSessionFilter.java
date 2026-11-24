package io.mosip.idrepository.paas.httpfilter;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.config.SessionProperties;
import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.session.SessionVerifier;
import io.mosip.kernel.core.logger.spi.Logger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates every enrolment request with the session token and device signature (API spec section 1,
 * {@code ENR-SES-001} / {@code ENR-SES-003}) and stores the verified {@link EnrolmentSession} as a request
 * attribute. Any other verifier failure is {@code ENR-GEN-003}, so the app always gets {@code errors[]}.
 * Actuator, API docs and the public app config endpoint are not filtered.
 */
public class EnrolmentSessionFilter extends OncePerRequestFilter {

	/** Request attribute holding the verified {@link EnrolmentSession}. */
	public static final String SESSION_ATTRIBUTE = "io.mosip.idrepository.paas.enrolmentSession";

	private static final Logger LOGGER = IdRepoLogger.getLogger(EnrolmentSessionFilter.class);

	private final SessionVerifier verifier;
	private final SessionProperties properties;
	private final ObjectMapper mapper;

	public EnrolmentSessionFilter(SessionVerifier verifier, SessionProperties properties, ObjectMapper mapper) {
		this.verifier = verifier;
		this.properties = properties;
		this.mapper = mapper;
	}

	/**
	 * Health checks, the API docs (Swagger UI and OpenAPI JSON) and the app config don't need a session. The
	 * app reads its config at launch, before it has a session (MP-243).
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = EnrolmentApi.pathWithinService(request);
		return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
				|| EnrolmentApi.isAppConfig(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = token(request.getHeader(properties.getTokenHeader()));
		if (token == null) {
			reject(request, response, EnrolmentErrorCode.SES_001);
			return;
		}
		String signature = request.getHeader(properties.getDeviceSignatureHeader());
		if (signature == null || signature.isBlank()) {
			reject(request, response, EnrolmentErrorCode.SES_003);
			return;
		}
		EnrolmentSession session;
		try {
			session = verifier.verify(token, signature.trim(), request);
		} catch (EnrolmentException e) {
			reject(request, response, e.getErrorCode());
			return;
		} catch (RuntimeException e) {
			// @RestControllerAdvice doesn't cover filters: without this the app gets Spring's /error JSON, no errors[].
			LOGGER.error("-", "EnrolmentSessionFilter", "verify", "Unexpected " + e.getClass().getName());
			reject(request, response, EnrolmentErrorCode.GEN_003);
			return;
		}
		request.setAttribute(SESSION_ATTRIBUTE, session);
		chain.doFilter(request, response);
	}

	/** The token after the configured scheme ({@code <scheme> <token>}, scheme case-insensitive), or null. */
	private String token(String header) {
		String prefix = properties.getTokenScheme() + " ";
		if (header == null || !header.regionMatches(true, 0, prefix, 0, prefix.length())) {
			return null;
		}
		String token = header.substring(prefix.length()).trim();
		return token.isEmpty() ? null : token;
	}

	private void reject(HttpServletRequest request, HttpServletResponse response, EnrolmentErrorCode code)
			throws IOException {
		response.setStatus(code.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		mapper.writeValue(response.getOutputStream(),
				EnrolmentResponse.error(EnrolmentApi.forRequest(request), code));
	}
}
