package io.mosip.idrepository.paas.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanRequest;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;
import io.mosip.idrepository.paas.spi.stub.StubPassportImageReader;

/** The MRZ step runs on what the OCR read, in the spec's order. */
public class PassportScanServiceImplTest {

	private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
	private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

	private final Td3MrzReader mrzReader = new Td3MrzReader(
			Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC));
	private final EnrolmentSession session = new EnrolmentSession("s1", null);
	private final PassportScanInput input = new PassportScanInput(new byte[] { 1 }, new byte[] { 2 },
			new PassportScanRequest());

	private static PassportImageReader readsMrz(String mrzText) {
		return image -> new PassportImageReading(mrzText, Map.of());
	}

	@Test
	public void withTheStubOcrTheScanStopsAtTheImageRead() {
		assertFails(new PassportScanServiceImpl(new StubPassportImageReader(), mrzReader), EnrolmentErrorCode.GEN_003,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	public void badCheckDigitStopsTheScanWithEnrPsp003() {
		String bad = LINE2.substring(0, 43) + "9";
		assertFails(new PassportScanServiceImpl(readsMrz(LINE1 + "\n" + bad), mrzReader), EnrolmentErrorCode.PSP_003,
				HttpStatus.OK);
	}

	@Test
	public void noMrzFoundIsEnrPsp003() {
		assertFails(new PassportScanServiceImpl(readsMrz(null), mrzReader), EnrolmentErrorCode.PSP_003, HttpStatus.OK);
	}

	@Test
	public void visaIsEnrPsp004() {
		assertFails(new PassportScanServiceImpl(readsMrz("V<" + LINE1.substring(2) + "\n" + LINE2), mrzReader),
				EnrolmentErrorCode.PSP_004, HttpStatus.OK);
	}

	@Test
	public void validMrzPassesToTheChecksNotBuiltYet() {
		assertFails(new PassportScanServiceImpl(readsMrz(LINE1 + "\n" + LINE2), mrzReader), EnrolmentErrorCode.GEN_003,
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	private void assertFails(PassportScanServiceImpl service, EnrolmentErrorCode code, HttpStatus status) {
		try {
			service.scan(session, input);
			fail("expected " + code.getCode());
		} catch (EnrolmentException e) {
			assertEquals(code, e.getErrorCode());
			assertEquals(status, e.getHttpStatus());
		}
	}
}
