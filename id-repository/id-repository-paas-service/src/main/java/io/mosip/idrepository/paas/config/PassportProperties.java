package io.mosip.idrepository.paas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Rules for the passport checks (API spec section 3). */
@Data
@ConfigurationProperties(prefix = "mosip.idpaas.passport")
public class PassportProperties {

	/** Time zone that decides today's date for the expiry check (ENR-PSP-008). */
	private String expiryZone = "America/Nassau";

	/** Days after the expiry date that are still accepted. The API spec has none, so 0. */
	private int expiryGraceDays = 0;
}
