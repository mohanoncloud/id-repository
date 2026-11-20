package io.mosip.idrepository.paas.dto;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import lombok.Data;

/**
 * MOSIP response envelope (API spec section 1): {@code id}, {@code version}, {@code responsetime} (UTC,
 * ISO-8601 with milliseconds), {@code response} and {@code errors[]}.
 */
@Data
public class EnrolmentResponse<T> {

	private static final DateTimeFormatter RESPONSE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private String id;
	private String version = EnrolmentApi.VERSION;
	private String responsetime = now();
	private T response;
	private List<EnrolmentError> errors = new ArrayList<>();

	public static <T> EnrolmentResponse<T> success(String id, T response) {
		EnrolmentResponse<T> envelope = new EnrolmentResponse<>();
		envelope.setId(id);
		envelope.setResponse(response);
		return envelope;
	}

	public static <T> EnrolmentResponse<T> error(String id, EnrolmentErrorCode code) {
		EnrolmentResponse<T> envelope = new EnrolmentResponse<>();
		envelope.setId(id);
		envelope.getErrors().add(new EnrolmentError(code.getCode(), code.getMessage()));
		return envelope;
	}

	static String now() {
		return ZonedDateTime.now(ZoneOffset.UTC).format(RESPONSE_TIME);
	}
}
