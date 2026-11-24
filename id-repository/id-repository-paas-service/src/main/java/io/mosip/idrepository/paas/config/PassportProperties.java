package io.mosip.idrepository.paas.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	/**
	 * Printed vs MRZ (ENR-PSP-007): OCR confidence (0.0 to 1.0) below which a printed field counts as unreadable,
	 * which asks for a retake (ENR-PSP-002) instead of the assisted desk.
	 */
	private double printedMinConfidence = 0.80;

	/**
	 * Printed nationality words accepted for each MRZ nationality code; the data page shows a word (e.g.
	 * BAHAMIAN), the MRZ a code (BHS). The printed code itself is always accepted.
	 */
	private Map<String, List<String>> nationalityNames = new LinkedHashMap<>(
			Map.of("BHS", List.of("BAHAMIAN", "BAHAMAS", "THE BAHAMAS")));
}
