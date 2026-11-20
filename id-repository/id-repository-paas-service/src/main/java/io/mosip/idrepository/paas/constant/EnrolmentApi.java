package io.mosip.idrepository.paas.constant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Envelope {@code id} values per endpoint (API spec section 1). Responses, including errors, echo the id
 * of the endpoint that was called.
 */
public final class EnrolmentApi {

	public static final String VERSION = "1.0";

	public static final String PASSPORT = "mosip.enrolment.passport";

	/** Used when the endpoint can't be determined (e.g. an unknown path). */
	public static final String DEFAULT = "mosip.enrolment";

	/** Request attribute holding the envelope id of the endpoint being served. */
	public static final String API_ID_ATTRIBUTE = "io.mosip.idrepository.paas.apiId";

	private EnrolmentApi() {
	}

	/** Envelope id for the endpoint a request is calling. */
	public static String forRequest(HttpServletRequest request) {
		return pathWithinService(request).startsWith("/passport") ? PASSPORT : DEFAULT;
	}

	/** The request path without the {@code /v1/enrolment} context path, e.g. {@code /passport}. */
	public static String pathWithinService(HttpServletRequest request) {
		String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
		String context = request.getContextPath() == null ? "" : request.getContextPath();
		return uri.startsWith(context) ? uri.substring(context.length()) : uri;
	}
}
