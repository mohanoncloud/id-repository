package io.mosip.idrepository.paas.passport.mrz;

import java.time.LocalDate;

/**
 * Fields read from a validated TD3 passport MRZ. Filler characters are removed and name parts are
 * separated by single spaces.
 *
 * @param documentType   "P", or "P" plus the state-defined subtype letter (e.g. "PD")
 * @param issuingState   ICAO 3-letter code with fillers removed (e.g. "BHS", "D")
 * @param surname        primary identifier
 * @param givenNames     secondary identifier; empty if the passport has none
 * @param nameTruncated  true if the name filled the whole MRZ name field, so it may be truncated
 * @param documentNumber passport number, fillers removed
 * @param nationality    ICAO 3-letter code with fillers removed
 * @param optionalData   personal number / optional data, fillers removed; empty if not used
 */
public record PassportMrz(String documentType, String issuingState, String surname, String givenNames,
		boolean nameTruncated, String documentNumber, String nationality, LocalDate dateOfBirth, Sex sex,
		LocalDate expiryDate, String optionalData) {

	/** Redacted so passport data never ends up in logs by accident. */
	@Override
	public String toString() {
		return "PassportMrz[redacted]";
	}
}
