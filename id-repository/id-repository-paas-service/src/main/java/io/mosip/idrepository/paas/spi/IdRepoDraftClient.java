package io.mosip.idrepository.paas.spi;

import java.util.Map;

/**
 * Calls the ID Repository draft APIs ({@code /idrepository/v1/identity/draft/*}) with a service token
 * (ID_REPOSITORY role). Draft save is blocked until the Bahamas ID schema gains address and passport fields.
 *
 * <p>Failures are thrown as {@code EnrolmentException} with ENR-PSP-011 (HTTP 503).
 */
public interface IdRepoDraftClient {

	/** {@code POST draft/create/{aid}} with no UIN: the ID Repository generates the UIN. */
	void createDraft(String applicationId);

	/** {@code PATCH draft/update/{aid}} with identity fields (validated against the ID schema). */
	void updateDraft(String applicationId, Map<String, Object> identity);

	/** {@code DELETE draft/discard/{aid}}. */
	void discardDraft(String applicationId);
}
