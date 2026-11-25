package io.mosip.idrepository.paas.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** {@code request} of {@code POST /device/register} (MP-247). */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceRegisterRequest {

	@Schema(description = "android or ios", example = "android")
	private String platform;

	@Schema(description = "OS release version the phone reports (Android \"14\", iOS \"17.4\"), never an Android API level",
			example = "14")
	private String osVersion;

	@Schema(description = "Build.MANUFACTURER on Android (required); not used on iOS", example = "Google")
	private String manufacturer;

	@Schema(description = "Build.MODEL on Android; the hardware identifier on iOS (iPhone10,1)", example = "Pixel 8")
	private String model;

	@Schema(description = "The My Pass app version", example = "1.3.0")
	private String appVersion;

	private PublicKeyJwk publicKey;

	private AttestationEvidence attestation;

	@Override
	public String toString() {
		return "DeviceRegisterRequest[platform=" + platform + "]";
	}
}
