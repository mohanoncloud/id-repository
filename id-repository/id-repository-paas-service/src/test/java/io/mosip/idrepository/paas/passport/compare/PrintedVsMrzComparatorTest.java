package io.mosip.idrepository.paas.passport.compare;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.mrz.PassportMrz;
import io.mosip.idrepository.paas.passport.mrz.Sex;
import io.mosip.idrepository.paas.passport.mrz.Td3MrzReader;
import io.mosip.idrepository.paas.spi.PassportImageReader.OcrField;

/** Printed vs MRZ rules (MP-257): exact fields, MRZ name form, truncation, "can't tell" means retake. */
public class PrintedVsMrzComparatorTest {

	private static final double HIGH = 0.98;

	private static final PrintedVsMrzComparator COMPARATOR = new PrintedVsMrzComparator(
			properties(Map.of("BHS", List.of("BAHAMIAN", "BAHAMAS"), "UTO", List.of("UTOPIAN"))));

	/** MRZ of the ICAO 9303 specimen, read by the real reader. */
	private static final PassportMrz ICAO_MRZ = new Td3MrzReader(
			Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC))
			.read("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<10");

	private static PassportProperties properties(Map<String, List<String>> nationalityNames) {
		PassportProperties properties = new PassportProperties();
		properties.setNationalityNames(nationalityNames);
		return properties;
	}

	/** Printed details that match the ICAO specimen. */
	private static Map<String, OcrField> icaoPrinted() {
		Map<String, OcrField> printed = new HashMap<>();
		printed.put("surname", new OcrField("ERIKSSON", HIGH));
		printed.put("givenNames", new OcrField("ANNA MARIA", HIGH));
		printed.put("passportNumber", new OcrField("L898902C3", HIGH));
		printed.put("nationality", new OcrField("UTOPIAN", HIGH));
		printed.put("dateOfBirth", new OcrField("12 AUG 74", HIGH));
		printed.put("sex", new OcrField("F", HIGH));
		printed.put("dateOfExpiry", new OcrField("15 APR 12", HIGH));
		return printed;
	}

	private static Map<String, OcrField> with(VizField field, OcrField value) {
		Map<String, OcrField> printed = icaoPrinted();
		printed.put(field.key(), value);
		return printed;
	}

	private static Map<String, OcrField> with(VizField field, String text) {
		return with(field, new OcrField(text, HIGH));
	}

	private static PassportMrz mrzWithName(String surname, String givenNames, boolean truncated) {
		return new PassportMrz("P", "BHS", surname, givenNames, truncated, "AB1234567", "BHS",
				LocalDate.of(1990, 1, 15), Sex.MALE, LocalDate.of(2031, 6, 30), "");
	}

	private static Map<String, OcrField> printedWithName(String surname, String givenNames) {
		Map<String, OcrField> printed = new HashMap<>();
		printed.put("surname", new OcrField(surname, HIGH));
		if (givenNames != null) {
			printed.put("givenNames", new OcrField(givenNames, HIGH));
		}
		printed.put("passportNumber", new OcrField("AB1234567", HIGH));
		printed.put("nationality", new OcrField("BAHAMIAN", HIGH));
		printed.put("dateOfBirth", new OcrField("15 JAN 1990", HIGH));
		printed.put("sex", new OcrField("M", HIGH));
		printed.put("dateOfExpiry", new OcrField("30 JUN 2031", HIGH));
		return printed;
	}

	private static ComparisonResult compare(Map<String, OcrField> printed, PassportMrz mrz) {
		return COMPARATOR.compare(printed, mrz);
	}

	// ------------------------------------------------------------------ outcome

	@Test
	public void matchingDetailsPass() {
		ComparisonResult result = compare(icaoPrinted(), ICAO_MRZ);
		assertEquals(ComparisonOutcome.MATCH, result.outcome());
		assertEquals(Optional.empty(), result.outcome().errorCode());
		assertTrue(result.fields().values().stream().allMatch(s -> s == FieldStatus.MATCH));
	}

	@Test
	public void eachFieldMismatchGoesToTheAssistedDesk() {
		Map<VizField, String> wrong = Map.of(VizField.SURNAME, "ERIKSON", VizField.GIVEN_NAMES, "ANNA MARIE",
				VizField.DOCUMENT_NUMBER, "L898902C4", VizField.NATIONALITY, "BAHAMIAN", VizField.DATE_OF_BIRTH,
				"13 AUG 1974", VizField.SEX, "M", VizField.EXPIRY_DATE, "15 APR 2013");
		wrong.forEach((field, value) -> {
			ComparisonResult result = compare(with(field, value), ICAO_MRZ);
			assertEquals(field.name(), ComparisonOutcome.ASSISTED_DESK, result.outcome());
			assertEquals(field.name(), Set.of(field), result.mismatchedFields());
			assertEquals(EnrolmentErrorCode.PSP_007, result.outcome().errorCode().orElseThrow());
		});
	}

	@Test
	public void lowConfidenceFieldAsksForARetake() {
		ComparisonResult result = compare(with(VizField.DATE_OF_BIRTH, new OcrField("13 AUG 1974", 0.55)), ICAO_MRZ);
		assertEquals(ComparisonOutcome.RETAKE, result.outcome());
		assertEquals(EnrolmentErrorCode.PSP_002, result.outcome().errorCode().orElseThrow());
		assertEquals(Set.of(VizField.DATE_OF_BIRTH), result.uncertainFields());
		assertTrue(result.mismatchedFields().isEmpty());
	}

	@Test
	public void missingOrBlankFieldAsksForARetake() {
		assertEquals(ComparisonOutcome.RETAKE, compare(with(VizField.DOCUMENT_NUMBER, (OcrField) null), ICAO_MRZ).outcome());
		assertEquals(ComparisonOutcome.RETAKE, compare(with(VizField.DOCUMENT_NUMBER, "  "), ICAO_MRZ).outcome());
		assertEquals(ComparisonOutcome.RETAKE,
				compare(with(VizField.DOCUMENT_NUMBER, new OcrField(null, HIGH)), ICAO_MRZ).outcome());
	}

	@Test
	public void noPrintedFieldsAtAllAsksForARetake() {
		assertEquals(ComparisonOutcome.RETAKE, compare(null, ICAO_MRZ).outcome());
		assertEquals(ComparisonOutcome.RETAKE, compare(Map.of(), ICAO_MRZ).outcome());
	}

	@Test
	public void confidenceExactlyAtTheThresholdIsReliable() {
		assertEquals(ComparisonOutcome.MATCH, compare(with(VizField.SEX, new OcrField("F", 0.80)), ICAO_MRZ).outcome());
	}

	@Test
	public void reliableMismatchWinsOverAnUncertainField() {
		Map<String, OcrField> printed = with(VizField.SEX, "M");
		printed.put("passportNumber", new OcrField("L898902C3", 0.3));
		ComparisonResult result = compare(printed, ICAO_MRZ);
		assertEquals(ComparisonOutcome.ASSISTED_DESK, result.outcome());
		assertEquals(Set.of(VizField.SEX), result.mismatchedFields());
		assertEquals(Set.of(VizField.DOCUMENT_NUMBER), result.uncertainFields());
	}

	// ------------------------------------------------------------------ requireMatch (what the scan calls)

	@Test
	public void requireMatchReturnsTheResultWhenEverythingMatches() {
		assertEquals(ComparisonOutcome.MATCH, COMPARATOR.requireMatch(icaoPrinted(), ICAO_MRZ).outcome());
	}

	@Test
	public void requireMatchIsEnrPsp007WithFieldNamesOnlyInTheLogMessage() {
		try {
			COMPARATOR.requireMatch(with(VizField.DOCUMENT_NUMBER, "L898902C4"), ICAO_MRZ);
			fail("expected ENR-PSP-007");
		} catch (EnrolmentException e) {
			assertEquals(EnrolmentErrorCode.PSP_007, e.getErrorCode());
			assertEquals("PRINTED_MRZ_MISMATCH [DOCUMENT_NUMBER]", e.getMessage());
		}
	}

	@Test
	public void requireMatchIsEnrPsp002WhenFieldsCantBeRead() {
		try {
			COMPARATOR.requireMatch(with(VizField.SURNAME, new OcrField("ERIKSSON", 0.2)), ICAO_MRZ);
			fail("expected ENR-PSP-002");
		} catch (EnrolmentException e) {
			assertEquals(EnrolmentErrorCode.PSP_002, e.getErrorCode());
			assertEquals("PRINTED_DETAILS_UNREADABLE [SURNAME]", e.getMessage());
		}
	}

	// ------------------------------------------------------------------ names

	@Test
	public void printedNameIsConvertedToMrzForm() {
		String[][] cases = { { "Smith", "SMITH" }, { "SMITH-JONES", "SMITH JONES" }, { "O'NEIL", "ONEIL" },
				{ "St. John", "ST JOHN" }, { "Pérez García", "PEREZ GARCIA" }, { "MÜLLER", "MUELLER" },
				{ "MÜLLER", "MULLER" }, { "STRAßER", "STRASSER" }, { "ØSTERGÅRD", "OSTERGARD" },
				{ "ØSTERGÅRD", "OESTERGAARD" }, { "van der Berg", "VAN DER BERG" } };
		for (String[] c : cases) {
			ComparisonResult result = compare(printedWithName(c[0], "JOHN"), mrzWithName(c[1], "JOHN", false));
			assertEquals(c[0] + " vs " + c[1], FieldStatus.MATCH, result.status(VizField.SURNAME));
		}
	}

	@Test
	public void accentedGivenNames() {
		assertEquals(ComparisonOutcome.MATCH,
				compare(printedWithName("SMITH", "José-Luis"), mrzWithName("SMITH", "JOSE LUIS", false)).outcome());
	}

	@Test
	public void differentNameIsAMismatch() {
		assertEquals(Set.of(VizField.SURNAME),
				compare(printedWithName("SMYTH", "JOHN"), mrzWithName("SMITH", "JOHN", false)).mismatchedFields());
	}

	@Test
	public void extraGivenNameWithoutTruncationIsAMismatch() {
		assertEquals(Set.of(VizField.GIVEN_NAMES),
				compare(printedWithName("SMITH", "JOHN PAUL"), mrzWithName("SMITH", "JOHN", false)).mismatchedFields());
	}

	@Test
	public void truncatedGivenNamesMatchThePrintedStart() {
		PassportMrz truncated = mrzWithName("WOLFESCHLEGELSTEINHAUSEN", "HUBERT BLAINE", true);
		assertEquals(ComparisonOutcome.MATCH,
				compare(printedWithName("Wolfeschlegelsteinhausen", "Hubert Blaine Wolfe"), truncated).outcome());
		assertEquals(Set.of(VizField.GIVEN_NAMES),
				compare(printedWithName("Wolfeschlegelsteinhausen", "Hubert Blake"), truncated).mismatchedFields());
	}

	@Test
	public void truncatedGivenNamesCutMidWord() {
		assertEquals(ComparisonOutcome.MATCH,
				compare(printedWithName("SMITH", "JOHN PAUL"), mrzWithName("SMITH", "JOHN PA", true)).outcome());
	}

	@Test
	public void truncatedNameStillNeedsTheExactSurnameWhenGivenNamesExist() {
		assertEquals(Set.of(VizField.SURNAME),
				compare(printedWithName("SMITHERS", "JOHN PAUL"), mrzWithName("SMITH", "JOHN PA", true)).mismatchedFields());
	}

	@Test
	public void surnameFillingTheWholeFieldIsComparedByItsStart() {
		String mrzSurname = "ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLM"; // 39 letters, cut off
		ComparisonResult result = compare(printedWithName(mrzSurname + "NOPQ", "JOHN"), mrzWithName(mrzSurname, "", true));
		assertEquals(FieldStatus.MATCH, result.status(VizField.SURNAME));
		assertEquals(FieldStatus.NOT_COMPARED, result.status(VizField.GIVEN_NAMES));
		assertEquals(ComparisonOutcome.MATCH, result.outcome());
	}

	@Test
	public void noGivenNamesOnEitherSide() {
		assertEquals(ComparisonOutcome.MATCH,
				compare(printedWithName("MADONNA", null), mrzWithName("MADONNA", "", false)).outcome());
	}

	@Test
	public void givenNamesInTheMrzButNotReadFromThePrintIsUncertain() {
		assertEquals(Set.of(VizField.GIVEN_NAMES),
				compare(printedWithName("SMITH", null), mrzWithName("SMITH", "JOHN", false)).uncertainFields());
	}

	@Test
	public void punctuationOnlyNameIsUncertain() {
		assertEquals(Set.of(VizField.SURNAME),
				compare(printedWithName("--", "JOHN"), mrzWithName("SMITH", "JOHN", false)).uncertainFields());
	}

	// ------------------------------------------------------------------ other fields

	@Test
	public void printedDateFormats() {
		for (String printed : List.of("12 AUG 74", "12 AUG 1974", "12AUG74", "12 aug/août 74", "12 AUG/AOUT 1974",
				"12-AUG-1974")) {
			assertEquals(printed, FieldStatus.MATCH,
					compare(with(VizField.DATE_OF_BIRTH, printed), ICAO_MRZ).status(VizField.DATE_OF_BIRTH));
		}
	}

	@Test
	public void unknownDateFormatIsUncertain() {
		for (String printed : List.of("12.08.1974", "AUG 12 1974", "12 FOO 1974", "32 AUG 1974")) {
			assertEquals(printed, FieldStatus.UNCERTAIN,
					compare(with(VizField.DATE_OF_BIRTH, printed), ICAO_MRZ).status(VizField.DATE_OF_BIRTH));
		}
	}

	@Test
	public void twoDigitYearStillMismatchesOnTheDay() {
		assertEquals(FieldStatus.MISMATCH,
				compare(with(VizField.EXPIRY_DATE, "16 APR 12"), ICAO_MRZ).status(VizField.EXPIRY_DATE));
	}

	@Test
	public void sexFormats() {
		for (String printed : List.of("F", "f", "F/F", "FEMALE")) {
			assertEquals(printed, FieldStatus.MATCH, compare(with(VizField.SEX, printed), ICAO_MRZ).status(VizField.SEX));
		}
	}

	@Test
	public void unspecifiedSexIsPrintedAsX() {
		PassportMrz unspecified = new PassportMrz("P", "BHS", "SMITH", "JOHN", false, "AB1234567", "BHS",
				LocalDate.of(1990, 1, 15), Sex.UNSPECIFIED, LocalDate.of(2031, 6, 30), "");
		Map<String, OcrField> printed = printedWithName("SMITH", "JOHN");
		printed.put("sex", new OcrField("X", HIGH));
		assertEquals(ComparisonOutcome.MATCH, compare(printed, unspecified).outcome());
	}

	@Test
	public void unknownSexTextIsUncertain() {
		assertEquals(FieldStatus.UNCERTAIN, compare(with(VizField.SEX, "?"), ICAO_MRZ).status(VizField.SEX));
	}

	@Test
	public void passportNumberIgnoresSpacesAndCase() {
		assertEquals(FieldStatus.MATCH,
				compare(with(VizField.DOCUMENT_NUMBER, "l898 902c3"), ICAO_MRZ).status(VizField.DOCUMENT_NUMBER));
	}

	@Test
	public void nationalityMatchesTheConfiguredNamesOrTheCode() {
		for (String printed : List.of("UTOPIAN", "utopian", "UTO", "Utopian / Utopienne")) {
			assertEquals(printed, FieldStatus.MATCH,
					compare(with(VizField.NATIONALITY, printed), ICAO_MRZ).status(VizField.NATIONALITY));
		}
	}

	@Test
	public void nationalityOfAnotherCountryIsAMismatch() {
		for (String printed : List.of("BAHAMIAN", "BHS")) {
			assertEquals(printed, FieldStatus.MISMATCH,
					compare(with(VizField.NATIONALITY, printed), ICAO_MRZ).status(VizField.NATIONALITY));
		}
	}

	@Test
	public void nationalityWordWithNoConfiguredMappingIsNotCompared() {
		PrintedVsMrzComparator defaults = new PrintedVsMrzComparator(new PassportProperties()); // BHS words only
		ComparisonResult result = defaults.compare(icaoPrinted(), ICAO_MRZ); // UTOPIAN isn't configured
		assertEquals(FieldStatus.NOT_COMPARED, result.status(VizField.NATIONALITY));
		assertEquals(ComparisonOutcome.MATCH, result.outcome());
	}

	@Test
	public void defaultConfigAcceptsTheBahamianWords() {
		PrintedVsMrzComparator defaults = new PrintedVsMrzComparator(new PassportProperties());
		for (String printed : List.of("BAHAMIAN", "Bahamas", "THE BAHAMAS", "BHS")) {
			Map<String, OcrField> fields = printedWithName("SMITH", "JOHN");
			fields.put("nationality", new OcrField(printed, HIGH));
			assertEquals(printed, FieldStatus.MATCH,
					defaults.compare(fields, mrzWithName("SMITH", "JOHN", false)).status(VizField.NATIONALITY));
		}
	}

	@Test
	public void configuredCodesAreCaseInsensitive() {
		PrintedVsMrzComparator lower = new PrintedVsMrzComparator(properties(Map.of("uto", List.of("utopian"))));
		assertEquals(FieldStatus.MATCH, lower.compare(icaoPrinted(), ICAO_MRZ).status(VizField.NATIONALITY));
	}

	@Test
	public void confidenceOutsideZeroToOneFailsAtStartup() {
		for (double value : new double[] { -0.1, 1.1 }) {
			PassportProperties properties = new PassportProperties();
			properties.setPrintedMinConfidence(value);
			try {
				new PrintedVsMrzComparator(properties);
				fail("expected a startup failure for " + value);
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("printed-min-confidence"));
			}
		}
	}

	// ------------------------------------------------------------------ no personal data in logs

	@Test
	public void resultAndOcrFieldsHaveNoPersonalDataInToString() {
		ComparisonResult result = compare(icaoPrinted(), ICAO_MRZ);
		for (String text : new String[] { result.toString(), new OcrField("ERIKSSON", HIGH).toString() }) {
			assertFalse(text, text.contains("ERIKSSON"));
			assertFalse(text, text.contains("L898902C3"));
		}
	}
}
