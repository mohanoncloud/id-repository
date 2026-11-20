package io.mosip.idrepository.paas.spi;

/**
 * Keyed hash of a passport for the already-enrolled check: HMAC-SHA256 over the normalised issuing state
 * and passport number, with the key from keymanager. A plain SHA-256 can be brute-forced because passport
 * numbers are short.
 */
public interface PassportHashService {

	String hash(String issuingState, String passportNumber);
}
