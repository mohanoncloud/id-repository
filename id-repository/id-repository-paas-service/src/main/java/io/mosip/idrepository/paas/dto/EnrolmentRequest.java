package io.mosip.idrepository.paas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * MOSIP request envelope (API spec section 1). JSON endpoints wrap the body in {@code request}; multipart
 * endpoints send this as a JSON part named {@code request}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnrolmentRequest<T> {

	private String id;
	private String version;
	private String requesttime;
	private T request;
}
