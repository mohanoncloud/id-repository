package io.mosip.idrepository.paas.session;

import io.mosip.idrepository.paas.exception.EnrolmentException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Verifies the session token and the device signature on every enrolment request.
 *
 * <p>Working assumption (until the session API is built): {@code Authorization: <scheme> <sessionToken>}
 * plus a device signature header. Header names and the scheme are in {@code mosip.idpaas.session.*}. The real
 * implementation replaces {@link StubSessionVerifier}.
 */
public interface SessionVerifier {

	/**
	 * @param sessionToken    the token, already stripped of the scheme prefix; never blank
	 * @param deviceSignature value of the device signature header; never blank
	 * @param request         the request, for implementations that need the body or other headers
	 * @return the verified session
	 * @throws EnrolmentException {@code ENR-SES-001} for a missing, invalid or expired session, or
	 *                            {@code ENR-SES-003} for a signature from a different device
	 */
	EnrolmentSession verify(String sessionToken, String deviceSignature, HttpServletRequest request);
}
