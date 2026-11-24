package io.mosip.idrepository.paas.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.config.AppConfigProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse.MaintenanceDto;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.repository.AppConfigRecord;
import io.mosip.idrepository.paas.repository.AppConfigRepository;
import io.mosip.idrepository.paas.service.AppConfigService;
import io.mosip.idrepository.paas.util.AppVersion;
import io.mosip.kernel.core.logger.spi.Logger;

/**
 * Reads the platform's row from {@code idpaas.app_config} and keeps it for
 * {@code mosip.idpaas.app-config.cache-ttl} (30 s by default), so app launches don't each hit the database.
 *
 * <p>If the database can't be read and an earlier copy is held, that copy is served and the read is tried
 * again after another TTL: a short database outage doesn't stop the app from starting. With no copy (database
 * down, or the table not seeded yet), the answer is ENR-GEN-003 (503) for a TTL and the app falls back to its
 * own cached config or defaults.
 */
@Service
public class AppConfigServiceImpl implements AppConfigService {

	private static final Logger LOGGER = IdRepoLogger.getLogger(AppConfigServiceImpl.class);
	private static final String CLASS = "AppConfigServiceImpl";

	private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private final AppConfigRepository repository;
	private final AppConfigProperties properties;
	private final Clock clock;
	private final Map<Platform, Cached> cache = new EnumMap<>(Platform.class);
	private final Map<Platform, ReentrantLock> locks = new EnumMap<>(Platform.class);

	public AppConfigServiceImpl(AppConfigRepository repository, AppConfigProperties properties, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
		for (Platform platform : Platform.values()) {
			locks.put(platform, new ReentrantLock());
		}
	}

	@Override
	public Result get(Platform platform, String appVersion, String acceptLanguage) {
		AppConfigRecord config = load(platform);

		AppConfigResponse response = new AppConfigResponse();
		response.setPlatform(platform.code());
		response.setMinimumVersion(config.minimumVersion());
		response.setLatestVersion(config.latestVersion());
		response.setStoreUrl(config.storeUrl());
		response.setFeatures(config.features());
		response.setDevicePolicy(config.devicePolicy());

		StringBuilder eTag = new StringBuilder().append(platform.code()).append("-v").append(config.configVersion());

		if (appVersion != null) {
			response.setUpdateRequired(AppVersion.isOlder(appVersion, config.minimumVersion()));
			response.setUpdateAvailable(
					config.latestVersion() != null && AppVersion.isOlder(appVersion, config.latestVersion()));
			eTag.append("-u").append(flag(response.getUpdateRequired())).append(flag(response.getUpdateAvailable()));
		}

		MaintenanceDto maintenance = new MaintenanceDto();
		maintenance.setEnabled(config.maintenanceEnabled());
		if (config.maintenanceEnabled()) {
			String language = language(config.maintenanceMessage(), acceptLanguage);
			maintenance.setMessage(language == null ? null : config.maintenanceMessage().get(language));
			maintenance.setEndsAt(utc(config.maintenanceEndsAt()));
			eTag.append("-m").append(language == null ? "" : language);
		}
		response.setMaintenance(maintenance);

		return new Result(response, "\"" + eTag + "\"");
	}

	/**
	 * The cached row while it's fresh; otherwise a new read, falling back to the old copy if the read fails.
	 * One request per platform reads at a time: while it does, the others get the old copy straight away
	 * instead of each waiting on the database (with no copy yet, they wait for that one read).
	 */
	private AppConfigRecord load(Platform platform) {
		Cached cached = cached(platform);
		if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
			return served(cached);
		}
		ReentrantLock lock = locks.get(platform);
		if (cached != null && cached.config() != null) {
			if (!lock.tryLock()) {
				return cached.config();
			}
		} else {
			lock.lock();
		}
		try {
			return refresh(platform);
		} finally {
			lock.unlock();
		}
	}

	/** Reads the row, unless another request did while this one waited for the lock. */
	private AppConfigRecord refresh(Platform platform) {
		Instant now = clock.instant();
		Cached cached = cached(platform);
		if (cached != null && now.isBefore(cached.expiresAt())) {
			return served(cached);
		}
		try {
			Optional<AppConfigRecord> row = repository.findByPlatform(platform);
			if (row.isEmpty()) {
				throw missing(platform, cached, now);
			}
			remember(platform, row.get(), now);
			return row.get();
		} catch (DataAccessException e) {
			if (cached == null || cached.config() == null) {
				LOGGER.error("-", CLASS, "load", "Can't read idpaas.app_config: " + e.getClass().getSimpleName());
				// Remembered for a TTL too, so the requests behind this one answer at once.
				remember(platform, null, now);
				throw unavailable();
			}
			LOGGER.warn("-", CLASS, "load", "Can't read idpaas.app_config (" + e.getClass().getSimpleName()
					+ "); serving the copy from config_version " + cached.config().configVersion());
			remember(platform, cached.config(), now);
			return cached.config();
		}
	}

	/**
	 * The table has no row for the platform (not seeded yet): ENR-GEN-003. With no earlier copy this is
	 * remembered for a TTL like a failed read, so until the row is added the requests behind this one answer at
	 * once instead of each querying the table. (The table doesn't allow deleting rows, so a row that was read
	 * before and is now gone is not expected; it is reported, and the copy is left as it is.)
	 */
	private EnrolmentException missing(Platform platform, Cached cached, Instant now) {
		LOGGER.error("-", CLASS, "load", "No row in idpaas.app_config for " + platform.code());
		if (cached == null || cached.config() == null) {
			remember(platform, null, now);
		}
		return new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				"No row in idpaas.app_config for " + platform.code());
	}

	/** The cached row, or ENR-GEN-003 if what's cached is a failed read with no earlier copy. */
	private static AppConfigRecord served(Cached cached) {
		if (cached.config() == null) {
			throw unavailable();
		}
		return cached.config();
	}

	private static EnrolmentException unavailable() {
		return new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				"idpaas.app_config unavailable");
	}

	private Cached cached(Platform platform) {
		synchronized (cache) {
			return cache.get(platform);
		}
	}

	/** @param config the row, or null to remember that the read failed with no earlier copy */
	private void remember(Platform platform, AppConfigRecord config, Instant now) {
		synchronized (cache) {
			cache.put(platform, new Cached(config, now.plus(properties.getCacheTtl())));
		}
	}

	/**
	 * The language to show the maintenance message in: the first Accept-Language entry, by weight, that has a
	 * message (2- or 3-letter codes both match the stored 3-letter codes, so {@code en} finds {@code eng}),
	 * else the default language, else any message there is. Null if there are no messages.
	 */
	String language(Map<String, String> messages, String acceptLanguage) {
		if (messages.isEmpty()) {
			return null;
		}
		for (Locale.LanguageRange range : ranges(acceptLanguage)) {
			String code = iso3(range.getRange());
			if (code != null && messages.containsKey(code)) {
				return code;
			}
		}
		if (messages.containsKey(properties.getDefaultLanguage())) {
			return properties.getDefaultLanguage();
		}
		return messages.keySet().iterator().next();
	}

	private static List<Locale.LanguageRange> ranges(String acceptLanguage) {
		if (acceptLanguage == null || acceptLanguage.isBlank()) {
			return List.of();
		}
		try {
			return Locale.LanguageRange.parse(acceptLanguage);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
	}

	/** ISO 639-2 code for a language range such as {@code en-US} or {@code ara}; null for {@code *} or unknown. */
	private static String iso3(String range) {
		if (range == null || range.startsWith("*")) {
			return null;
		}
		try {
			String code = Locale.forLanguageTag(range).getISO3Language();
			return code.isEmpty() ? null : code;
		} catch (MissingResourceException e) {
			return null;
		}
	}

	private static String utc(LocalDateTime time) {
		return time == null ? null : time.atOffset(ZoneOffset.UTC).format(UTC_TIME);
	}

	private static char flag(Boolean value) {
		return Boolean.TRUE.equals(value) ? '1' : '0';
	}

	private record Cached(AppConfigRecord config, Instant expiresAt) {
	}
}
