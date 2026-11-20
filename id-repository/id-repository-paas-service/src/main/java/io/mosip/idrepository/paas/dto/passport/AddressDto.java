package io.mosip.idrepository.paas.dto.passport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * Address sent with the passport scan. Keys follow the API spec; required fields are
 * {@code addressLine1}, {@code region}, {@code province}, {@code city} and {@code zone} (ENR-PSP-010).
 * {@code toString()} is redacted: an address is personal data.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddressDto {

	private String addressLine1;
	private String addressLine2;
	private String city;
	private String province;
	private String region;
	private String zone;
	private String postalCode;

	@Override
	public String toString() {
		return "AddressDto[redacted]";
	}
}
