package io.mosip.idrepository.paas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.repository.AppConfigRecord;
import io.mosip.idrepository.paas.repository.AppConfigRepository;
import io.mosip.idrepository.paas.repository.DeviceRecord;
import io.mosip.idrepository.paas.repository.DeviceRepository;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier;
import io.mosip.idrepository.paas.spi.DeviceAttestationVerifier.AttestationResult;

/**
 * {@code POST /device/register} through the whole application (MP-247): no session needed, the envelope and
 * the error codes. The attestation verifier and both database repositories are mocked.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
				+ "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
		"mosip.idpaas.app-config.cache-ttl=0s" })
@AutoConfigureMockMvc
public class DeviceRegisterHttpTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mvc;

	@MockBean
	private DeviceAttestationVerifier verifier;

	@MockBean
	private DeviceRepository devices;

	@MockBean
	private AppConfigRepository appConfig;

	private static String body(String id, String platform, String osVersion) {
		return "{\"id\":\"" + id + "\",\"version\":\"1.0\",\"requesttime\":\"2026-11-25T10:00:00.000Z\","
				+ "\"request\":{\"platform\":\"" + platform + "\",\"osVersion\":\"" + osVersion + "\","
				+ "\"manufacturer\":\"Google\",\"model\":\"Pixel 8\",\"appVersion\":\"1.3.0\","
				+ "\"publicKey\":{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs\","
				+ "\"y\":\"9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA\"},\"attestation\":{\"challengeId\":\"c1\"}}}";
	}

	private void policy(String devicePolicyJson) throws Exception {
		when(appConfig.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(new AppConfigRecord("android", "0.0.0",
				null, "https://store.example", false, Map.of(), null, MAPPER.readTree("{}"),
				MAPPER.readTree(devicePolicyJson), 1)));
	}

	private void attestationPasses() {
		when(verifier.verify(any(), any(), anyString(), any()))
				.thenReturn(new AttestationResult("TEE", null, Map.of("integrityLevel", "TEE")));
	}

	@Test
	public void registersWithoutASession() throws Exception {
		attestationPasses();
		policy("{\"policyVersion\":\"p1\",\"minOsVersion\":\"10\",\"blockedModels\":[]}");

		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON)
				.content(body("mosip.enrolment.device.register", "android", "14")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.device.register"))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andExpect(jsonPath("$.response.deviceId").value(org.hamcrest.Matchers.startsWith("dev_")));
	}

	@Test
	public void anOldOsIsRejectedWithEnrDev001AndNothingIsStored() throws Exception {
		attestationPasses();
		policy("{\"policyVersion\":\"p1\",\"minOsVersion\":\"15\",\"blockedModels\":[]}");

		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON)
				.content(body("mosip.enrolment.device.register", "android", "14")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response").doesNotExist())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-DEV-001"));
		org.mockito.Mockito.verify(devices, org.mockito.Mockito.never()).insert(any());
	}

	@Test
	public void aRevokedKeyLooksLikeAnyRegisteredKeyEnrDev002() throws Exception {
		attestationPasses();
		policy("{\"policyVersion\":\"p1\",\"minOsVersion\":\"10\",\"blockedModels\":[]}");
		doThrow(new DuplicateKeyException("uk_device_jkt")).when(devices).insert(any());
		when(devices.findByJkt(anyString())).thenReturn(Optional.of(new DeviceRecord("dev_old", "j", "{}", null,
				"android", "14", "Google", "Pixel 8", "1.3.0", null, "{}", "REVOKED", LocalDateTime.now())));

		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON)
				.content(body("mosip.enrolment.device.register", "android", "14")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-DEV-002"));
	}

	@Test
	public void failedAttestationIsEnrDev004() throws Exception {
		when(verifier.verify(any(), any(), anyString(), any()))
				.thenThrow(new EnrolmentException(EnrolmentErrorCode.DEV_004, "bad"));

		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON)
				.content(body("mosip.enrolment.device.register", "android", "14")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-DEV-004"));
	}

	@Test
	public void aWrongEnvelopeIdIsEnrGen001() throws Exception {
		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON)
				.content(body("mosip.enrolment.passport", "android", "14")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.device.register"))
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}

	@Test
	public void notJsonIsEnrGen001() throws Exception {
		mvc.perform(post("/device/register").contentType(MediaType.APPLICATION_JSON).content("{nope"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}
}
