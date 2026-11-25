package io.mosip.idrepository.paas.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterRequest;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.repository.DeviceRecord;
import io.mosip.idrepository.paas.repository.DeviceRepository;
import io.mosip.idrepository.paas.service.DevicePolicyChecker;
import io.mosip.idrepository.paas.service.DeviceService;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier.AttestationResult;
import io.mosip.idrepository.paas.util.AppVersion;
import io.mosip.idrepository.paas.util.DeviceKey;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * Device registration. The database has the last word on uniqueness (decision B1): the insert either works or
 * hits {@code uk_device_jkt} / {@code uk_device_attest_key}, so simultaneous requests for the same key can't
 * both succeed. Nothing is logged that came from the request except the platform and, on failure, a reason
 * code: no keys, tokens, models or OS versions.
 */
@Service
public class DeviceServiceImpl implements DeviceService {

	private static final Logger LOGGER = IdRepoLogger.getLogger(DeviceServiceImpl.class);
	private static final String CLASS = "DeviceServiceImpl";

	private static final int MAX_MODEL = 128;
	private static final int MAX_MANUFACTURER = 64;

	private final DeviceAttestationVerifier attestationVerifier;
	private final DevicePolicyChecker policyChecker;
	private final DeviceRepository repository;
	private final ObjectMapper mapper;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public DeviceServiceImpl(DeviceAttestationVerifier attestationVerifier, DevicePolicyChecker policyChecker,
			DeviceRepository repository, ObjectMapper mapper, Clock clock) {
		this.attestationVerifier = attestationVerifier;
		this.policyChecker = policyChecker;
		this.repository = repository;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Override
	public DeviceRegisterResponse register(DeviceRegisterRequest request) {
		if (request == null) {
			throw invalid("request missing");
		}
		Platform platform = Platform.fromCode(request.getPlatform())
				.orElseThrow(() -> invalid("platform missing or unknown"));
		String osVersion = trimmed(request.getOsVersion());
		if (!AppVersion.isValid(osVersion)) {
			throw invalid("osVersion is not a dotted number");
		}
		String appVersion = trimmed(request.getAppVersion());
		if (!AppVersion.isValid(appVersion)) {
			throw invalid("appVersion is not a dotted number");
		}
		String model = trimmed(request.getModel());
		if (model == null || model.length() > MAX_MODEL) {
			throw invalid("model missing or too long");
		}
		String manufacturer = trimmed(request.getManufacturer());
		if (platform == Platform.ANDROID && (manufacturer == null || manufacturer.length() > MAX_MANUFACTURER)) {
			throw invalid("manufacturer missing or too long");
		}
		if (platform == Platform.IOS) {
			manufacturer = null;
		}
		if (request.getAttestation() == null) {
			throw invalid("attestation missing");
		}
		DeviceKey key;
		try {
			key = DeviceKey.of(request.getPublicKey());
		} catch (IllegalArgumentException e) {
			throw invalid("publicKey: " + e.getMessage());
		}

		// 1. Attestation (MP-246): genuine app, unmodified device, key in secure hardware.
		AttestationResult attestation = attestationVerifier.verify(platform, request.getPublicKey(), key.jkt(),
				request.getAttestation());
		String appAttestKeyId = attestation.appAttestKeyId();
		if (platform == Platform.IOS && (appAttestKeyId == null || appAttestKeyId.isBlank())) {
			LOGGER.error("-", CLASS, "register", "Attestation verifier returned no App Attest key ID for iOS");
			throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
					"attestation verifier returned no App Attest key ID");
		}
		if (platform == Platform.ANDROID) {
			appAttestKeyId = null;
		}

		// 2. Supported-device policy (fails closed if app_config can't be read). The OS version the attestation
		// proved is used when the verifier has one; otherwise the value the phone reports, which on a rooted
		// phone can be edited (see DevicePolicyChecker), so the real protection is the attestation.
		String attestedOsVersion = attestation.osVersion();
		if (attestedOsVersion != null) {
			if (!AppVersion.isValid(attestedOsVersion.trim())) {
				LOGGER.error("-", CLASS, "register", "Attestation verifier returned an OS version that is not a dotted number");
				throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
						"attestation verifier returned an invalid OS version");
			}
			osVersion = attestedOsVersion.trim();
		}
		String policyVersion = policyChecker.check(platform, osVersion, manufacturer, model);

		// 3. Store (os_version is the version the policy was checked with). The unique indexes decide duplicates.
		DeviceRecord device = new DeviceRecord(newDeviceId(), key.jkt(), key.canonicalJwk(), appAttestKeyId,
				platform.code(), osVersion, manufacturer, model, appVersion, policyVersion,
				json(attestation.summary()), DeviceRecord.ACTIVE, LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
		try {
			repository.insert(device);
		} catch (DuplicateKeyException e) {
			throw duplicate(key.jkt(), appAttestKeyId);
		} catch (DataAccessException e) {
			LOGGER.error("-", CLASS, "register", "Can't write idpaas.device: " + e.getClass().getSimpleName());
			throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
					"idpaas.device unavailable");
		}
		LOGGER.info("-", CLASS, "register", "Registered " + platform.code() + " device " + device.deviceId()
				+ " (policy " + policyVersion + ")");
		return new DeviceRegisterResponse(device.deviceId());
	}

	/**
	 * The key (or iOS App Attest key ID) is already registered: ENR-DEV-002, whether or not that device was
	 * revoked. A genuine app never registers a key twice (a reinstall makes a new key), so a repeat is a replay
	 * or a probe, and a separate code for a revoked key would tell the caller it was revoked. Whether it was is
	 * in the log only (the handler logs the exception message). A revoked key can never be registered again.
	 */
	private EnrolmentException duplicate(String jkt, String appAttestKeyId) {
		String state = "state unknown";
		try {
			Optional<DeviceRecord> existing = repository.findByJkt(jkt);
			if (existing.isEmpty() && appAttestKeyId != null) {
				existing = repository.findByAppAttestKeyId(appAttestKeyId);
			}
			if (existing.isPresent()) {
				state = existing.get().isRevoked() ? "the device is revoked" : "the device is active";
			}
		} catch (DataAccessException e) {
			LOGGER.warn("-", CLASS, "duplicate", "Can't read idpaas.device: " + e.getClass().getSimpleName());
		}
		return new EnrolmentException(EnrolmentErrorCode.DEV_002, "Device key already registered; " + state);
	}

	/** {@code dev_<epoch millis>_<8 hex>}, the format in the session spec's examples. */
	private String newDeviceId() {
		byte[] suffix = new byte[4];
		random.nextBytes(suffix);
		return "dev_" + clock.millis() + "_" + HexFormat.of().formatHex(suffix);
	}

	private String json(Map<String, Object> summary) {
		try {
			return mapper.writeValueAsString(summary == null ? Map.of() : summary);
		} catch (JsonProcessingException e) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
					"attestation summary is not JSON");
		}
	}

	private static String trimmed(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static EnrolmentException invalid(String reason) {
		return new EnrolmentException(EnrolmentErrorCode.GEN_001, reason);
	}
}
