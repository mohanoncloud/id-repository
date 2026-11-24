package io.mosip.idrepository.paas.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.config.AppConfigProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.repository.AppConfigRecord;
import io.mosip.idrepository.paas.repository.AppConfigRepository;
import io.mosip.idrepository.paas.service.AppConfigService.Result;

public class AppConfigServiceImplTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final AppConfigRepository repository = mock(AppConfigRepository.class);
	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
	private final AppConfigServiceImpl service = new AppConfigServiceImpl(repository, new AppConfigProperties(), clock);

	public static AppConfigRecord android(int configVersion) {
		return config("android", "1.2.0", "1.4.1", false, Map.of(), configVersion);
	}

	static AppConfigRecord config(String platform, String minimum, String latest, boolean maintenance,
			Map<String, String> messages, int configVersion) {
		return new AppConfigRecord(platform, minimum, latest, "https://store.example/" + platform, maintenance,
				messages, maintenance ? LocalDateTime.of(2026, 11, 25, 6, 0) : null,
				json("{\"showUin\":false,\"emailSkipAllowed\":true}"),
				json("{\"policyVersion\":\"2026-11-01\",\"minOsVersion\":\"10\",\"blockedModels\":[]}"), configVersion);
	}

	static JsonNode json(String text) {
		try {
			return MAPPER.readTree(text);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Map<String, String> messages(String... pairs) {
		Map<String, String> messages = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			messages.put(pairs[i], pairs[i + 1]);
		}
		return messages;
	}

	@Test
	public void returnsThePlatformsConfig() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3)));

		Result result = service.get(Platform.ANDROID, null, null);

		AppConfigResponse response = result.response();
		assertEquals("android", response.getPlatform());
		assertEquals("1.2.0", response.getMinimumVersion());
		assertEquals("1.4.1", response.getLatestVersion());
		assertEquals("https://store.example/android", response.getStoreUrl());
		assertFalse(response.getMaintenance().isEnabled());
		assertNull(response.getMaintenance().getMessage());
		assertNull(response.getMaintenance().getEndsAt());
		assertTrue(response.getFeatures().get("emailSkipAllowed").asBoolean());
		assertEquals("10", response.getDevicePolicy().get("minOsVersion").asText());
		assertNull("no appVersion sent", response.getUpdateRequired());
		assertNull(response.getUpdateAvailable());
		assertEquals("\"android-v3\"", result.eTag());
	}

	@Test
	public void eachPlatformIsReadSeparately() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3)));
		when(repository.findByPlatform(Platform.IOS))
				.thenReturn(Optional.of(config("ios", "2.0.0", null, false, Map.of(), 9)));

		assertEquals("1.2.0", service.get(Platform.ANDROID, null, null).response().getMinimumVersion());
		Result ios = service.get(Platform.IOS, null, null);
		assertEquals("2.0.0", ios.response().getMinimumVersion());
		assertEquals("\"ios-v9\"", ios.eTag());
	}

	@Test
	public void appVersionBelowMinimumRequiresAnUpdate() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3)));

		Result result = service.get(Platform.ANDROID, "1.1.9", null);

		assertTrue(result.response().getUpdateRequired());
		assertTrue(result.response().getUpdateAvailable());
		assertEquals("\"android-v3-u11\"", result.eTag());
	}

	@Test
	public void versionsAreComparedAsNumbers() {
		when(repository.findByPlatform(Platform.ANDROID))
				.thenReturn(Optional.of(config("android", "1.9.0", "1.10.0", false, Map.of(), 1)));

		// As text "1.10.0" < "1.9.0"; as versions it's newer.
		Result result = service.get(Platform.ANDROID, "1.10.0", null);

		assertFalse(result.response().getUpdateRequired());
		assertFalse(result.response().getUpdateAvailable());
	}

	@Test
	public void appVersionBetweenMinimumAndLatestOnlySuggestsAnUpdate() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3)));

		Result result = service.get(Platform.ANDROID, "1.3", null);

		assertFalse(result.response().getUpdateRequired());
		assertTrue(result.response().getUpdateAvailable());
		assertEquals("\"android-v3-u01\"", result.eTag());
	}

	@Test
	public void noLatestVersionMeansNoUpdateAvailable() {
		when(repository.findByPlatform(Platform.IOS))
				.thenReturn(Optional.of(config("ios", "2.0.0", null, false, Map.of(), 1)));

		Result result = service.get(Platform.IOS, "2.0.0", null);

		assertFalse(result.response().getUpdateRequired());
		assertFalse(result.response().getUpdateAvailable());
	}

	@Test
	public void maintenanceMessageFollowsAcceptLanguage() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(config("android", "1.0", null, true,
				messages("eng", "Down for maintenance", "ara", "صيانة"), 4)));

		Result arabic = service.get(Platform.ANDROID, null, "ar, en;q=0.8");
		Result english = service.get(Platform.ANDROID, null, "en-US");

		assertTrue(arabic.response().getMaintenance().isEnabled());
		assertEquals("صيانة", arabic.response().getMaintenance().getMessage());
		assertEquals("2026-11-25T06:00:00.000Z", arabic.response().getMaintenance().getEndsAt());
		assertEquals("Down for maintenance", english.response().getMaintenance().getMessage());
		assertNotEquals("language changes the body, so the ETag", arabic.eTag(), english.eTag());
	}

	@Test
	public void acceptLanguageWeightsAreRespected() {
		Map<String, String> messages = messages("eng", "English", "ara", "Arabic");

		assertEquals("eng", service.language(messages, "ar;q=0.2, en;q=0.9"));
		assertEquals("ara", service.language(messages, "fr, ara"));
	}

	@Test
	public void unknownOrMissingLanguageFallsBackToTheDefaultThenAnyMessage() {
		assertEquals("eng", service.language(messages("ara", "Arabic", "eng", "English"), "fr-FR"));
		assertEquals("eng", service.language(messages("ara", "Arabic", "eng", "English"), null));
		assertEquals("eng", service.language(messages("ara", "Arabic", "eng", "English"), "not a ;; header"));
		assertEquals("eng", service.language(messages("ara", "Arabic", "eng", "English"), "*"));
		assertEquals("ara", service.language(messages("ara", "Arabic"), "fr"));
		assertNull(service.language(Map.of(), "en"));
	}

	@Test
	public void configIsCachedForTheTtl() {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3)),
				Optional.of(android(4)));

		service.get(Platform.ANDROID, null, null);
		clock.advance(Duration.ofSeconds(29));
		assertEquals("\"android-v3\"", service.get(Platform.ANDROID, null, null).eTag());
		verify(repository, times(1)).findByPlatform(Platform.ANDROID);

		clock.advance(Duration.ofSeconds(1));
		assertEquals("\"android-v4\"", service.get(Platform.ANDROID, null, null).eTag());
		verify(repository, times(2)).findByPlatform(Platform.ANDROID);
	}

	@Test
	public void databaseDownServesTheLastCopy() {
		AppConfigRecord last = android(3);
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(last))
				.thenThrow(new DataAccessResourceFailureException("down"));

		service.get(Platform.ANDROID, null, null);
		clock.advance(Duration.ofMinutes(5));
		Result result = service.get(Platform.ANDROID, null, null);

		assertEquals("\"android-v3\"", result.eTag());
		// Not retried on every request while the database is down: only after another TTL.
		service.get(Platform.ANDROID, null, null);
		verify(repository, times(2)).findByPlatform(Platform.ANDROID);
	}

	@Test
	public void databaseDownWithNoCopyIsEnrGen003() {
		when(repository.findByPlatform(Platform.IOS)).thenThrow(new DataAccessResourceFailureException("down"));

		assertGen003(() -> service.get(Platform.IOS, null, null));
	}

	@Test
	public void failedReadWithNoCopyIsRememberedForTheTtl() {
		when(repository.findByPlatform(Platform.IOS)).thenThrow(new DataAccessResourceFailureException("down"))
				.thenReturn(Optional.of(config("ios", "2.0.0", null, false, Map.of(), 1)));

		assertGen003(() -> service.get(Platform.IOS, null, null));
		assertGen003(() -> service.get(Platform.IOS, null, null));
		verify(repository, times(1)).findByPlatform(Platform.IOS);

		clock.advance(Duration.ofSeconds(30));
		assertEquals("\"ios-v1\"", service.get(Platform.IOS, null, null).eTag());
	}

	/** While one request is reading (slowly) from the database, the others get the old copy without waiting. */
	@Test
	public void onlyOneRequestReadsAtATime() throws Exception {
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(android(3))).thenAnswer(call -> {
			reading.countDown();
			release.await(10, TimeUnit.SECONDS);
			return Optional.of(android(4));
		});
		service.get(Platform.ANDROID, null, null);
		clock.advance(Duration.ofSeconds(30));

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<Result> slow = executor.submit(() -> service.get(Platform.ANDROID, null, null));
			assertTrue(reading.await(10, TimeUnit.SECONDS));

			assertEquals("\"android-v3\"", service.get(Platform.ANDROID, null, null).eTag());
			verify(repository, times(2)).findByPlatform(Platform.ANDROID);

			release.countDown();
			assertEquals("\"android-v4\"", slow.get(10, TimeUnit.SECONDS).eTag());
			assertEquals("\"android-v4\"", service.get(Platform.ANDROID, null, null).eTag());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	public void missingRowIsEnrGen003() {
		when(repository.findByPlatform(Platform.IOS)).thenReturn(Optional.empty());

		assertGen003(() -> service.get(Platform.IOS, null, null));
	}

	/** Before the rows are seeded, the requests behind the first one answer at once, without a query each. */
	@Test
	public void missingRowIsRememberedForTheTtl() {
		when(repository.findByPlatform(Platform.IOS)).thenReturn(Optional.empty())
				.thenReturn(Optional.of(config("ios", "2.0.0", null, false, Map.of(), 1)));

		assertGen003(() -> service.get(Platform.IOS, null, null));
		assertGen003(() -> service.get(Platform.IOS, null, null));
		verify(repository, times(1)).findByPlatform(Platform.IOS);

		// Once the row is added, it is picked up after the TTL.
		clock.advance(Duration.ofSeconds(30));
		assertEquals("\"ios-v1\"", service.get(Platform.IOS, null, null).eTag());
	}

	private static void assertGen003(Runnable call) {
		try {
			call.run();
			fail("expected ENR-GEN-003");
		} catch (EnrolmentException e) {
			assertSame(EnrolmentErrorCode.GEN_003, e.getErrorCode());
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
		}
	}

	/** A clock the test moves forward. */
	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
