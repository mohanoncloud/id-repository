package io.mosip.idrepository.paas.passport.compare;

/**
 * Printed (visual zone) fields compared with the MRZ. Only these names are logged, never the values.
 * {@link #key()} is the key in {@code PassportImageReading.printedFields()}; it matches the field names of the
 * {@code passport} object in the API response.
 */
public enum VizField {

	SURNAME("surname"),
	GIVEN_NAMES("givenNames"),
	DOCUMENT_NUMBER("passportNumber"),
	NATIONALITY("nationality"),
	DATE_OF_BIRTH("dateOfBirth"),
	SEX("sex"),
	EXPIRY_DATE("dateOfExpiry");

	private final String key;

	VizField(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}
}
