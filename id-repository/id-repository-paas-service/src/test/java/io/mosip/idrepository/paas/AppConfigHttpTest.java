package io.mosip.idrepository.paas;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.repository.AppConfigRecord;
import io.mosip.idrepository.paas.repository.AppConfigRepository;

/**
 * {@code GET /app/config} through the whole application (MP-243): no session needed, the envelope, the
 * caching headers and the spec's error codes. Only the database read is mocked. The service's cache is
 * turned off here; {@code AppConfigServiceImplTest} covers it.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
				+ "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
		// No caching between requests, so each test sees its own stubbed row.
		"mosip.idpaas.app-config.cache-ttl=0s" })
@AutoConfigureMockMvc
public class AppConfigHttpTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Autowired
	private MockMvc mvc;

	@MockBean
	private AppConfigRepository repository;

	private static AppConfigRecord config(String platform, boolean maintenance, int configVersion) throws Exception {
		return new AppConfigRecord(platform, "1.2.0", "1.4.1", "https://store.example/" + platform, maintenance,
				maintenance ? Map.of("eng", "Down for maintenance") : Map.of(),
				maintenance ? LocalDateTime.of(2026, 11, 25, 6, 0) : null,
				MAPPER.readTree("{\"showUin\":false,\"emailSkipAllowed\":true}"),
				MAPPER.readTree("{\"policyVersion\":\"2026-11-01\",\"minOsVersion\":\"10\",\"blockedModels\":[]}"),
				configVersion);
	}

	@Test
	public void configIsReturnedWithoutASession() throws Exception {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(config("android", false, 7)));

		mvc.perform(get("/app/config").param("platform", "android").param("appVersion", "1.1.0"))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "\"android-v7-u11\""))
				.andExpect(header().string("Cache-Control", containsString("max-age=60")))
				.andExpect(header().string("Cache-Control", containsString("public")))
				.andExpect(header().string("Vary", containsString("Accept-Language")))
				.andExpect(jsonPath("$.id").value("mosip.enrolment.app.config"))
				.andExpect(jsonPath("$.version").value("1.0"))
				.andExpect(jsonPath("$.errors").isEmpty())
				.andExpect(jsonPath("$.response.platform").value("android"))
				.andExpect(jsonPath("$.response.minimumVersion").value("1.2.0"))
				.andExpect(jsonPath("$.response.latestVersion").value("1.4.1"))
				.andExpect(jsonPath("$.response.storeUrl").value("https://store.example/android"))
				.andExpect(jsonPath("$.response.updateRequired").value(true))
				.andExpect(jsonPath("$.response.updateAvailable").value(true))
				.andExpect(jsonPath("$.response.maintenance.enabled").value(false))
				.andExpect(jsonPath("$.response.features.emailSkipAllowed").value(true))
				.andExpect(jsonPath("$.response.devicePolicy.minOsVersion").value("10"));
	}

	@Test
	public void withoutAppVersionTheUpdateFlagsAreLeftOut() throws Exception {
		when(repository.findByPlatform(Platform.IOS)).thenReturn(Optional.of(config("ios", false, 2)));

		mvc.perform(get("/app/config").param("platform", "IOS"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response.platform").value("ios"))
				.andExpect(jsonPath("$.response.updateRequired").doesNotExist())
				.andExpect(jsonPath("$.response.updateAvailable").doesNotExist());
	}

	@Test
	public void unchangedConfigIs304() throws Exception {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(config("android", false, 7)));

		mvc.perform(get("/app/config").param("platform", "android").header("If-None-Match", "\"android-v7\""))
				.andExpect(status().isNotModified())
				.andExpect(header().string("ETag", "\"android-v7\""))
				.andExpect(content().string(""));
		mvc.perform(get("/app/config").param("platform", "android").header("If-None-Match", "\"android-v6\""))
				.andExpect(status().isOk());
	}

	@Test
	public void unknownOrMissingPlatformIsEnrGen001() throws Exception {
		mvc.perform(get("/app/config").param("platform", "windows"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.app.config"))
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.GEN_001.getMessage()));
		mvc.perform(get("/app/config"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}

	@Test
	public void appVersionThatIsNotADottedNumberIsEnrGen001() throws Exception {
		mvc.perform(get("/app/config").param("platform", "android").param("appVersion", "v1.3"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}

	@Test
	public void platformWithNoConfigRowIsEnrGen003() throws Exception {
		when(repository.findByPlatform(Platform.IOS)).thenReturn(Optional.empty());

		mvc.perform(get("/app/config").param("platform", "ios"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.app.config"))
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}

	@Test
	public void maintenanceMessageIsInTheResponse() throws Exception {
		when(repository.findByPlatform(Platform.ANDROID)).thenReturn(Optional.of(config("android", true, 50)));

		mvc.perform(get("/app/config").param("platform", "android").header("Accept-Language", "en"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response.maintenance.enabled").value(true))
				.andExpect(jsonPath("$.response.maintenance.message").value("Down for maintenance"))
				.andExpect(jsonPath("$.response.maintenance.endsAt").value("2026-11-25T06:00:00.000Z"));
	}

	@Test
	public void otherEndpointsStillNeedASession() throws Exception {
		mvc.perform(get("/app/config/../passport"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-001"));
	}
}
