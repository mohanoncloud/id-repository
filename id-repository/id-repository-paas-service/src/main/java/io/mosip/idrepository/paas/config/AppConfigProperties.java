package io.mosip.idrepository.paas.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Caching, language and rate limit for the public app config endpoint ({@code GET /app/config}, MP-243). */
@Data
@ConfigurationProperties(prefix = "mosip.idpaas.app-config")
public class AppConfigProperties {

	/**
	 * How long each pod keeps a platform's config before reading the table again. A change in
	 * {@code idpaas.app_config} reaches the app within this time plus {@link #maxAge}.
	 */
	private Duration cacheTtl = Duration.ofSeconds(30);

	/** {@code Cache-Control: max-age} sent to the app and any cache in between. */
	private Duration maxAge = Duration.ofSeconds(60);

	/**
	 * Longest a read of {@code idpaas.app_config} may take (rounded up to whole seconds, at least 1). The
	 * read holds the platform's refresh lock, so a hung query would otherwise keep every launch on the old copy
	 * (or, with no copy, waiting) until the connection gives up.
	 */
	private Duration queryTimeout = Duration.ofSeconds(3);

	/** Language of the maintenance message when none of the app's Accept-Language languages has one. */
	private String defaultLanguage = "eng";

	private RateLimit rateLimit = new RateLimit();

	@Data
	public static class RateLimit {

		/**
		 * Requests per client IP per minute, counted per pod. Generous on purpose: many phones can share one
		 * mobile-network IP (carrier NAT), and the app calls this once per launch.
		 */
		private int requestsPerMinute = 120;

		/**
		 * Most client IPs tracked at once. When full, counts from earlier minutes are dropped; if every tracked
		 * client is active this minute, new clients pass uncounted until the next minute.
		 */
		private int maxTrackedClients = 10_000;
	}
}
