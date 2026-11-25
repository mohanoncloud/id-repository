package io.mosip.idrepository.paas.spi.stub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.constant.Platform;
import io.mosip.idrepository.paas.exception.EnrolmentException;

/** Until MP-246 exists the stub must never let a device through. */
public class StubDeviceAttestationVerifierTest {

	@Test
	public void everyAttestationFailsWithEnrGen003() {
		EnrolmentException e = assertThrows(EnrolmentException.class,
				() -> new StubDeviceAttestationVerifier().verify(Platform.ANDROID, null, "jkt", null));

		assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
	}
}
