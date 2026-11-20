package io.mosip.idrepository.paas.spi.stub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.session.StubSessionVerifier;

public class StubsTest {

	@Test
	public void applicationIdHasTheRidLayout() {
		String aid = new StubApplicationIdGenerator().generate();
		assertEquals(29, aid.length());
		assertTrue(aid, aid.matches("\\d{29}"));
		assertTrue(aid.startsWith(StubApplicationIdGenerator.PLACEHOLDER_CENTRE_ID
				+ StubApplicationIdGenerator.PLACEHOLDER_MACHINE_ID));
	}

	@Test
	public void unconfiguredStubsFailWithEnrGen003And503() {
		assertNotConfigured(() -> new StubPassportImageReader().read(new byte[1]));
		assertNotConfigured(() -> new StubImageQualityChecker().check(new byte[1]));
		assertNotConfigured(() -> new StubPassportHashService().hash("BHS", "P1234567"));
		assertNotConfigured(() -> new StubIdRepoDraftClient().createDraft("1"));
		assertNotConfigured(() -> new StubIdRepoDraftClient().updateDraft("1", Map.of()));
		assertNotConfigured(() -> new StubIdRepoDraftClient().discardDraft("1"));
	}

	@Test
	public void stubSessionIdDoesNotExposeTheToken() {
		String id = new StubSessionVerifier().verify("secret-token", "sig", null).sessionId();
		assertTrue(id.startsWith("stub-"));
		assertTrue(!id.contains("secret-token"));
		assertEquals(id, new StubSessionVerifier().verify("secret-token", "other-sig", null).sessionId());
	}

	private static void assertNotConfigured(Runnable call) {
		try {
			call.run();
			fail("expected EnrolmentException");
		} catch (EnrolmentException e) {
			assertEquals(EnrolmentErrorCode.GEN_003, e.getErrorCode());
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getHttpStatus());
		}
	}
}
