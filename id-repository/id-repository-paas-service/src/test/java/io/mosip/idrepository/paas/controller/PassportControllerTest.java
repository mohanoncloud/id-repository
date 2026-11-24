package io.mosip.idrepository.paas.controller;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.TestImages;
import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.config.SessionProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.exception.EnrolmentExceptionHandler;
import io.mosip.idrepository.paas.httpfilter.EnrolmentSessionFilter;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.service.PassportScanService;
import io.mosip.idrepository.paas.service.impl.PassportScanServiceImpl;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.session.SessionVerifier;
import io.mosip.idrepository.paas.session.StubSessionVerifier;
import io.mosip.idrepository.paas.passport.compare.PrintedVsMrzComparator;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.passport.upload.PassportImageValidator;
import io.mosip.idrepository.paas.passport.validation.PassportExpiryCheck;
import io.mosip.idrepository.paas.spi.stub.StubPassportImageReader;

public class PassportControllerTest {

	private static final String RESPONSE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";
	private static final String VALID_REQUEST = "{\"id\":\"mosip.enrolment.passport\",\"version\":\"1.0\","
			+ "\"requesttime\":\"2026-10-31T10:18:05.000Z\",\"request\":{\"additionalDetails\":{\"address\":"
			+ "{\"addressLine1\":\"12 Shirley Street\",\"city\":\"Nassau\",\"province\":\"New Providence\","
			+ "\"region\":\"New Providence\",\"zone\":\"Centreville\"}}}}";

	private final ObjectMapper mapper = new ObjectMapper();
	private PassportScanService service;

	@Before
	public void setUp() {
		service = mock(PassportScanService.class);
	}

	private MockMvc mvc(PassportScanService scanService, SessionVerifier verifier) {
		return MockMvcBuilders.standaloneSetup(new PassportController(scanService, mapper))
				.setControllerAdvice(new EnrolmentExceptionHandler())
				.addFilters(new EnrolmentSessionFilter(verifier, new SessionProperties(), mapper))
				.build();
	}

	private MockMvc mvc() {
		return mvc(service, new StubSessionVerifier());
	}

	private static MockMultipartFile image(String part) {
		return new MockMultipartFile(part, part + ".jpg", "image/jpeg", new byte[] { (byte) 0xFF, (byte) 0xD8, 1, 2 });
	}

	private static MockMultipartFile requestPart(String json) {
		return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
				json.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartHttpServletRequestBuilder passport() {
		return multipart("/passport");
	}

	private static MockMultipartHttpServletRequestBuilder signed(MockMultipartHttpServletRequestBuilder builder) {
		builder.header("Authorization", "Bearer session-token-123");
		builder.header("X-Device-Signature", "eyJhbGciOiJFUzI1NiJ9..c2lnbmF0dXJl");
		return builder;
	}

	// ------------------------------------------------------------------ session

	@Test
	public void missingSessionTokenIs401WithEnrSes001() throws Exception {
		mvc().perform(passport().file(image("frontImage")).file(image("backImage")).file(requestPart(VALID_REQUEST)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.passport"))
				.andExpect(jsonPath("$.version").value("1.0"))
				.andExpect(jsonPath("$.responsetime").value(matchesPattern(RESPONSE_TIME)))
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-001"))
				.andExpect(jsonPath("$.errors[0].message").value(EnrolmentErrorCode.SES_001.getMessage()));
		verify(service, never()).scan(any(), any());
	}

	@Test
	public void tokenWithoutBearerPrefixIsRejected() throws Exception {
		mvc().perform(passport().header("Authorization", "session-token-123").header("X-Device-Signature", "sig"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-001"));
	}

	@Test
	public void missingDeviceSignatureIsEnrSes003() throws Exception {
		mvc().perform(passport().header("Authorization", "Bearer session-token-123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-003"));
	}

	@Test
	public void verifierRejectionIsPassedThrough() throws Exception {
		SessionVerifier rejecting = (token, signature, request) -> {
			throw new EnrolmentException(EnrolmentErrorCode.SES_003);
		};
		mvc(service, rejecting).perform(signed(passport()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-SES-003"));
	}

	// ------------------------------------------------------------------ request validation

	@Test
	public void missingFrontImageIsEnrPsp001() throws Exception {
		mvc().perform(signed(passport().file(image("backImage")).file(requestPart(VALID_REQUEST))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"));
	}

	@Test
	public void emptyBackImageIsEnrPsp001() throws Exception {
		MockMultipartFile empty = new MockMultipartFile("backImage", "b.jpg", "image/jpeg", new byte[0]);
		mvc().perform(signed(passport().file(image("frontImage")).file(empty).file(requestPart(VALID_REQUEST))))
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-001"));
	}

	@Test
	public void missingRequestPartIsEnrGen001() throws Exception {
		mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}

	@Test
	public void malformedJsonIsEnrGen001() throws Exception {
		mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage")).file(requestPart("{not json"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
	}

	@Test
	public void wrongEnvelopeIdOrVersionIsEnrGen001() throws Exception {
		String wrongId = VALID_REQUEST.replace("mosip.enrolment.passport", "mosip.enrolment.other");
		String wrongVersion = VALID_REQUEST.replace("\"version\":\"1.0\"", "\"version\":\"2.0\"");
		for (String json : new String[] { wrongId, wrongVersion }) {
			mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage")).file(requestPart(json))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.id").value("mosip.enrolment.passport"))
					.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-001"));
		}
	}

	// ------------------------------------------------------------------ service

	@Test
	public void successReturnsTheSpecEnvelope() throws Exception {
		PassportScanResponse response = new PassportScanResponse();
		response.setApplicationId("10001100010000120261031101805");
		response.setStatus("PASSPORT_VERIFIED");
		response.setNextStep("FACE_MATCH");
		when(service.scan(any(), any())).thenReturn(response);

		mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage")).file(requestPart(VALID_REQUEST))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("mosip.enrolment.passport"))
				.andExpect(jsonPath("$.response.applicationId").value("10001100010000120261031101805"))
				.andExpect(jsonPath("$.response.status").value("PASSPORT_VERIFIED"))
				.andExpect(jsonPath("$.response.nextStep").value("FACE_MATCH"))
				.andExpect(jsonPath("$.errors").value(empty()));

		ArgumentCaptor<EnrolmentSession> session = ArgumentCaptor.forClass(EnrolmentSession.class);
		ArgumentCaptor<PassportScanInput> input = ArgumentCaptor.forClass(PassportScanInput.class);
		verify(service).scan(session.capture(), input.capture());
		assertEquals(4, input.getValue().frontImage().length);
		assertEquals("Nassau", input.getValue().request().getAdditionalDetails().getAddress().getCity());
		assertEquals(false, session.getValue().sessionId().contains("session-token-123"));
	}

	@Test
	public void businessFailureIsHttp200WithErrors() throws Exception {
		when(service.scan(any(), any())).thenThrow(new EnrolmentException(EnrolmentErrorCode.PSP_007));

		mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage")).file(requestPart(VALID_REQUEST))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-PSP-007"))
				.andExpect(jsonPath("$.errors[0].message")
						.value("The details on your passport don’t match. Please visit an assisted desk."));
	}

	@Test
	public void scaffoldServiceAnswersEnrGen003With503() throws Exception {
		PassportProperties properties = new PassportProperties();
		byte[] jpeg = TestImages.validJpeg(); // passes the file checks, so the scan reaches the stub OCR
		mvc(new PassportScanServiceImpl(new PassportImageValidator(properties), new StubPassportImageReader(),
				new Td3MrzReader(Clock.systemUTC()), new PrintedVsMrzComparator(properties),
				new PassportExpiryCheck(Clock.systemUTC(), properties)), new StubSessionVerifier())
				.perform(signed(passport().file(new MockMultipartFile("frontImage", "f.jpg", "image/jpeg", jpeg))
						.file(new MockMultipartFile("backImage", "b.jpg", "image/jpeg", jpeg)).file(requestPart(VALID_REQUEST))))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}

	@Test
	public void unexpectedErrorIsEnrGen003With500() throws Exception {
		when(service.scan(any(), any())).thenThrow(new IllegalStateException("boom"));

		mvc().perform(signed(passport().file(image("frontImage")).file(image("backImage")).file(requestPart(VALID_REQUEST))))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errors[0].errorCode").value("ENR-GEN-003"));
	}
}
