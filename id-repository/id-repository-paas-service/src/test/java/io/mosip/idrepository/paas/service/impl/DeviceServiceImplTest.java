package io.mosip.idrepository.paas.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.dto.device.AttestationEvidence;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterRequest;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterResponse;
import io.mosip.idrepository.paas.dto.device.PublicKeyJwk;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.repository.DeviceRecord;
import io.mosip.idrepository.paas.repository.DeviceRepository;
import io.mosip.idrepository.paas.service.DevicePolicyChecker;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier.AttestationResult;

public class DeviceServiceImplTest {

	private static final String X = "l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs";
	private static final String Y = "9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA";
	private static final String JKT = "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I";

	private final DeviceAttestationVerifier verifier = mock(DeviceAttestationVerifier.class);
	private final DevicePolicyChecker policy = mock(DevicePolicyChecker.class);
	private final DeviceRepository repository = mock(DeviceRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
	private final DeviceServiceImpl service = new DeviceServiceImpl(verifier, policy, repository,
			new ObjectMapper(), clock);

	@Before
	public void passingDefaults() {
		when(verifier.verify(any(), any(), anyString(), any()))
				.thenReturn(new AttestationResult("TEE", null, Map.of("integrityLevel", "TEE")));
		when(policy.check(any(), anyString(), any(), anyString())).thenReturn("2026-11-24");
	}

	private static DeviceRegisterRequest android() {
		DeviceRegisterRequest request = new DeviceRegisterRequest();
		request.setPlatform("android");
		request.setOsVersion("14");
		request.setManufacturer("Google");
		request.setModel("Pixel 8");
		request.setAppVersion("1.3.0");
		PublicKeyJwk jwk = new PublicKeyJwk();
		jwk.setKty("EC");
		jwk.setCrv("P-256");
		jwk.setX(X);
		jwk.setY(Y);
		request.setPublicKey(jwk);
		request.setAttestation(new AttestationEvidence());
		return request;
	}

	private static DeviceRegisterRequest ios() {
		DeviceRegisterRequest request = android();
		request.setPlatform("ios");
		request.setOsVersion("17.4");
		request.setManufacturer("Apple");
		request.setModel("iPhone16,1");
		return request;
	}

	private static DeviceRecord existing(String status) {
		return new DeviceRecord("dev_old", JKT, "{}", null, "android", "14", "Google", "Pixel 8", "1.3.0", null, "{}",
				status, LocalDateTime.of(2026, 11, 1, 0, 0));
	}

	private EnrolmentErrorCode codeOf(Runnable register) {
		return assertThrows(EnrolmentException.class, register::run).getErrorCode();
	}

	// ---- success

	@Test
	public void validAttestationRegistersTheDeviceAndReturnsItsId() {
		DeviceRegisterResponse response = service.register(android());

		ArgumentCaptor<DeviceRecord> stored = ArgumentCaptor.forClass(DeviceRecord.class);
		verify(repository).insert(stored.capture());
		DeviceRecord device = stored.getValue();
		assertEquals(device.deviceId(), response.getDeviceId());
		assertTrue(device.deviceId(), device.deviceId().matches("dev_\\d{13}_[0-9a-f]{8}"));
		assertEquals(JKT, device.jkt());
		assertEquals("android", device.platform());
		assertEquals("14", device.osVersion());
		assertEquals("Google", device.manufacturer());
		assertEquals("Pixel 8", device.model());
		assertEquals("1.3.0", device.appVersion());
		assertEquals("2026-11-24", device.policyVersion());
		assertEquals("{\"integrityLevel\":\"TEE\"}", device.attestationSummary());
		assertEquals(DeviceRecord.ACTIVE, device.statusCode());
		assertNull(device.appAttestKeyId());
		assertEquals(LocalDateTime.of(2026, 11, 25, 10, 0), device.createdAt());
	}

	@Test
	public void iosStoresTheVerifiedAppAttestKeyIdAndNoManufacturer() {
		when(verifier.verify(eq(Platform.IOS), any(), anyString(), any()))
				.thenReturn(new AttestationResult("APP_ATTEST", "KEYID_1", Map.of()));

		service.register(ios());

		ArgumentCaptor<DeviceRecord> stored = ArgumentCaptor.forClass(DeviceRecord.class);
		verify(repository).insert(stored.capture());
		assertEquals("KEYID_1", stored.getValue().appAttestKeyId());
		assertNull(stored.getValue().manufacturer());
	}

	@Test
	public void theVerifierGetsTheKeyAndItsThumbprint() {
		service.register(android());

		verify(verifier).verify(eq(Platform.ANDROID), any(PublicKeyJwk.class), eq(JKT), any(AttestationEvidence.class));
	}

	// ---- order: attestation, then policy, then insert

	@Test
	public void aFailedAttestationStopsEverythingElse() {
		when(verifier.verify(any(), any(), anyString(), any()))
				.thenThrow(new EnrolmentException(EnrolmentErrorCode.DEV_004, "bad chain"));

		assertEquals(EnrolmentErrorCode.DEV_004, codeOf(() -> service.register(android())));

		verify(policy, never()).check(any(), anyString(), any(), anyString());
		verify(repository, never()).insert(any());
	}

	@Test
	public void aBlockedDeviceIsNotStored() {
		when(policy.check(any(), anyString(), any(), anyString()))
				.thenThrow(new EnrolmentException(EnrolmentErrorCode.DEV_001, "OS_TOO_OLD"));

		assertEquals(EnrolmentErrorCode.DEV_001, codeOf(() -> service.register(android())));

		verify(repository, never()).insert(any());
	}

	@Test
	public void thePolicyIsCheckedWithTheReportedValues() {
		service.register(android());

		verify(policy).check(Platform.ANDROID, "14", "Google", "Pixel 8");
	}

	@Test
	public void anUnreadablePolicyIsA503AndNothingIsStored() {
		when(policy.check(any(), anyString(), any(), anyString())).thenThrow(
				new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE, "unavailable"));

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(android()));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
		verify(repository, never()).insert(any());
	}

	// ---- B1: duplicates

	@Test
	public void theSameKeyAgainIsRejected() {
		doThrow(new DuplicateKeyException("uk_device_jkt")).when(repository).insert(any());
		when(repository.findByJkt(JKT)).thenReturn(Optional.of(existing(DeviceRecord.ACTIVE)));

		assertEquals(EnrolmentErrorCode.DEV_002, codeOf(() -> service.register(android())));
	}

	@Test
	public void aRevokedKeyGetsTheSameCodeButTheLogSaysItWasRevoked() {
		doThrow(new DuplicateKeyException("uk_device_jkt")).when(repository).insert(any());
		when(repository.findByJkt(JKT)).thenReturn(Optional.of(existing(DeviceRecord.REVOKED)));

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(android()));

		// The caller can't tell a revoked key from a registered one; the log message can.
		assertEquals(EnrolmentErrorCode.DEV_002, e.getErrorCode());
		assertEquals(HttpStatus.OK, e.getHttpStatus());
		assertTrue(e.getMessage(), e.getMessage().contains("revoked"));
	}

	@Test
	public void anActiveDuplicateIsLoggedAsActive() {
		doThrow(new DuplicateKeyException("uk_device_jkt")).when(repository).insert(any());
		when(repository.findByJkt(JKT)).thenReturn(Optional.of(existing(DeviceRecord.ACTIVE)));

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(android()));

		assertTrue(e.getMessage(), e.getMessage().contains("active"));
	}

	// ---- OS version: attested when the attestation has one

	@Test
	public void theAttestedOsVersionIsUsedForThePolicyAndStored() {
		when(verifier.verify(any(), any(), anyString(), any())).thenReturn(
				new AttestationResult("TEE", null, Map.of(), "9"));

		service.register(android()); // the phone reports 14, the attestation proves 9

		verify(policy).check(Platform.ANDROID, "9", "Google", "Pixel 8");
		ArgumentCaptor<DeviceRecord> stored = ArgumentCaptor.forClass(DeviceRecord.class);
		verify(repository).insert(stored.capture());
		assertEquals("9", stored.getValue().osVersion());
	}

	@Test
	public void withoutAnAttestedOsVersionTheReportedOneIsUsed() {
		service.register(android());

		verify(policy).check(Platform.ANDROID, "14", "Google", "Pixel 8");
	}

	@Test
	public void anAttestedOsVersionThatIsNotADottedNumberIsAVerifierFault() {
		when(verifier.verify(any(), any(), anyString(), any())).thenReturn(
				new AttestationResult("TEE", null, Map.of(), "140000x"));

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(android()));

		assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
		verify(policy, never()).check(any(), anyString(), any(), anyString());
		verify(repository, never()).insert(any());
	}

	@Test
	public void anOldAttestedVersionIsRejectedEvenIfThePhoneReportsANewOne() {
		when(verifier.verify(any(), any(), anyString(), any())).thenReturn(
				new AttestationResult("TEE", null, Map.of(), "9"));
		when(policy.check(any(), eq("9"), any(), anyString()))
				.thenThrow(new EnrolmentException(EnrolmentErrorCode.DEV_001, "OS_TOO_OLD"));

		assertEquals(EnrolmentErrorCode.DEV_001, codeOf(() -> service.register(android())));

		verify(repository, never()).insert(any());
	}

	@Test
	public void theSameAppAttestKeyIdWithAnotherKeyIsRejected() {
		when(verifier.verify(eq(Platform.IOS), any(), anyString(), any()))
				.thenReturn(new AttestationResult("APP_ATTEST", "KEYID_1", Map.of()));
		doThrow(new DuplicateKeyException("uk_device_attest_key")).when(repository).insert(any());
		when(repository.findByJkt(anyString())).thenReturn(Optional.empty());
		when(repository.findByAppAttestKeyId("KEYID_1")).thenReturn(Optional.of(existing(DeviceRecord.ACTIVE)));

		assertEquals(EnrolmentErrorCode.DEV_002, codeOf(() -> service.register(ios())));
	}

	@Test
	public void aDuplicateIsStillRejectedIfTheFollowUpReadFails() {
		doThrow(new DuplicateKeyException("uk_device_jkt")).when(repository).insert(any());
		when(repository.findByJkt(JKT)).thenThrow(new QueryTimeoutException("slow"));

		assertEquals(EnrolmentErrorCode.DEV_002, codeOf(() -> service.register(android())));
	}

	@Test
	public void aDatabaseFailureIsA503() {
		doThrow(new QueryTimeoutException("slow")).when(repository).insert(any());

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(android()));

		assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
	}

	// ---- bad requests: ENR-GEN-001, nothing else is called

	private void assertRejectedAsMalformed(DeviceRegisterRequest request) {
		assertEquals(EnrolmentErrorCode.GEN_001, codeOf(() -> service.register(request)));
		verify(verifier, never()).verify(any(), any(), anyString(), any());
		verify(repository, never()).insert(any());
	}

	@Test
	public void malformedRequestsAreRejectedBeforeAnyCheck() {
		assertRejectedAsMalformed(null);

		DeviceRegisterRequest request = android();
		request.setPlatform("windows");
		assertRejectedAsMalformed(request);

		request = android();
		request.setOsVersion("Android 14");
		assertRejectedAsMalformed(request);

		request = android();
		request.setOsVersion(null);
		assertRejectedAsMalformed(request);

		request = android();
		request.setAppVersion("latest");
		assertRejectedAsMalformed(request);

		request = android();
		request.setModel(" ");
		assertRejectedAsMalformed(request);

		request = android();
		request.setModel("x".repeat(129));
		assertRejectedAsMalformed(request);

		request = android();
		request.setManufacturer(null);
		assertRejectedAsMalformed(request);

		request = android();
		request.setAttestation(null);
		assertRejectedAsMalformed(request);

		request = android();
		request.setPublicKey(null);
		assertRejectedAsMalformed(request);

		request = android();
		request.getPublicKey().setD("c2VjcmV0");
		assertRejectedAsMalformed(request);
	}

	@Test
	public void iosNeedsNoManufacturer() {
		DeviceRegisterRequest request = ios();
		request.setManufacturer(null);
		when(verifier.verify(eq(Platform.IOS), any(), anyString(), any()))
				.thenReturn(new AttestationResult("APP_ATTEST", "KEYID_1", Map.of()));

		service.register(request);

		verify(repository).insert(any());
	}

	@Test
	public void iosWithoutAVerifiedKeyIdIsAServerFault() {
		when(verifier.verify(eq(Platform.IOS), any(), anyString(), any()))
				.thenReturn(new AttestationResult("APP_ATTEST", null, Map.of()));

		EnrolmentException e = assertThrows(EnrolmentException.class, () -> service.register(ios()));

		assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
		verify(repository, never()).insert(any());
	}
}
