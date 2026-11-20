package io.mosip.idrepository.paas.passport.mrz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.Test;

import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;

public class Td3MrzReaderTest {

	/** ICAO 9303 Part 4 TD3 specimen. */
	static final String ICAO_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<";
	static final String ICAO_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10";

	/** "Today" for all tests: 18 Nov 2026. */
	static final Clock TODAY = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);

	private final Td3MrzReader reader = new Td3MrzReader(TODAY);

	// ------------------------------------------------------------------ valid MRZs

	@Test
	public void readsIcaoSpecimen() {
		assertEquals(44, ICAO_LINE1.length());
		assertEquals(44, ICAO_LINE2.length());

		PassportMrz mrz = reader.read(ICAO_LINE1, ICAO_LINE2);

		assertEquals("P", mrz.documentType());
		assertEquals("UTO", mrz.issuingState());
		assertEquals("ERIKSSON", mrz.surname());
		assertEquals("ANNA MARIA", mrz.givenNames());
		assertFalse(mrz.nameTruncated());
		assertEquals("L898902C3", mrz.documentNumber());
		assertEquals("UTO", mrz.nationality());
		assertEquals(LocalDate.of(1974, 8, 12), mrz.dateOfBirth());
		assertEquals(Sex.FEMALE, mrz.sex());
		assertEquals(LocalDate.of(2012, 4, 15), mrz.expiryDate());
		assertEquals("ZE184226B", mrz.optionalData());
	}

	@Test
	public void readsTwoLineText() {
		PassportMrz mrz = reader.read("\r\n" + ICAO_LINE1 + "\r\n\r\n" + ICAO_LINE2 + "\n");
		assertEquals("L898902C3", mrz.documentNumber());
	}

	@Test
	public void readsSyntheticBahamasPassport() {
		PassportMrz mrz = reader.read(Mrz.line1("P", "BHS", "SMITH<<JOHN<PAUL"),
				Mrz.line2("AB1234567", "BHS", "900115", 'M', "310630", ""));

		assertEquals("BHS", mrz.issuingState());
		assertEquals("SMITH", mrz.surname());
		assertEquals("JOHN PAUL", mrz.givenNames());
		assertEquals("AB1234567", mrz.documentNumber());
		assertEquals(LocalDate.of(1990, 1, 15), mrz.dateOfBirth());
		assertEquals(Sex.MALE, mrz.sex());
		assertEquals(LocalDate.of(2031, 6, 30), mrz.expiryDate());
		assertEquals("", mrz.optionalData());
	}

	@Test
	public void optionalDataWithoutCheckDigitAndUnspecifiedSex() {
		String line2 = Mrz.line2("AB1234567", "BHS", "900115", '<', "310630", "");
		assertEquals('<', line2.charAt(42));

		PassportMrz mrz = reader.read(Mrz.line1("P", "BHS", "DOE<<ALEX"), line2);

		assertEquals(Sex.UNSPECIFIED, mrz.sex());
	}

	// ------------------------------------------------------------------ check digits

	@Test
	public void eachCheckDigitFailureIsEnrPsp003WithItsField() {
		int[] positions = { 9, 19, 27, 42, 43 };
		MrzField[] fields = { MrzField.DOCUMENT_NUMBER, MrzField.DATE_OF_BIRTH, MrzField.EXPIRY_DATE,
				MrzField.OPTIONAL_DATA, MrzField.COMPOSITE };
		for (int i = 0; i < positions.length; i++) {
			char original = ICAO_LINE2.charAt(positions[i]);
			String line2 = Mrz.replaceAt(ICAO_LINE2, positions[i], original == '5' ? '6' : '5');

			MrzException e = expectFailure(ICAO_LINE1, line2);

			assertEquals(MrzError.CHECK_DIGIT_FAILED, e.getError());
			assertEquals(Optional.of(fields[i]), e.getField());
			assertEquals(EnrolmentErrorCode.PSP_003, e.getErrorCode());
		}
	}

	@Test
	public void changedDataFailsItsCheckDigit() {
		String line2 = Mrz.replaceAt(ICAO_LINE2, 18, '3'); // DOB 740812 -> 740813

		assertEquals(Optional.of(MrzField.DATE_OF_BIRTH), expectFailure(ICAO_LINE1, line2).getField());
	}

	@Test
	public void invalidSexIsUnreadable() {
		String line2 = Mrz.replaceAt(ICAO_LINE2, 20, 'Q'); // sex is not covered by check digits

		assertEquals(MrzError.MRZ_UNREADABLE, expectFailure(ICAO_LINE1, line2).getError());
	}

	// ------------------------------------------------------------------ OCR corrections

	@Test
	public void lettersInDigitFieldsAreCorrected() {
		String line2 = ICAO_LINE2;
		line2 = Mrz.replaceAt(line2, 15, 'O'); // DOB 740812 read as 74O8I2
		line2 = Mrz.replaceAt(line2, 17, 'I');
		line2 = Mrz.replaceAt(line2, 9, 'G'); // check digit 6 read as G
		line2 = Mrz.replaceAt(line2, 43, 'O'); // composite 0 read as O

		assertEquals(LocalDate.of(1974, 8, 12), reader.read(ICAO_LINE1, line2).dateOfBirth());
	}

	@Test
	public void digitsInLetterFieldsAreCorrected() {
		String line1 = ICAO_LINE1;
		line1 = Mrz.replaceAt(line1, 4, '0'); // UTO read as UT0
		line1 = Mrz.replaceAt(line1, 7, '1'); // ERIKSSON read as ER1K5SON
		line1 = Mrz.replaceAt(line1, 9, '5');
		String line2 = Mrz.replaceAt(ICAO_LINE2, 12, '0');

		PassportMrz mrz = reader.read(line1, line2);

		assertEquals("UTO", mrz.issuingState());
		assertEquals("UTO", mrz.nationality());
		assertEquals("ERIKSSON", mrz.surname());
	}

	@Test
	public void alphanumericDocumentNumberIsNotCorrected() {
		// L898902C3 read as LB98902C3: can't know which is right, so no correction and the check digit fails
		String line2 = Mrz.replaceAt(ICAO_LINE2, 1, 'B');

		assertEquals(Optional.of(MrzField.DOCUMENT_NUMBER), expectFailure(ICAO_LINE1, line2).getField());
	}

	@Test
	public void lowerCaseAndFillerLookalikesAreNormalised() {
		String line1 = ICAO_LINE1.toLowerCase().replace("<<anna", "««anna");
		String line2 = ICAO_LINE2.replace("B<<<<<", "B‹‹‹‹‹");

		PassportMrz mrz = reader.read(line1, line2);

		assertEquals("ERIKSSON", mrz.surname());
		assertEquals("ANNA MARIA", mrz.givenNames());
	}

	@Test
	public void spacesAreHandled() {
		// Full-length line: spaces were fillers
		String line1 = ICAO_LINE1.replace("ERIKSSON<<ANNA<MARIA", "ERIKSSON  ANNA MARIA");
		assertEquals("ANNA MARIA", reader.read(line1, ICAO_LINE2).givenNames());

		// Longer line: a stray space is removed
		String line2 = ICAO_LINE2.substring(0, 10) + " " + ICAO_LINE2.substring(10);
		assertEquals("L898902C3", reader.read(ICAO_LINE1, line2).documentNumber());
	}

	// ------------------------------------------------------------------ format

	@Test
	public void malformedInputIsUnreadable() {
		assertUnreadable(() -> reader.read(ICAO_LINE1, ICAO_LINE2.substring(0, 43)));
		assertUnreadable(() -> reader.read(ICAO_LINE1 + "<", ICAO_LINE2));
		assertUnreadable(() -> reader.read(ICAO_LINE1));
		assertUnreadable(() -> reader.read(ICAO_LINE1 + "\n" + ICAO_LINE2 + "\n" + ICAO_LINE2));
		assertUnreadable(() -> reader.read((String) null));
		assertUnreadable(() -> reader.read(null, ICAO_LINE2));
		assertUnreadable(() -> reader.read(Mrz.replaceAt(ICAO_LINE1, 8, '#'), ICAO_LINE2));
		// TD1 (ID card) layout: 3 x 30 characters
		assertUnreadable(() -> reader.read(
				"I<UTOD231458907<<<<<<<<<<<<<<<\n7408122F1204159UTO<<<<<<<<<<<6\nERIKSSON<<ANNA<MARIA<<<<<<<<<<"));
	}

	// ------------------------------------------------------------------ document type

	@Test
	public void nonPassportIsEnrPsp004() {
		for (String code : new String[] { "V<", "I<", "AC" }) {
			MrzException e = expectFailure(code + ICAO_LINE1.substring(2), ICAO_LINE2);

			assertEquals(MrzError.UNSUPPORTED_DOCUMENT_TYPE, e.getError());
			assertEquals(EnrolmentErrorCode.PSP_004, e.getErrorCode());
		}
	}

	@Test
	public void passportSubtypeIsAccepted() {
		assertEquals("PD", reader.read("PD" + ICAO_LINE1.substring(2), ICAO_LINE2).documentType());
	}

	@Test
	public void checkDigitsAreCheckedBeforeDocumentType() {
		MrzException e = expectFailure("V<" + ICAO_LINE1.substring(2), Mrz.replaceAt(ICAO_LINE2, 43, '9'));

		assertEquals(MrzError.CHECK_DIGIT_FAILED, e.getError());
	}

	// ------------------------------------------------------------------ dates

	@Test
	public void dateOfBirthCenturyRule() {
		String[][] cases = { { "261118", "2026-11-18" }, // today: this century
				{ "261119", "1926-11-19" }, // tomorrow in 20YY: last century
				{ "100101", "2010-01-01" }, { "000229", "2000-02-29" }, { "991231", "1999-12-31" },
				{ "270101", "1927-01-01" } };
		for (String[] c : cases) {
			assertEquals(c[0], LocalDate.parse(c[1]), readWithDates(c[0], "310630").dateOfBirth());
		}
	}

	@Test
	public void expiryIsAlways20yy() {
		String[][] cases = { { "310630", "2031-06-30" }, { "991231", "2099-12-31" }, { "120415", "2012-04-15" } };
		for (String[] c : cases) {
			assertEquals(c[0], LocalDate.parse(c[1]), readWithDates("900115", c[0]).expiryDate());
		}
	}

	@Test
	public void invalidDatesAreEnrPsp003() {
		for (String dob : new String[] { "741332", "740230", "740012", "<<<<<<" }) {
			assertInvalidDate(() -> readWithDates(dob, "310630"));
		}
		assertInvalidDate(() -> readWithDates("900115", "310631"));
	}

	// ------------------------------------------------------------------ names

	@Test
	public void namesAreSplitIntoSurnameAndGivenNames() {
		PassportMrz multi = readName("VAN<DER<BERG<<JAN<PIETER");
		assertEquals("VAN DER BERG", multi.surname());
		assertEquals("JAN PIETER", multi.givenNames());

		PassportMrz single = readName("MADONNA");
		assertEquals("MADONNA", single.surname());
		assertEquals("", single.givenNames());
	}

	@Test
	public void nameFillingTheWholeFieldIsFlaggedAsTruncated() {
		String name = "WOLFESCHLEGELSTEINHAUSEN<<HUBERT<BLAINE<W"; // 41 chars, cut to 39
		PassportMrz mrz = readName(name.substring(0, 39));

		assertTrue(mrz.nameTruncated());
		assertEquals("WOLFESCHLEGELSTEINHAUSEN", mrz.surname());
		assertEquals("HUBERT BLAINE", mrz.givenNames());
	}

	// ------------------------------------------------------------------ no personal data in logs

	@Test
	public void noMrzDataInToStringOrExceptionMessage() {
		String text = reader.read(ICAO_LINE1, ICAO_LINE2).toString();
		assertFalse(text.contains("L898902C3"));
		assertFalse(text.contains("ERIKSSON"));

		MrzException e = expectFailure(ICAO_LINE1, Mrz.replaceAt(ICAO_LINE2, 9, '7'));
		assertEquals("CHECK_DIGIT_FAILED (DOCUMENT_NUMBER)", e.getMessage());
	}

	// ------------------------------------------------------------------ helpers

	private PassportMrz readWithDates(String dob, String expiry) {
		return reader.read(Mrz.line1("P", "BHS", "DOE<<JANE"), Mrz.line2("AB1234567", "BHS", dob, 'F', expiry, "12345"));
	}

	private PassportMrz readName(String nameField) {
		return reader.read(Mrz.line1("P", "BHS", nameField),
				Mrz.line2("AB1234567", "BHS", "900115", 'M', "310630", ""));
	}

	private MrzException expectFailure(String line1, String line2) {
		try {
			reader.read(line1, line2);
		} catch (MrzException e) {
			return e;
		}
		fail("expected MrzException");
		return null;
	}

	private static void assertUnreadable(Runnable call) {
		assertError(MrzError.MRZ_UNREADABLE, call);
	}

	private static void assertInvalidDate(Runnable call) {
		assertError(MrzError.INVALID_DATE, call);
	}

	private static void assertError(MrzError expected, Runnable call) {
		try {
			call.run();
			fail("expected " + expected);
		} catch (MrzException e) {
			assertEquals(expected, e.getError());
			assertEquals(EnrolmentErrorCode.PSP_003, e.getErrorCode());
		}
	}

	/**
	 * Builds TD3 lines for tests. Uses its own check-digit code (not {@link MrzCheckDigit}) so the tests
	 * don't just repeat the code under test.
	 */
	static final class Mrz {

		private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

		static String line1(String type, String state, String name) {
			return pad(pad(type, 2) + pad(state, 3) + name, 44).substring(0, 44);
		}

		static String line2(String docNo, String nationality, String dob, char sex, String expiry, String optional) {
			String doc = pad(docNo, 9) + cd(pad(docNo, 9));
			String birth = dob + cd(dob);
			String opt = pad(optional, 14);
			String exp = expiry + cd(expiry) + opt + (optional.isEmpty() ? "<" : cd(opt));
			return doc + pad(nationality, 3) + birth + sex + exp + cd(doc + birth + exp);
		}

		static String replaceAt(String s, int index, char c) {
			return s.substring(0, index) + c + s.substring(index + 1);
		}

		private static String pad(String s, int length) {
			return s + "<".repeat(Math.max(0, length - s.length()));
		}

		private static String cd(String s) {
			int[] weights = { 7, 3, 1 };
			int sum = 0;
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				sum += (c == '<' ? 0 : CHARS.indexOf(c)) * weights[i % 3];
			}
			return String.valueOf(sum % 10);
		}
	}
}
