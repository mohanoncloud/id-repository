package io.mosip.idrepository.paas.constant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Envelope {@code id} values per endpoint (API spec section 1). Responses, including errors, echo the id
 * of the endpoint that was called.
 */
public final class EnrolmentApi {

	public static final String VERSION = "1.0";

	public static final String PASSPORT = "mosip.enrolment.passport";

	/** Public app config (MP-243): the one endpoint called without a session. */
	public static final String APP_CONFIG = "mosip.enrolment.app.config";

	/** Path of the app config endpoint within the service. */
	public static final String APP_CONFIG_PATH = "/app/config";

	/** Device registration (MP-247): called before the app has a session, so like the app config it is public. */
	public static final String DEVICE_REGISTER = "mosip.enrolment.device.register";

	/** Path of the device registration endpoint within the service. */
	public static final String DEVICE_REGISTER_PATH = "/device/register";

	/** Used when the endpoint can't be determined (e.g. an unknown path). */
	public static final String DEFAULT = "mosip.enrolment";

	/** Request attribute holding the envelope id of the endpoint being served. */
	public static final String API_ID_ATTRIBUTE = "io.mosip.idrepository.paas.apiId";

	private EnrolmentApi() {
	}

	/** Envelope id for the endpoint a request is calling. */
	public static String forRequest(HttpServletRequest request) {
		String path = pathWithinService(request);
		if (path.startsWith("/passport")) {
			return PASSPORT;
		}
		if (isDeviceRegister(request)) {
			return DEVICE_REGISTER;
		}
		return isAppConfig(request) ? APP_CONFIG : DEFAULT;
	}

	/** True for device registration, which is called before the app has a session. */
	public static boolean isDeviceRegister(HttpServletRequest request) {
		return DEVICE_REGISTER_PATH.equals(pathWithinService(request));
	}

	/** True for the endpoints the app calls without a session: the app config and device registration. */
	public static boolean isPublic(HttpServletRequest request) {
		return isAppConfig(request) || isDeviceRegister(request);
	}

	/** True for the public app config endpoint, which is called before the app has a session. */
	public static boolean isAppConfig(HttpServletRequest request) {
		return APP_CONFIG_PATH.equals(pathWithinService(request));
	}

	/** The request path without the {@code /v1/enrolment} context path, e.g. {@code /passport}. */
	public static String pathWithinService(HttpServletRequest request) {
		String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
		String context = request.getContextPath() == null ? "" : request.getContextPath();
		return uri.startsWith(context) ? uri.substring(context.length()) : uri;
	}
}
