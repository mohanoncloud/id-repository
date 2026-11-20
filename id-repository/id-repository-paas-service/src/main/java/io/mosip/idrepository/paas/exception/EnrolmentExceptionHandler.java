package io.mosip.idrepository.paas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import io.mosip.kernel.core.logger.spi.Logger;
import jakarta.servlet.http.HttpServletRequest;

/** Turns every failure into the spec's response envelope with {@code errors[]}. */
@RestControllerAdvice
public class EnrolmentExceptionHandler {

	private static final Logger LOGGER = IdRepoLogger.getLogger(EnrolmentExceptionHandler.class);
	private static final String CLASS = "EnrolmentExceptionHandler";

	@ExceptionHandler(EnrolmentException.class)
	public ResponseEntity<EnrolmentResponse<Object>> handle(EnrolmentException e, HttpServletRequest request) {
		LOGGER.info("-", CLASS, "handle", e.getErrorCode().getCode() + " " + e.getMessage());
		return respond(request, e.getErrorCode(), e.getHttpStatus());
	}

	/** File over 5 MB, or the whole request over the multipart limit. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<EnrolmentResponse<Object>> handle(MaxUploadSizeExceededException e,
			HttpServletRequest request) {
		return respond(request, EnrolmentErrorCode.PSP_001, HttpStatus.PAYLOAD_TOO_LARGE);
	}

	@ExceptionHandler({ MultipartException.class, HttpMessageNotReadableException.class,
			HttpMediaTypeNotSupportedException.class })
	public ResponseEntity<EnrolmentResponse<Object>> handleMalformed(Exception e, HttpServletRequest request) {
		LOGGER.info("-", CLASS, "handleMalformed", e.getClass().getSimpleName());
		return respond(request, EnrolmentErrorCode.GEN_001, EnrolmentErrorCode.GEN_001.getHttpStatus());
	}

	/** No such endpoint: a client error, not a server fault. The spec has no dedicated code, so ENR-GEN-001. */
	@ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
	public ResponseEntity<EnrolmentResponse<Object>> handleNotFound(Exception e, HttpServletRequest request) {
		return respond(request, EnrolmentErrorCode.GEN_001, HttpStatus.NOT_FOUND);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<EnrolmentResponse<Object>> handleWrongMethod(HttpRequestMethodNotSupportedException e,
			HttpServletRequest request) {
		return respond(request, EnrolmentErrorCode.GEN_001, HttpStatus.METHOD_NOT_ALLOWED);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<EnrolmentResponse<Object>> handleUnexpected(Exception e, HttpServletRequest request) {
		// Type only: the message or stack trace could carry request data.
		LOGGER.error("-", CLASS, "handleUnexpected", "Unexpected " + e.getClass().getName());
		return respond(request, EnrolmentErrorCode.GEN_003, EnrolmentErrorCode.GEN_003.getHttpStatus());
	}

	private static ResponseEntity<EnrolmentResponse<Object>> respond(HttpServletRequest request,
			EnrolmentErrorCode code, HttpStatus status) {
		return ResponseEntity.status(status).body(EnrolmentResponse.error(apiId(request), code));
	}

	static String apiId(HttpServletRequest request) {
		Object id = request.getAttribute(EnrolmentApi.API_ID_ATTRIBUTE);
		return id != null ? id.toString() : EnrolmentApi.forRequest(request);
	}
}
