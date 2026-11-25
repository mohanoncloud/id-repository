package io.mosip.idrepository.paas.dto.device;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code response} of {@code POST /device/register}: the app keeps the ID and sends it to create a session. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegisterResponse {

	@Schema(example = "dev_1763547330000_3f9a2c1b")
	private String deviceId;
}
