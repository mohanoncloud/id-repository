package io.mosip.idrepository.paas.exception;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;

public class EnrolmentExceptionHandlerTest {

	private final EnrolmentExceptionHandler handler = new EnrolmentExceptionHandler();

	private static MockHttpServletRequest request(String servletPath) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/enrolment" + servletPath);
		request.setContextPath("/v1/enrolment");
		request.setServletPath(servletPath);
		return request;
	}

	@Test
	public void fileOverTheLimitIsEnrPsp001With413() {
		ResponseEntity<EnrolmentResponse<Object>> response = handler
				.handle(new MaxUploadSizeExceededException(5 * 1024 * 1024), request("/passport"));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
		assertEquals("ENR-PSP-001", response.getBody().getErrors().get(0).getErrorCode());
		assertEquals(EnrolmentApi.PASSPORT, response.getBody().getId());
	}

	@Test
	public void unknownPathIs404NotAServerFault() {
		ResponseEntity<EnrolmentResponse<Object>> response = handler.handleNotFound(
				new NoResourceFoundException(HttpMethod.GET, "unknown"), request("/unknown"));

		assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
		assertEquals("ENR-GEN-001", response.getBody().getErrors().get(0).getErrorCode());
	}

	@Test
	public void wrongMethodIs405() {
		ResponseEntity<EnrolmentResponse<Object>> response = handler
				.handleWrongMethod(new HttpRequestMethodNotSupportedException("GET"), request("/passport"));

		assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
		assertEquals("ENR-GEN-001", response.getBody().getErrors().get(0).getErrorCode());
		assertEquals(EnrolmentApi.PASSPORT, response.getBody().getId());
	}

	@Test
	public void apiIdFallsBackToThePath() {
		assertEquals(EnrolmentApi.PASSPORT, EnrolmentExceptionHandler.apiId(request("/passport")));
		assertEquals(EnrolmentApi.DEFAULT, EnrolmentExceptionHandler.apiId(request("/unknown")));

		MockHttpServletRequest withAttribute = request("/unknown");
		withAttribute.setAttribute(EnrolmentApi.API_ID_ATTRIBUTE, "mosip.enrolment.face.match");
		assertEquals("mosip.enrolment.face.match", EnrolmentExceptionHandler.apiId(withAttribute));
	}

	@Test
	public void unexpectedExceptionMessageIsNotReturned() {
		ResponseEntity<EnrolmentResponse<Object>> response = handler
				.handleUnexpected(new IllegalStateException("passport P1234567"), request("/passport"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertEquals("ENR-GEN-003", response.getBody().getErrors().get(0).getErrorCode());
		assertEquals(false, response.getBody().toString().contains("P1234567"));
	}
}
