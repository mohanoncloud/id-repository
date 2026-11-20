package io.mosip.idrepository.paas.spi.stub;

import org.springframework.http.HttpStatus;

import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.kernel.core.logger.spi.Logger;

/** Shared behaviour of the stubs: log a warning at start-up and fail calls with ENR-GEN-003 (HTTP 503). */
final class NotConfigured {

	private static final Logger LOGGER = IdRepoLogger.getLogger(NotConfigured.class);

	private NotConfigured() {
	}

	static void warn(Class<?> stub, String what) {
		LOGGER.warn("-", stub.getSimpleName(), "init", "STUB in use: " + what + " is not configured");
	}

	static EnrolmentException fail(String what) {
		return new EnrolmentException(EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE,
				what + " is not configured");
	}
}
