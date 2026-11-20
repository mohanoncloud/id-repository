package io.mosip.idrepository.paas.controller;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.EnrolmentRequest;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import io.mosip.idrepository.paas.dto.passport.PassportScanRequest;
import io.mosip.idrepository.paas.dto.passport.PassportScanResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.httpfilter.EnrolmentSessionFilter;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.service.PassportScanService;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import jakarta.servlet.http.HttpServletRequest;

/** {@code POST /v1/enrolment/passport} (API spec section 3). */
@RestController
public class PassportController {

	private static final TypeReference<EnrolmentRequest<PassportScanRequest>> REQUEST_TYPE = new TypeReference<>() {
	};

	private final PassportScanService passportScanService;
	private final ObjectMapper mapper;

	public PassportController(PassportScanService passportScanService, ObjectMapper mapper) {
		this.passportScanService = passportScanService;
		this.mapper = mapper;
	}

	/**
	 * Parts are optional at the Spring level so that a missing part returns the spec's error code
	 * (ENR-PSP-001 for an image, ENR-GEN-001 for the request) rather than a generic 400.
	 */
	@PostMapping(path = "/passport", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EnrolmentResponse<PassportScanResponse>> scanPassport(
			@RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
			@RequestPart(value = "backImage", required = false) MultipartFile backImage,
			@RequestPart(value = "request", required = false) String requestJson,
			@RequestAttribute(EnrolmentSessionFilter.SESSION_ATTRIBUTE) EnrolmentSession session,
			HttpServletRequest httpRequest) {
		httpRequest.setAttribute(EnrolmentApi.API_ID_ATTRIBUTE, EnrolmentApi.PASSPORT);

		EnrolmentRequest<PassportScanRequest> request = parseRequest(requestJson);
		PassportScanInput input = new PassportScanInput(bytes(frontImage), bytes(backImage), request.getRequest());

		PassportScanResponse response = passportScanService.scan(session, input);
		return ResponseEntity.ok(EnrolmentResponse.success(EnrolmentApi.PASSPORT, response));
	}

	/** Malformed JSON, or the envelope id or version is wrong → ENR-GEN-001 (spec section 6). */
	private EnrolmentRequest<PassportScanRequest> parseRequest(String json) {
		if (json == null || json.isBlank()) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_001, "request part missing");
		}
		EnrolmentRequest<PassportScanRequest> request;
		try {
			request = mapper.readValue(json, REQUEST_TYPE);
		} catch (JsonProcessingException e) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_001, "request part is not valid JSON");
		}
		if (!EnrolmentApi.PASSPORT.equals(request.getId()) || !EnrolmentApi.VERSION.equals(request.getVersion())) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_001, "wrong envelope id or version");
		}
		if (request.getRequest() == null) {
			request.setRequest(new PassportScanRequest());
		}
		return request;
	}

	/** Missing or empty image → ENR-PSP-001. */
	private static byte[] bytes(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new EnrolmentException(EnrolmentErrorCode.PSP_001, "image part missing or empty");
		}
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new EnrolmentException(EnrolmentErrorCode.PSP_001, "image part unreadable");
		}
	}
}
