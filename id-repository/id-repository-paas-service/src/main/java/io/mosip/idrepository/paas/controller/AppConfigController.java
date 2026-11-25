package io.mosip.idrepository.paas.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.idrepository.paas.config.AppConfigProperties;
import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.service.AppConfigService;
import io.mosip.idrepository.paas.util.AppVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code GET /v1/enrolment/app/config} (MP-243): the config the app reads at launch. Public: it's called
 * before the app has a session, so {@code EnrolmentSessionFilter} skips it; {@code PublicEndpointRateLimitFilter}
 * limits it per client IP instead.
 */
@RestController
public class AppConfigController {

	private final AppConfigService appConfigService;
	private final AppConfigProperties properties;

	public AppConfigController(AppConfigService appConfigService, AppConfigProperties properties) {
		this.appConfigService = appConfigService;
		this.properties = properties;
	}

	@Operation(summary = "App config for one platform",
			description = "Minimum and latest app version, store link, maintenance mode, feature flags and the "
					+ "supported-device policy. No session needed. Send If-None-Match with the last ETag to get "
					+ "304 when nothing has changed.")
	@ApiResponse(responseCode = "200", description = "The platform's config")
	@ApiResponse(responseCode = "304", description = "Unchanged since the ETag in If-None-Match")
	@ApiResponse(responseCode = "400", description = "ENR-GEN-001: platform missing or unknown, or appVersion "
			+ "not a dotted number")
	@ApiResponse(responseCode = "429", description = "ENR-GEN-002: too many requests from this client")
	@ApiResponse(responseCode = "503", description = "ENR-GEN-003: config can't be read")
	@GetMapping(path = EnrolmentApi.APP_CONFIG_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EnrolmentResponse<AppConfigResponse>> getConfig(
			@Parameter(description = "android or ios", required = true)
			@RequestParam(value = "platform", required = false) String platformCode,
			@Parameter(description = "The app's own version, e.g. 1.3.0. When sent, the response says whether "
					+ "an update is required or available.")
			@RequestParam(value = "appVersion", required = false) String appVersion,
			@RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
			@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
			HttpServletRequest httpRequest) {
		httpRequest.setAttribute(EnrolmentApi.API_ID_ATTRIBUTE, EnrolmentApi.APP_CONFIG);

		// Parameters are optional at the Spring level so that a missing one is ENR-GEN-001, not a generic 400.
		Platform platform = Platform.fromCode(platformCode)
				.orElseThrow(() -> new EnrolmentException(EnrolmentErrorCode.GEN_001, "platform missing or unknown"));
		String version = appVersion == null || appVersion.isBlank() ? null : appVersion.trim();
		if (version != null && !AppVersion.isValid(version)) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_001, "appVersion is not a dotted number");
		}

		AppConfigService.Result result = appConfigService.get(platform, version, acceptLanguage);

		HttpHeaders headers = new HttpHeaders();
		headers.setETag(result.eTag());
		headers.setCacheControl(CacheControl.maxAge(properties.getMaxAge()).cachePublic());
		// The maintenance message depends on Accept-Language; the URL carries platform and appVersion.
		headers.setVary(List.of(HttpHeaders.ACCEPT_LANGUAGE));

		if (matches(ifNoneMatch, result.eTag())) {
			return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
		}
		return new ResponseEntity<>(EnrolmentResponse.success(EnrolmentApi.APP_CONFIG, result.response()), headers,
				HttpStatus.OK);
	}

	/** If-None-Match holds this ETag (strong or weak form) or {@code *}. */
	static boolean matches(String ifNoneMatch, String eTag) {
		if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
			return false;
		}
		for (String candidate : ifNoneMatch.split(",")) {
			String value = candidate.trim();
			if (value.startsWith("W/")) {
				value = value.substring(2);
			}
			if (value.equals("*") || value.equals(eTag)) {
				return true;
			}
		}
		return false;
	}
}
