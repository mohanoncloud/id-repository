package io.mosip.idrepository.paas.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanRequest;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.passport.validation.PassportExpiryCheck;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;
import io.mosip.idrepository.paas.spi.stub.StubPassportImageReader;

/** The checks run on what the OCR read, in the spec's order: MRZ read, then expiry, then the rest. */
public class PassportScanServiceImplTest {

	/** ICAO 9303 specimen; its passport expires on 15 April 2012. */
	private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
	private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

	private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
	/** Noon UTC on the specimen's expiry date: still 15 April in Nassau, so not expired. */
	private static final Clock ON_SPECIMEN_EXPIRY_DATE = Clock.fixed(Instant.parse("2012-04-15T12:00:00Z"),
			ZoneOffset.UTC);

	private final Td3MrzReader mrzReader = new Td3MrzReader(TODAY);
	private final EnrolmentSession session = new EnrolmentSession("s1", null);
	private final PassportScanInput input = new PassportScanInput(new byte[] { 1 }, new byte[] { 2 },
			new PassportScanRequest());

	private static PassportImageReader readsMrz(String mrzText) {
		return image -> new PassportImageReading(mrzText, Map.of());
	}

	private PassportScanServiceImpl service(PassportImageReader imageReader) {
		return service(imageReader, TODAY);
	}

	private PassportScanServiceImpl service(PassportImageReader imageReader, Clock expiryClock) {
		return new PassportScanServiceImpl(imageReader, mrzReader,
				new PassportExpiryCheck(expiryClock, new PassportProperties()));
	}

	@Test
	public void withTheStubOcrTheScanStopsAtTheImageRead() {
		assertFails(service(new StubPassportImageReader()), EnrolmentErrorCode.GEN_003, HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	public void badCheckDigitStopsTheScanWithEnrPsp003() {
		// The specimen is also expired: the MRZ read fails first (spec order), so ENR-PSP-003, not 008.
		String bad = LINE2.substring(0, 43) + "9";
		assertFails(service(readsMrz(LINE1 + "\n" + bad)), EnrolmentErrorCode.PSP_003, HttpStatus.OK);
	}

	@Test
	public void noMrzFoundIsEnrPsp003() {
		assertFails(service(readsMrz(null)), EnrolmentErrorCode.PSP_003, HttpStatus.OK);
	}

	@Test
	public void visaIsEnrPsp004() {
		assertFails(service(readsMrz("V<" + LINE1.substring(2) + "\n" + LINE2)), EnrolmentErrorCode.PSP_004,
				HttpStatus.OK);
	}

	@Test
	public void expiredPassportIsEnrPsp008() {
		assertFails(service(readsMrz(LINE1 + "\n" + LINE2)), EnrolmentErrorCode.PSP_008, HttpStatus.OK);
	}

	@Test
	public void validUnexpiredPassportReachesTheChecksNotBuiltYet() {
		assertFails(service(readsMrz(LINE1 + "\n" + LINE2), ON_SPECIMEN_EXPIRY_DATE), EnrolmentErrorCode.GEN_003,
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
