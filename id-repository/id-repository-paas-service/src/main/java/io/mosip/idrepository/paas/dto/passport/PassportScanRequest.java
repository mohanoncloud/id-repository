package io.mosip.idrepository.paas.dto.passport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/** {@code request} of {@code POST /v1/enrolment/passport} (API spec section 3). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PassportScanRequest {

	private AdditionalDetails additionalDetails;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class AdditionalDetails {
		private AddressDto address;
	}
}
