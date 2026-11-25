package io.mosip.idrepository.paas.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.util.AppVersion;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * The server-side supported-device check at registration (MP-247). The app checks the same policy before
 * enrolment starts, but its copy can be edited on a rooted phone, so this is the check that counts.
 *
 * <p>The policy is {@code idpaas.app_config.device_policy} for the request's platform, read through
 * {@link AppConfigService} (same cache, same 503 when the row can't be read, so the check fails closed):
 * {@code {"policyVersion", "minOsVersion", "blockedModels": [{"manufacturer", "model", "reasonCode"}]}}.
 *
 * <p><b>The values checked are self-reported unless attested.</b> {@code osVersion}, {@code manufacturer} and
 * {@code model} come from the registration request, so on a rooted phone (the case this server-side check exists
 * for) they can be edited just like the app's cached policy. On their own they stop only honest old or blocked
 * phones; the real protection is the attestation (MP-246). {@code DeviceServiceImpl} passes the OS version the
 * attestation proved when the verifier has one ({@code AttestationResult.osVersion}). The attestation also
 * carries the model and manufacturer on Android; using those is a follow-up for MP-246.
 *
 * <p>Matching is the app's: OS versions are compared as numbers (17.4 is older than 17.10), models exactly
 * but ignoring case. On Android a blocked entry matches on manufacturer and model; on iOS the model is the
 * hardware identifier (iPhone10,1) and is unique, so only it is compared. A platform with no policy
 * ({@code device_policy} null) has no restrictions.
 */
@Component
public class DevicePolicyChecker {

	private static final Logger LOGGER = IdRepoLogger.getLogger(DevicePolicyChecker.class);
	private static final String CLASS = "DevicePolicyChecker";

	private final AppConfigService appConfigService;

	public DevicePolicyChecker(AppConfigService appConfigService) {
		this.appConfigService = appConfigService;
	}

	/**
	 * @param osVersion    dotted numeric OS release version, already validated
	 * @param manufacturer Android only; may be null
	 * @return the policy version the device was checked against, or null if the platform has no policy
	 * @throws EnrolmentException ENR-DEV-001 if the device is not supported; ENR-GEN-003 (503) if the policy
	 *                            can't be read or is unusable (fails closed)
	 */
	public String check(Platform platform, String osVersion, String manufacturer, String model) {
		JsonNode policy = appConfigService.get(platform, null, null).response().getDevicePolicy();
		if (policy == null || !policy.isObject()) {
			return null;
		}
		String policyVersion = text(policy.get("policyVersion"));

		String minOsVersion = text(policy.get("minOsVersion"));
		if (minOsVersion != null) {
			if (!AppVersion.isValid(minOsVersion)) {
				// A bad value in the table must not silently switch the check off.
				LOGGER.error("-", CLASS, "check", "device_policy.minOsVersion is not a dotted number for "
						+ platform.code());
				throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
						"device_policy.minOsVersion is not a dotted number");
			}
			if (AppVersion.isOlder(osVersion, minOsVersion)) {
				throw unsupported("OS_TOO_OLD", policyVersion);
			}
		}

		JsonNode blocked = policy.get("blockedModels");
		if (blocked != null && blocked.isArray()) {
			for (JsonNode entry : blocked) {
				if (matches(platform, entry, manufacturer, model)) {
					String reason = text(entry.get("reasonCode"));
					throw unsupported("MODEL_BLOCKED" + (reason == null ? "" : ":" + reason), policyVersion);
				}
			}
		}
		return policyVersion;
	}

	private static boolean matches(Platform platform, JsonNode entry, String manufacturer, String model) {
		String blockedModel = text(entry.get("model"));
		if (blockedModel == null || model == null || !blockedModel.equalsIgnoreCase(model.trim())) {
			return false;
		}
		if (platform == Platform.IOS) {
			return true;
		}
		String blockedManufacturer = text(entry.get("manufacturer"));
		return blockedManufacturer == null
				|| (manufacturer != null && blockedManufacturer.equalsIgnoreCase(manufacturer.trim()));
	}

	/** The node's text, trimmed; null if absent, not text, or blank. */
	private static String text(JsonNode node) {
		if (node == null || !node.isTextual() || node.asText().isBlank()) {
			return null;
		}
		return node.asText().trim();
	}

	private static EnrolmentException unsupported(String reason, String policyVersion) {
		// Reason code and policy version only: nothing that identifies the person or the phone.
		return new EnrolmentException(EnrolmentErrorCode.DEV_001,
				"Device not supported: " + reason + " (policy " + policyVersion + ")");
	}
}
