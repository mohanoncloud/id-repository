package io.mosip.idrepository.paas;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;

/**
 * {@code POST /passport} through the whole application: session filter, controller, MRZ read, expiry check
 * and the error envelope, with the real config binding. Only the OCR is faked (it returns MRZ text), because
 * the OCR library isn't chosen yet (MP-397). Spring Security's default chain is excluded as in
 * {@link SwaggerTest}: in an environment the kernel auth adapter lets these paths through.
 *
 * <p>The clock is fixed at 23:59:59 on 18 Nov 2026 in Nassau, which is already 19 Nov in UTC.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
		+ "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration")
@AutoConfigureMockMvc
public class PassportScanHttpTest {

	/** ICAO 9303 specimen; expired on 15 April 2012. */
	private static final String SPECIMEN = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n"
			+ "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
	/** Synthetic Bahamas passports (made-up data), expiring on 18 and 17 Nov 2026. */
	private static final String BHS_LINE1 = "P<BHSSMITH<<JOHN<PAUL<<<<<<<<<<<<<<<<<<<<<<<";
	private static final String EXPIRES_TODAY_IN_NASSAU = BHS_LINE1 + "\n" + "AB12345671BHS9001158M2611181<<<<<<<<<<<<<<<8";
	private static final String EXPIRED_YESTERDAY_IN_NASSAU = BHS_LINE1 + "\n" + "AB12345671BHS9001158M2611170<<<<<<<<<<<<<<<4";

	private static final String VALID_REQUEST = "{\"id\":\"mosip.enrolment.passport\",\"version\":\"1.0\","
			+ "\"requesttime\":\"2026-11-18T10:18:05.000Z\",\"request\":{\"additionalDetails\":{\"address\":"
			+ "{\"addressLine1\":\"12 Shirley Street\",\"city\":\"Nassau\",\"province\":\"New Providence\","
			+ "\"region\":\"New Providence\",\"zone\":\"Centreville\"}}}}";

	@TestConfiguration
	static class FixedClock {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-11-19T04:59:59Z"), ZoneOffset.UTC);
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private PassportProperties passportProperties;

	@MockBean
	private PassportImageReader imageReader;

	@Test
	public void expiryRuleComesFromConfig() {
		assertEquals("America/Nassau", passportProperties.getExpiryZone());
		assertEquals(0, passportProperties.getExpiryGraceDays());
	}

	@Test
	public void expiredPassportIsEnrPsp008WithTheSpecMessage() throws Exception {
		scan(SPECIMEN).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.passport"))
				.andExpect(jsonPath("$.response").doesNotExist())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-008"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_008.getMessage()));
	}

	@Test
	public void passportThatExpiredYesterdayInNassauIsEnrPsp008() throws Exception {
		scan(EXPIRED_YESTERDAY_IN_NASSAU).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-008"));
	}

	@Test
	public void passportExpiringTodayInNassauIsAcceptedAlthoughUtcIsTheNextDay() throws Exception {
		// Passes the expiry check and reaches the steps not built yet (MP-257 / MP-259).
		scan(EXPIRES_TODAY_IN_NASSAU).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}

	@Test
	public void badCheckDigitIsEnrPsp003BeforeTheExpiryCheck() throws Exception {
		String badCompositeDigit = SPECIMEN.substring(0, SPECIMEN.length() - 1) + "9";
		scan(badCompositeDigit).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-003"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_003.getMessage()));
	}

	@Test
	public void noMrzFoundIsEnrPsp003() throws Exception {
		scan(null).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-003"));
	}

	@Test
	public void visaIsEnrPsp004() throws Exception {
		scan("V<" + SPECIMEN.substring(2)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-004"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_004.getMessage()));
	}

	@Test
	public void sessionIsCheckedBeforeAnything() throws Exception {
		mvc.perform(passport()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-001"))
				.andExpect(jsonPath("$.errors").value(not(empty())));
	}

	private ResultActions scan(String mrzText) throws Exception {
		when(imageReader.read(any())).thenReturn(new PassportImageReading(mrzText, Map.of()));
		MockMultipartHttpServletRequestBuilder request = passport();
		request.header("Authorization", "Bearer session-token-123");
		request.header("X-Device-Signature", "eyJhbGciOiJFUzI1NiJ9..c2lnbmF0dXJl");
		return mvc.perform(request);
	}

	private static MockMultipartHttpServletRequestBuilder passport() {
		return (MockMultipartHttpServletRequestBuilder) multipart("/passport").file(image("frontImage"))
				.file(image("backImage"))
				.file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
						VALID_REQUEST.getBytes(StandardCharsets.UTF_8)));
	}

	private static MockMultipartFile image(String part) {
		return new MockMultipartFile(part, part + ".jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2 });
	}
}
