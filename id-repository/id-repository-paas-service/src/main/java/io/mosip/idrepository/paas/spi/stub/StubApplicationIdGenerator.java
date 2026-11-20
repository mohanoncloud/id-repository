package io.mosip.idrepository.paas.spi.stub;

import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.spi.ApplicationIdGenerator;

/**
 * Until the ID PAAS centre and machine IDs are confirmed: a 29-digit id in the MOSIP RID layout
 * (centre 5 + machine 5 + sequence 5 + UTC yyyyMMddHHmmss), with placeholder centre/machine IDs and a
 * random sequence. Not unique enough for production.
 */
@Component
public class StubApplicationIdGenerator implements ApplicationIdGenerator {

	static final String PLACEHOLDER_CENTRE_ID = "10001";
	static final String PLACEHOLDER_MACHINE_ID = "10001";

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private final SecureRandom random = new SecureRandom();

	public StubApplicationIdGenerator() {
		NotConfigured.warn(getClass(), "Kernel RID generator (ID PAAS centre and machine IDs)");
	}

	@Override
	public String generate() {
		return PLACEHOLDER_CENTRE_ID + PLACEHOLDER_MACHINE_ID + String.format("%05d", random.nextInt(100_000))
				+ ZonedDateTime.now(ZoneOffset.UTC).format(TIMESTAMP);
	}
}
