package io.mosip.idrepository.paas.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.appconfig.AppConfigResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/** The matching rules from MP-247: exact and case-insensitive, OS versions compared as numbers. */
public class DevicePolicyCheckerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final AppConfigService appConfig = mock(AppConfigService.class);
	private final DevicePolicyChecker checker = new DevicePolicyChecker(appConfig);

	private void policy(Platform platform, String json) throws Exception {
		AppConfigResponse response = new AppConfigResponse();
		JsonNode node = json == null ? null : MAPPER.readTree(json);
		response.setDevicePolicy(node);
		when(appConfig.get(any(Platform.class), isNull(), isNull()))
				.thenReturn(new AppConfigService.Result(response, "\"e\""));
	}

	private static void assertUnsupported(Runnable check) {
		EnrolmentException e = assertThrows(EnrolmentException.class, check::run);
		assertEquals(EnrolmentErrorCode.DEV_001, e.getErrorCode());
		assertEquals(HttpStatus.OK, e.getHttpStatus());
	}

	// ---- OS version

	@Test
	public void osVersionsAreComparedAsNumbersNotText() throws Exception {
		policy(Platform.IOS, "{\"policyVersion\":\"p1\",\"minOsVersion\":\"17.10\",\"blockedModels\":[]}");
		assertUnsupported(() -> checker.check(Platform.IOS, "17.4", null, "iPhone16,1"));
		assertEquals("p1", checker.check(Platform.IOS, "17.10", null, "iPhone16,1"));
		assertEquals("p1", checker.check(Platform.IOS, "17.11", null, "iPhone16,1"));

		policy(Platform.ANDROID, "{\"policyVersion\":\"p2\",\"minOsVersion\":\"9\",\"blockedModels\":[]}");
		assertEquals("p2", checker.check(Platform.ANDROID, "10", "Google", "Pixel 8"));

		policy(Platform.ANDROID, "{\"policyVersion\":\"p3\",\"minOsVersion\":\"10\",\"blockedModels\":[]}");
		assertUnsupported(() -> checker.check(Platform.ANDROID, "9", "Google", "Pixel 8"));
	}

	@Test
	public void theMinimumVersionItselfIsAllowedAndMissingPartsCountAsZero() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"p\",\"minOsVersion\":\"10\",\"blockedModels\":[]}");
		assertEquals("p", checker.check(Platform.ANDROID, "10", "Google", "Pixel 8"));
		assertEquals("p", checker.check(Platform.ANDROID, "10.0", "Google", "Pixel 8"));
		assertEquals("p", checker.check(Platform.ANDROID, "10.0.0", "Google", "Pixel 8"));
		assertUnsupported(() -> checker.check(Platform.ANDROID, "9.99", "Google", "Pixel 8"));
	}

	@Test
	public void anUnusableMinimumVersionFailsClosedInsteadOfSwitchingTheCheckOff() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"p\",\"minOsVersion\":\"Android 10\",\"blockedModels\":[]}");

		EnrolmentException e = assertThrows(EnrolmentException.class,
				() -> checker.check(Platform.ANDROID, "14", "Google", "Pixel 8"));

		assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
	}

	// ---- Blocked models

	@Test
	public void androidMatchesManufacturerAndModelIgnoringCase() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"p\",\"minOsVersion\":\"10\",\"blockedModels\":"
				+ "[{\"manufacturer\":\"Samsung\",\"model\":\"SM-A105F\",\"reasonCode\":\"NFC_UNRELIABLE\"}]}");

		assertUnsupported(() -> checker.check(Platform.ANDROID, "14", "samsung", "sm-a105f"));
		assertUnsupported(() -> checker.check(Platform.ANDROID, "14", " SAMSUNG ", "SM-A105F "));
	}

	@Test
	public void aSimilarButDifferentModelOrManufacturerIsNotBlocked() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"p\",\"minOsVersion\":\"10\",\"blockedModels\":"
				+ "[{\"manufacturer\":\"Samsung\",\"model\":\"SM-A105F\",\"reasonCode\":\"NO_SECURE_HW\"}]}");

		assertEquals("p", checker.check(Platform.ANDROID, "14", "Samsung", "SM-A105FN"));
		assertEquals("p", checker.check(Platform.ANDROID, "14", "Samsung", "SM-A105"));
		assertEquals("p", checker.check(Platform.ANDROID, "14", "Xiaomi", "SM-A105F"));
		assertEquals("p", checker.check(Platform.ANDROID, "14", null, "SM-A105F"));
	}

	@Test
	public void anEntryWithoutManufacturerBlocksTheModelFromAnyManufacturer() throws Exception {
		policy(Platform.ANDROID,
				"{\"policyVersion\":\"p\",\"blockedModels\":[{\"model\":\"Pixel 3\",\"reasonCode\":\"X\"}]}");

		assertUnsupported(() -> checker.check(Platform.ANDROID, "14", "Google", "pixel 3"));
	}

	@Test
	public void iosMatchesTheHardwareIdentifierOnly() throws Exception {
		policy(Platform.IOS, "{\"policyVersion\":\"p\",\"minOsVersion\":\"16\",\"blockedModels\":"
				+ "[{\"manufacturer\":\"Apple\",\"model\":\"iPhone10,1\",\"reasonCode\":\"NO_SECURE_HW\"}]}");

		assertUnsupported(() -> checker.check(Platform.IOS, "17.4", null, "iphone10,1"));
		assertEquals("p", checker.check(Platform.IOS, "17.4", null, "iPhone10,2"));
	}

	@Test
	public void entriesWithoutAModelAreIgnored() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"p\",\"blockedModels\":[{\"manufacturer\":\"Samsung\"},{}, 5]}");

		assertEquals("p", checker.check(Platform.ANDROID, "14", "Samsung", "SM-A105F"));
	}

	// ---- No policy

	@Test
	public void aPlatformWithoutAPolicyHasNoRestrictions() throws Exception {
		policy(Platform.ANDROID, null);
		assertNull(checker.check(Platform.ANDROID, "1", "Anyone", "Anything"));

		policy(Platform.ANDROID, "{}");
		assertNull(checker.check(Platform.ANDROID, "1", "Anyone", "Anything"));
	}

	@Test
	public void theStoredPolicyVersionIsReturnedForTheDeviceRecord() throws Exception {
		policy(Platform.ANDROID, "{\"policyVersion\":\"2026-11-24\",\"minOsVersion\":\"10\",\"blockedModels\":[]}");

		assertEquals("2026-11-24", checker.check(Platform.ANDROID, "14", "Google", "Pixel 8"));
	}

	// ---- app_config unavailable

	@Test
	public void failsClosedWhenTheConfigCantBeRead() {
		when(appConfig.get(any(Platform.class), isNull(), isNull())).thenThrow(
				new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));

		EnrolmentException e = assertThrows(EnrolmentException.class,
				() -> checker.check(Platform.ANDROID, "14", "Google", "Pixel 8"));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
	}
}
