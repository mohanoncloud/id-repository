package io.mosip.idrepository.paas.constant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.http.HttpStatus;

/** The codes and HTTP statuses must match the API spec (sections 3 and 6). */
public class EnrolmentErrorCodeTest {

	@Test
	public void codesAreUniqueAndWellFormed() {
		Set<String> codes = Arrays.stream(EnrolmentErrorCode.values()).map(EnrolmentErrorCode::getCode)
				.collect(Collectors.toSet());
		assertEquals(EnrolmentErrorCode.values().length, codes.size());
		codes.forEach(code -> assertTrue(code, code.matches("ENR-(PSP|SES|DEV|GEN)-\\d{3}")));
	}

	@Test
	public void passportCodes001To011ArePresent() {
		for (int i = 1; i <= 11; i++) {
			String code = String.format("ENR-PSP-%03d", i);
			assertTrue(code, Arrays.stream(EnrolmentErrorCode.values()).anyMatch(c -> c.getCode().equals(code)));
		}
	}

	@Test
	public void httpStatusesFollowTheSpec() {
		assertEquals(HttpStatus.UNAUTHORIZED, EnrolmentErrorCode.SES_001.getHttpStatus());
		assertEquals(HttpStatus.BAD_REQUEST, EnrolmentErrorCode.GEN_001.getHttpStatus());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, EnrolmentErrorCode.GEN_002.getHttpStatus());
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, EnrolmentErrorCode.PSP_011.getHttpStatus());
		assertEquals(HttpStatus.OK, EnrolmentErrorCode.PSP_007.getHttpStatus());
		assertEquals(HttpStatus.OK, EnrolmentErrorCode.SES_003.getHttpStatus());
	}
}
