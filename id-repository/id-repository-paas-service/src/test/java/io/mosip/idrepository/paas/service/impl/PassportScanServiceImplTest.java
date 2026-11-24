package io.mosip.idrepository.paas.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.dto.passport.PassportScanRequest;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.compare.PrintedVsMrzComparator;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.passport.validation.PassportExpiryCheck;
import io.mosip.idrepository.paas.service.PassportScanInput;
import io.mosip.idrepository.paas.session.EnrolmentSession;
import io.mosip.idrepository.paas.spi.PassportImageReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.OcrField;
import io.mosip.idrepository.paas.spi.PassportImageReader.PassportImageReading;
import io.mosip.idrepository.paas.spi.stub.StubPassportImageReader;

/** The checks run on what the OCR read, in the spec's order: MRZ read, printed vs MRZ, expiry, then the rest. */
public class PassportScanServiceImplTest {

	/** ICAO 9303 specimen; its passport expires on 15 April 2012. */
	private static final String LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
	private static final String LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";
	private static final String SPECIMEN = LINE1 + "\n" + LINE2;

	private static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
	/** Noon UTC on the specimen's expiry date: still 15 April in Nassau, so not expired. */
	private static final Clock ON_SPECIMEN_EXPIRY_DATE = Clock.fixed(Instant.parse("2012-04-15T12:00:00Z"),
			ZoneOffset.UTC);

	private final Td3MrzReader mrzReader = new Td3MrzReader(TODAY);
	private final EnrolmentSession session = new EnrolmentSession("s1", null);
	private final PassportScanInput input = new PassportScanInput(new byte[] { 1 }, new byte[] { 2 },
			new PassportScanRequest());

	/** Printed details that match the specimen MRZ. */
	private static Map<String, OcrField> specimenPrinted() {
		Map<String, OcrField> printed = new HashMap<>();
		printed.put("surname", new OcrField("ERIKSSON", 0.98));
		printed.put("givenNames", new OcrField("ANNA MARIA", 0.98));
		printed.put("passportNumber", new OcrField("L898902C3", 0.98));
		printed.put("nationality", new OcrField("UTO", 0.98));
		printed.put("dateOfBirth", new OcrField("12 AUG 1974", 0.98));
		printed.put("sex", new OcrField("F", 0.98));
		printed.put("dateOfExpiry", new OcrField("15 APR 2012", 0.98));
		return printed;
	}

	private static PassportImageReader reads(String mrzText, Map<String, OcrField> printed) {
		return image -> new PassportImageReading(mrzText, printed);
	}

	private static PassportImageReader readsMrz(String mrzText) {
		return reads(mrzText, specimenPrinted());
	}

	private PassportScanServiceImpl service(PassportImageReader imageReader) {
		return service(imageReader, TODAY);
	}

	private PassportScanServiceImpl service(PassportImageReader imageReader, Clock expiryClock) {
		PassportProperties properties = new PassportProperties();
		return new PassportScanServiceImpl(imageReader, mrzReader, new PrintedVsMrzComparator(properties),
				new PassportExpiryCheck(expiryClock, properties));
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
	public void printedDetailsThatDontMatchAreEnrPsp007BeforeTheExpiryCheck() {
		// The specimen is expired too; printed vs MRZ comes first in the spec, so ENR-PSP-007, not 008.
		Map<String, OcrField> printed = specimenPrinted();
		printed.put("dateOfBirth", new OcrField("13 AUG 1974", 0.98));
		assertFails(service(reads(SPECIMEN, printed)), EnrolmentErrorCode.PSP_007, HttpStatus.OK);
	}

	@Test
	public void unreadablePrintedDetailsAreEnrPsp002() {
		Map<String, OcrField> printed = specimenPrinted();
		printed.put("surname", new OcrField("ERIKSSON", 0.4));
		assertFails(service(reads(SPECIMEN, printed)), EnrolmentErrorCode.PSP_002, HttpStatus.OK);
	}

	@Test
	public void noPrintedDetailsAtAllAreEnrPsp002() {
		assertFails(service(reads(SPECIMEN, Map.of())), EnrolmentErrorCode.PSP_002, HttpStatus.OK);
	}

	@Test
	public void expiredPassportIsEnrPsp008() {
		assertFails(service(readsMrz(SPECIMEN)), EnrolmentErrorCode.PSP_008, HttpStatus.OK);
	}

	@Test
	public void validUnexpiredPassportReachesTheChecksNotBuiltYet() {
		assertFails(service(readsMrz(SPECIMEN), ON_SPECIMEN_EXPIRY_DATE), EnrolmentErrorCode.GEN_003,
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
