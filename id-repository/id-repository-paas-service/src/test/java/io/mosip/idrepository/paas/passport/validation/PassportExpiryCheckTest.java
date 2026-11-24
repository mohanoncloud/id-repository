package io.mosip.idrepository.paas.passport.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/** Expiry rule: valid up to and including the expiry date, in Nassau time, with no grace by default. */
public class PassportExpiryCheckTest {

	private static PassportExpiryCheck at(String instant) {
		return check(instant, new PassportProperties());
	}

	private static PassportExpiryCheck check(String instant, PassportProperties properties) {
		return new PassportExpiryCheck(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), properties);
	}

	private static PassportProperties grace(int days) {
		PassportProperties properties = new PassportProperties();
		properties.setExpiryGraceDays(days);
		return properties;
	}

	@Test
	public void validBeforeTheExpiryDate() {
		assertFalse(at("2026-11-18T15:00:00Z").isExpired(LocalDate.of(2030, 1, 1)));
	}

	@Test
	public void validOnTheExpiryDate() {
		assertFalse(at("2026-11-18T15:00:00Z").isExpired(LocalDate.of(2026, 11, 18)));
	}

	@Test
	public void expiredTheDayAfter() {
		assertTrue(at("2026-11-19T15:00:00Z").isExpired(LocalDate.of(2026, 11, 18)));
	}

	// Nassau in November is EST (UTC-5): midnight on 19 Nov is 05:00 UTC.

	@Test
	public void lateEveningInNassauIsStillTheExpiryDayAlthoughUtcIsTheNextDay() {
		assertFalse(at("2026-11-19T04:59:59Z").isExpired(LocalDate.of(2026, 11, 18)));
	}

	@Test
	public void expiresAtMidnightInNassau() {
		assertTrue(at("2026-11-19T05:00:00Z").isExpired(LocalDate.of(2026, 11, 18)));
	}

	// Nassau in July is EDT (UTC-4): midnight on 2 Jul is 04:00 UTC.

	@Test
	public void summerTimeMidnight() {
		LocalDate expiry = LocalDate.of(2026, 7, 1);
		assertFalse(at("2026-07-02T03:59:59Z").isExpired(expiry));
		assertTrue(at("2026-07-02T04:00:00Z").isExpired(expiry));
	}

	@Test
	public void graceDaysExtendTheExpiryDate() {
		LocalDate expiry = LocalDate.of(2026, 11, 18); // 30 days before "today"
		assertFalse(check("2026-12-18T15:00:00Z", grace(30)).isExpired(expiry));
		assertTrue(check("2026-12-18T15:00:00Z", grace(29)).isExpired(expiry));
	}

	@Test
	public void zoneComesFromConfig() {
		PassportProperties utc = new PassportProperties();
		utc.setExpiryZone("UTC");
		// 01:00 UTC on 19 Nov: already the next day in UTC, still 18 Nov in Nassau.
		assertTrue(check("2026-11-19T01:00:00Z", utc).isExpired(LocalDate.of(2026, 11, 18)));
		assertFalse(at("2026-11-19T01:00:00Z").isExpired(LocalDate.of(2026, 11, 18)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void negativeGraceDaysFailAtStartup() {
		check("2026-11-18T15:00:00Z", grace(-1));
	}

	@Test(expected = DateTimeException.class)
	public void unknownZoneFailsAtStartup() {
		PassportProperties properties = new PassportProperties();
		properties.setExpiryZone("Mars/Base");
		check("2026-11-18T15:00:00Z", properties);
	}

	@Test
	public void requireNotExpiredPassesOnTheExpiryDate() {
		at("2026-11-19T15:00:00Z").requireNotExpired(LocalDate.of(2026, 11, 19));
	}

	@Test
	public void requireNotExpiredIsEnrPsp008WithoutDatesInTheMessage() {
		try {
			at("2026-11-19T15:00:00Z").requireNotExpired(LocalDate.of(2026, 11, 18));
			fail("expected ENR-PSP-008");
		} catch (EnrolmentException e) {
			assertEquals(EnrolmentErrorCode.PSP_008, e.getErrorCode());
			assertEquals(HttpStatus.OK, e.getHttpStatus());
			assertEquals("PASSPORT_EXPIRED", e.getMessage());
		}
	}
}
