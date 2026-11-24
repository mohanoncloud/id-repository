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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import io.mosip.idrepository.paas.spi.PassportImageReader.OcrField;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;

/**
 * {@code POST /passport} through the whole application: session filter, controller, file checks, MRZ read, printed
 * vs MRZ, expiry check and the error envelope, with the real config binding. Images are synthetic JPEG/PNG files.
 * Only the OCR is faked (it returns the MRZ text and the printed fields), because the OCR library isn't chosen yet
 * (MP-397). Spring Security's default
 * chain is excluded as in {@link SwaggerTest}: in an environment the kernel auth adapter lets these paths through.
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

	/** Synthetic 1600 x 1200 JPEG that passes the file checks. */
	private static final byte[] VALID_IMAGE = TestImages.validJpeg();

	private static final String VALID_REQUEST ="{\"id\":\"mosip.enrolment.passport\",\"version\":\"1.0\","
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

	/** Printed details matching the specimen MRZ. */
	private static Map<String, OcrField> specimenPrinted() {
		return printed("ERIKSSON", "ANNA MARIA", "L898902C3", "UTO", "12 AUG 1974", "F", "15 APR 2012");
	}

	/** Printed details matching the synthetic Bahamas passports; the nationality is printed as a word. */
	private static Map<String, OcrField> bahamasPrinted(String dateOfExpiry) {
		return printed("SMITH", "JOHN PAUL", "AB1234567", "BAHAMIAN", "15 JAN 1990", "M", dateOfExpiry);
	}

	private static Map<String, OcrField> printed(String surname, String givenNames, String passportNumber,
			String nationality, String dateOfBirth, String sex, String dateOfExpiry) {
		Map<String, OcrField> printed = new HashMap<>();
		printed.put("surname", new OcrField(surname, 0.97));
		printed.put("givenNames", new OcrField(givenNames, 0.97));
		printed.put("passportNumber", new OcrField(passportNumber, 0.97));
		printed.put("nationality", new OcrField(nationality, 0.97));
		printed.put("dateOfBirth", new OcrField(dateOfBirth, 0.97));
		printed.put("sex", new OcrField(sex, 0.97));
		printed.put("dateOfExpiry", new OcrField(dateOfExpiry, 0.97));
		return printed;
	}

	@Test
	public void passportRulesComeFromConfig() {
		assertEquals("America/Nassau", passportProperties.getExpiryZone());
		assertEquals(0, passportProperties.getExpiryGraceDays());
		assertEquals(0.80, passportProperties.getPrintedMinConfidence(), 0.0);
		assertEquals(List.of("BAHAMIAN", "BAHAMAS", "THE BAHAMAS"), passportProperties.getNationalityNames().get("BHS"));
	}

	// ------------------------------------------------------------------ expiry (MP-258)

	@Test
	public void expiredPassportIsEnrPsp008WithTheSpecMessage() throws Exception {
		scan(SPECIMEN, specimenPrinted()).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.passport"))
				.andExpect(jsonPath("$.response").doesNotExist())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-008"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_008.getMessage()));
	}

	@Test
	public void passportThatExpiredYesterdayInNassauIsEnrPsp008() throws Exception {
		scan(EXPIRED_YESTERDAY_IN_NASSAU, bahamasPrinted("17 NOV 2026")).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-008"));
	}

	@Test
	public void passportExpiringTodayInNassauIsAcceptedAlthoughUtcIsTheNextDay() throws Exception {
		// Passes printed vs MRZ (BAHAMIAN = BHS) and expiry, and reaches the steps not built yet (MP-259).
		scan(EXPIRES_TODAY_IN_NASSAU, bahamasPrinted("18 NOV 2026")).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}

	// ------------------------------------------------------------------ printed vs MRZ (MP-257)

	@Test
	public void printedDetailsThatDontMatchAreEnrPsp007WithTheSpecMessage() throws Exception {
		Map<String, OcrField> printed = bahamasPrinted("18 NOV 2026");
		printed.put("passportNumber", new OcrField("AB1234568", 0.97));
		scan(EXPIRES_TODAY_IN_NASSAU, printed).andExpect(status().isOk())
				.andExpect(jsonPath("$.response").doesNotExist())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-007"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_007.getMessage()));
	}

	@Test
	public void mismatchIsReportedBeforeExpiry() throws Exception {
		// Expired and a different date of birth: printed vs MRZ comes first in the spec.
		Map<String, OcrField> printed = specimenPrinted();
		printed.put("dateOfBirth", new OcrField("13 AUG 1974", 0.97));
		scan(SPECIMEN, printed).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-007"));
	}

	@Test
	public void unreadablePrintedDetailsAreEnrPsp002AskingForARetake() throws Exception {
		Map<String, OcrField> printed = bahamasPrinted("18 NOV 2026");
		printed.put("surname", new OcrField("SMITH", 0.42));
		scan(EXPIRES_TODAY_IN_NASSAU, printed).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-002"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_002.getMessage()));
	}

	// ------------------------------------------------------------------ MRZ (MP-261) and session

	@Test
	public void badCheckDigitIsEnrPsp003BeforeAnyOtherCheck() throws Exception {
		String badCompositeDigit = SPECIMEN.substring(0, SPECIMEN.length() - 1) + "9";
		scan(badCompositeDigit, specimenPrinted()).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-003"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_003.getMessage()));
	}

	@Test
	public void noMrzFoundIsEnrPsp003() throws Exception {
		scan(null, specimenPrinted()).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-003"));
	}

	@Test
	public void visaIsEnrPsp004() throws Exception {
		scan("V<" + SPECIMEN.substring(2), specimenPrinted()).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-004"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_004.getMessage()));
	}

	// ------------------------------------------------------------------ file checks (MP-256)

	@Test
	public void pdfRenamedAsJpgIsEnrPsp001WithTheSpecMessage() throws Exception {
		byte[] pdf = "%PDF-1.4 fake scan".getBytes(StandardCharsets.US_ASCII);
		scan(SPECIMEN, specimenPrinted(), pdf, VALID_IMAGE).andExpect(status().isOk())
				.andExpect(jsonPath("$.response").doesNotExist())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.PSP_001.getMessage()));
	}

	@Test
	public void lowResolutionBackImageIsEnrPsp001() throws Exception {
		scan(SPECIMEN, specimenPrinted(), VALID_IMAGE, TestImages.jpeg(1100, 800)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"));
	}

	@Test
	public void imageOverFiveMegabytesIsHttp413WithEnrPsp001() throws Exception {
		byte[] big = Arrays.copyOf(VALID_IMAGE, 5 * 1024 * 1024 + 1);
		scan(SPECIMEN, specimenPrinted(), big, VALID_IMAGE).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"));
	}

	@Test
	public void fileChecksComeBeforeTheMrzRead() throws Exception {
		// A bad image and an unreadable MRZ: the file check fails first (spec order).
		scan(null, specimenPrinted(), new byte[] { 1, 2, 3 }, VALID_IMAGE).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"));
	}

	@Test
	public void pngImagesAreAccepted() throws Exception {
		scan(EXPIRES_TODAY_IN_NASSAU, bahamasPrinted("18 NOV 2026"), TestImages.png(1200, 1700), TestImages.png(1700, 1200))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}

	@Test
	public void sessionIsCheckedBeforeAnything() throws Exception {
		mvc.perform(passport(VALID_IMAGE, VALID_IMAGE)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-001"))
				.andExpect(jsonPath("$.errors").value(not(empty())));
	}

	private ResultActions scan(String mrzText, Map<String, OcrField> printed) throws Exception {
		return scan(mrzText, printed, VALID_IMAGE, VALID_IMAGE);
	}

	private ResultActions scan(String mrzText, Map<String, OcrField> printed, byte[] front, byte[] back)
			throws Exception {
		when(imageReader.read(any())).thenReturn(new PassportImageReading(mrzText, printed));
		MockMultipartHttpServletRequestBuilder request = passport(front, back);
		request.header("Authorization", "Bearer session-token-123");
		request.header("X-Device-Signature", "eyJhbGciOiJFUzI1NiJ9..c2lnbmF0dXJl");
		return mvc.perform(request);
	}

	private static MockMultipartHttpServletRequestBuilder passport(byte[] front, byte[] back) {
		return (MockMultipartHttpServletRequestBuilder) multipart("/passport")
				.file(new MockMultipartFile("frontImage", "front.jpg", "image/jpeg", front))
				.file(new MockMultipartFile("backImage", "back.jpg", "image/jpeg", back))
				.file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
						VALID_REQUEST.getBytes(StandardCharsets.UTF_8)));
	}
}
