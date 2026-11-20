package io.mosip.idrepository.paas.spi;

/**
 * Generates the Application ID (AID), which is also the ID Repository draft's {@code registrationId}.
 * Decided: the kernel RID generator with a dedicated ID PAAS centre and machine ID (IDs to be confirmed).
 */
public interface ApplicationIdGenerator {

	String generate();
}
