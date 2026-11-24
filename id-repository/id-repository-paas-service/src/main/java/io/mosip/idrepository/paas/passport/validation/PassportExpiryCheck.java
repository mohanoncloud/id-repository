package io.mosip.idrepository.paas.passport.validation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/**
 * Passport expiry rule (ENR-PSP-008). A passport is valid up to and including its expiry date, plus the
 * configured grace days, judged by today's date in the configured time zone (Bahamas: America/Nassau), not
 * the server's.
 *
 * <p>Used by the passport scan (MP-258); the chip read should use the same bean so both apply the same rule.
 */
@Component
public class PassportExpiryCheck {

	private final Clock clock;
	private final ZoneId zone;
	private final int graceDays;

	/** Fails at startup on an unknown time zone or negative grace days, rather than on the first scan. */
	public PassportExpiryCheck(Clock clock, PassportProperties properties) {
		if (properties.getExpiryGraceDays() < 0) {
			throw new IllegalArgumentException("mosip.idpaas.passport.expiry-grace-days must be 0 or more");
		}
		this.clock = clock;
		this.zone = ZoneId.of(properties.getExpiryZone());
		this.graceDays = properties.getExpiryGraceDays();
	}

	/** True if the passport has expired today (after any grace days). */
	public boolean isExpired(LocalDate expiryDate) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		return today.isAfter(expiryDate.plusDays(graceDays));
	}

	/** @throws EnrolmentException ENR-PSP-008 if the passport has expired. The message has no dates in it. */
	public void requireNotExpired(LocalDate expiryDate) {
		if (isExpired(expiryDate)) {
			throw new EnrolmentException(EnrolmentErrorCode.PSP_008, "PASSPORT_EXPIRED");
		}
	}
}
