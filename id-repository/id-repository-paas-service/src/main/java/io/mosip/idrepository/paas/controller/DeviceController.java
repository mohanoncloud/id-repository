package io.mosip.idrepository.paas.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.idrepository.paas.constant.EnrolmentApi;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.EnrolmentRequest;
import io.mosip.idrepository.paas.dto.EnrolmentResponse;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterRequest;
import io.mosip.idrepository.paas.dto.device.DeviceRegisterResponse;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /v1/enrolment/device/register} (MP-247). Public: the app calls it before it has a session, so
 * {@code EnrolmentSessionFilter} skips it; the attestation proves the request instead, and
 * {@code PublicEndpointRateLimitFilter} limits it per client IP.
 */
@RestController
public class DeviceController {

	private final DeviceService deviceService;

	public DeviceController(DeviceService deviceService) {
		this.deviceService = deviceService;
	}

	@Operation(summary = "Register the device key",
			description = "Checks the attestation and the supported-device policy, then stores the device public "
					+ "key and returns the device ID. The same key can't be registered twice. No session needed.")
	@ApiResponse(responseCode = "200", description = "Registered (errors[] empty), or a business failure with "
			+ "errors[]: ENR-DEV-001 device not supported, ENR-DEV-002 key already registered (also when it was revoked), "
			+ "ENR-DEV-004 attestation failed (all proposed, MP-247)")
	@ApiResponse(responseCode = "400", description = "ENR-GEN-001: malformed request")
	@ApiResponse(responseCode = "429", description = "ENR-GEN-002: too many requests from this client")
	@ApiResponse(responseCode = "503", description = "ENR-GEN-003: attestation, app config or database unavailable")
	@PostMapping(path = EnrolmentApi.DEVICE_REGISTER_PATH, consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<EnrolmentResponse<DeviceRegisterResponse>> register(
			@RequestBody(required = false) EnrolmentRequest<DeviceRegisterRequest> request,
			HttpServletRequest httpRequest) {
		httpRequest.setAttribute(EnrolmentApi.API_ID_ATTRIBUTE, EnrolmentApi.DEVICE_REGISTER);

		if (request == null || !EnrolmentApi.DEVICE_REGISTER.equals(request.getId())
				|| !EnrolmentApi.VERSION.equals(request.getVersion()) || request.getRequest() == null) {
			throw new EnrolmentException(EnrolmentErrorCode.GEN_001, "wrong envelope id or version, or no request");
		}
		DeviceRegisterResponse response = deviceService.register(request.getRequest());
		return ResponseEntity.ok(EnrolmentResponse.success(EnrolmentApi.DEVICE_REGISTER, response));
	}
}
