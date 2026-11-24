package io.mosip.idrepository.paas.dto.appconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** {@code response} of {@code GET /app/config} (MP-243). Public: never put anything sensitive here. */
@Data
public class AppConfigResponse {

	@Schema(description = "android or ios", example = "android")
	private String platform;

	@Schema(description = "Below this version the app shows the force-update screen.", example = "1.2.0")
	private String minimumVersion;

	@Schema(description = "Newest version; lets the app suggest an update without forcing it. May be null.",
			example = "1.4.1")
	private String latestVersion;

	@Schema(description = "Play Store or App Store link for this platform.")
	private String storeUrl;

	@Schema(description = "Only when appVersion was sent: true if appVersion is below minimumVersion.")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean updateRequired;

	@Schema(description = "Only when appVersion was sent: true if appVersion is below latestVersion.")
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private Boolean updateAvailable;

	private MaintenanceDto maintenance;

	@Schema(description = "On/off flags the app reads, e.g. {\"showUin\": false, \"emailSkipAllowed\": true}. "
			+ "The agreed list is in MP-243.")
	private JsonNode features;

	@Schema(description = "Supported-device policy (MP-244): {\"policyVersion\", \"minOsVersion\", "
			+ "\"blockedModels\": [{\"manufacturer\", \"model\", \"reasonCode\"}]}. Null if not set; the app then "
			+ "uses its cached copy or built-in defaults.")
	private JsonNode devicePolicy;

	@Data
	public static class MaintenanceDto {

		@Schema(description = "When true the app shows the message and doesn't start an enrolment.")
		private boolean enabled;

		@Schema(description = "Approved text in the app's language (Accept-Language), else the default language. "
				+ "Null when maintenance is off.")
		private String message;

		@Schema(description = "Expected end of maintenance (UTC, ISO-8601 with milliseconds), or null.",
				example = "2026-11-25T06:00:00.000Z")
		private String endsAt;
	}
}
