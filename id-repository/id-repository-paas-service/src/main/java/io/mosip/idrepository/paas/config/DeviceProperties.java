package io.mosip.idrepository.paas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Settings for device registration ({@code POST /device/register}, MP-247). */
@Data
@ConfigurationProperties(prefix = "mosip.idpaas.device")
public class DeviceProperties {

	private RateLimit rateLimit = new RateLimit();

	@Data
	public static class RateLimit {

		/**
		 * Registrations per client IP per minute, counted per pod. The endpoint has no session to limit by and
		 * does the costly attestation checks, so it is much stricter than the app config; a phone registers
		 * once per install, and many phones can still share one carrier-NAT address.
		 */
		private int requestsPerMinute = 30;

		/** Most client IPs tracked at once; see the app config's setting of the same name. */
		private int maxTrackedClients = 10_000;
	}
}
