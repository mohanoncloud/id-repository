package io.mosip.idrepository.paas.repository;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One row of {@code idpaas.app_config}.
 *
 * @param maintenanceMessage approved text per language code ({@code eng}, {@code ara}, ...); empty if none
 * @param maintenanceEndsAt  expected end of maintenance in UTC, or null
 * @param features           on/off flags as stored (a JSON object)
 * @param devicePolicy       supported-device policy (MP-244) as stored, or null
 * @param configVersion      increased by the table's trigger on every change; used for the ETag
 */
public record AppConfigRecord(String platform, String minimumVersion, String latestVersion, String storeUrl,
		boolean maintenanceEnabled, Map<String, String> maintenanceMessage, LocalDateTime maintenanceEndsAt,
		JsonNode features, JsonNode devicePolicy, int configVersion) {
}
