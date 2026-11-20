package io.mosip.idrepository.paas.dto.passport;

import lombok.Data;

/**
 * Passport details read from the image, as returned to the app (API spec section 3). Dates are
 * {@code YYYY-MM-DD}. {@code toString()} is redacted: these are personal data.
 */
@Data
public class PassportDetailsDto {

	private String documentType;
	private String issuingState;
	private String passportNumber;
	private String surname;
	private String givenNames;
	private String nationality;
	private String dateOfBirth;
	private String sex;
	private String dateOfExpiry;

	@Override
	public String toString() {
		return "PassportDetailsDto[redacted]";
	}
}
