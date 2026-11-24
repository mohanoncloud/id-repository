package io.mosip.idrepository.paas.constant;

import java.util.Locale;
import java.util.Optional;

/** The platforms with their own row in {@code idpaas.app_config}. Android and iOS release separately. */
public enum Platform {

	ANDROID, IOS;

	/** The value in the {@code platform} query parameter and column, e.g. {@code android}. */
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The platform for a query parameter, ignoring case; empty if missing or unknown. */
	public static Optional<Platform> fromCode(String code) {
		if (code == null) {
			return Optional.empty();
		}
		for (Platform platform : values()) {
			if (platform.code().equalsIgnoreCase(code.trim())) {
				return Optional.of(platform);
			}
		}
		return Optional.empty();
	}
}
