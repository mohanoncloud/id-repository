package io.mosip.idrepository.paas.service;

import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/**
 * The config the app reads at launch (MP-243): minimum and latest version, store link, maintenance mode,
 * feature flags and the supported-device policy (MP-244), one set per platform.
 */
public interface AppConfigService {

	/**
	 * @param appVersion     the app's own version, or null; when given, the answer says whether an update is
	 *                       required or available
	 * @param acceptLanguage the request's Accept-Language header, or null; picks the maintenance message
	 * @throws EnrolmentException ENR-GEN-003 (503) if the platform's config can't be read
	 */
	Result get(Platform platform, String appVersion, String acceptLanguage);

	/**
	 * @param response the {@code response} body
	 * @param eTag     strong ETag (quoted) that changes whenever the body would, apart from responsetime
	 */
	record Result(AppConfigResponse response, String eTag) {
	}
}
