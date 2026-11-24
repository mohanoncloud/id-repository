package io.mosip.idrepository.paas.passport.compare;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.exception.EnrolmentException;
import io.mosip.idrepository.paas.passport.mrz.PassportMrz;
import io.mosip.idrepository.paas.passport.mrz.Sex;
import io.mosip.idrepository.paas.spi.PassportImageReader.OcrField;

/**
 * Compares the printed (visual zone) details with the validated MRZ (MP-257, ENR-PSP-007).
 *
 * <ul>
 *   <li>Passport number, date of birth, expiry date, sex, nationality: exact match. Dates are compared as dates,
 *       whatever the printed format; a two-digit printed year is compared with the last two digits of the MRZ
 *       year.</li>
 *   <li>Names: the printed name is converted to MRZ form first (upper case, accents removed, spaces and hyphens
 *       to {@code <}). If the MRZ name was truncated, the printed name only has to start with the MRZ name.</li>
 *   <li>Nationality: the printed word (e.g. BAHAMIAN) is matched through the configured names for the MRZ code.
 *       A word with no configured mapping is NOT_COMPARED rather than a mismatch.</li>
 *   <li>A field that is missing, below the confidence threshold or in an unknown format is UNCERTAIN: it leads to
 *       a retake (ENR-PSP-002), never to the assisted desk.</li>
 * </ul>
 *
 * <p>The MRZ must already be read and validated by {@code Td3MrzReader}, so an invalid MRZ is rejected before
 * any comparison.
 */
@Component
public class PrintedVsMrzComparator {

	private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{3}");

	private final double minConfidence;
	private final Map<String, Set<String>> nationalityNames;

	/** Fails at startup on a confidence outside 0.0 to 1.0. */
	public PrintedVsMrzComparator(PassportProperties properties) {
		double confidence = properties.getPrintedMinConfidence();
		if (confidence < 0.0 || confidence > 1.0) {
			throw new IllegalArgumentException("mosip.idpaas.passport.printed-min-confidence must be between 0 and 1");
		}
		this.minConfidence = confidence;
		Map<String, Set<String>> names = new LinkedHashMap<>();
		properties.getNationalityNames().forEach((code, words) -> names.put(code.toUpperCase(Locale.ROOT),
				words.stream().map(PrintedVsMrzComparator::normaliseWords).collect(Collectors.toUnmodifiableSet())));
		this.nationalityNames = Map.copyOf(names);
	}

	/**
	 * Fails the scan unless the printed details match the MRZ. Only field names go in the log message, never
	 * values.
	 *
	 * @param printedFields printed fields from OCR, keyed by {@link VizField#key()}; null is treated as empty
	 * @throws EnrolmentException ENR-PSP-007 on a reliable mismatch, ENR-PSP-002 if fields couldn't be read
	 */
	public ComparisonResult requireMatch(Map<String, OcrField> printedFields, PassportMrz mrz) {
		ComparisonResult result = compare(printedFields, mrz);
		switch (result.outcome()) {
		case ASSISTED_DESK -> throw new EnrolmentException(result.outcome().errorCode().orElseThrow(),
				"PRINTED_MRZ_MISMATCH " + result.mismatchedFields());
		case RETAKE -> throw new EnrolmentException(result.outcome().errorCode().orElseThrow(),
				"PRINTED_DETAILS_UNREADABLE " + result.uncertainFields());
		default -> {
			return result;
		}
		}
	}

	public ComparisonResult compare(Map<String, OcrField> printedFields, PassportMrz mrz) {
		Map<String, OcrField> printed = printedFields == null ? Map.of() : printedFields;
		Map<VizField, FieldStatus> fields = new EnumMap<>(VizField.class);
		compareNames(printed.get(VizField.SURNAME.key()), printed.get(VizField.GIVEN_NAMES.key()), mrz, fields);
		fields.put(VizField.DOCUMENT_NUMBER,
				compareDocumentNumber(printed.get(VizField.DOCUMENT_NUMBER.key()), mrz.documentNumber()));
		fields.put(VizField.NATIONALITY, compareNationality(printed.get(VizField.NATIONALITY.key()), mrz.nationality()));
		fields.put(VizField.DATE_OF_BIRTH, compareDate(printed.get(VizField.DATE_OF_BIRTH.key()), mrz.dateOfBirth()));
		fields.put(VizField.SEX, compareSex(printed.get(VizField.SEX.key()), mrz.sex()));
		fields.put(VizField.EXPIRY_DATE, compareDate(printed.get(VizField.EXPIRY_DATE.key()), mrz.expiryDate()));
		return new ComparisonResult(fields);
	}

	// ------------------------------------------------------------------ names

	private void compareNames(OcrField surname, OcrField givenNames, PassportMrz mrz, Map<VizField, FieldStatus> out) {
		String mrzSurname = MrzNameFormat.fromParsedMrz(mrz.surname());
		String mrzGiven = MrzNameFormat.fromParsedMrz(mrz.givenNames());
		if (mrz.nameTruncated() && mrzGiven.isEmpty()) {
			// The surname filled the field, or the given names were cut off completely.
			out.put(VizField.SURNAME, compareName(surname, mrzSurname, true));
			out.put(VizField.GIVEN_NAMES, FieldStatus.NOT_COMPARED);
			return;
		}
		out.put(VizField.SURNAME, compareName(surname, mrzSurname, false));
		if (mrzGiven.isEmpty() && isBlank(givenNames)) {
			out.put(VizField.GIVEN_NAMES, FieldStatus.MATCH); // no given names on either side
		} else {
			out.put(VizField.GIVEN_NAMES, compareName(givenNames, mrzGiven, mrz.nameTruncated()));
		}
	}

	private FieldStatus compareName(OcrField printed, String mrzName, boolean truncated) {
		if (!reliable(printed)) {
			return FieldStatus.UNCERTAIN;
		}
		Set<String> variants = MrzNameFormat.variants(printed.text());
		if (variants.isEmpty()) {
			return FieldStatus.UNCERTAIN;
		}
		boolean match = variants.stream().anyMatch(v -> truncated ? v.startsWith(mrzName) : v.equals(mrzName));
		return match ? FieldStatus.MATCH : FieldStatus.MISMATCH;
	}

	// ------------------------------------------------------------------ other fields

	private FieldStatus compareDocumentNumber(OcrField printed, String mrzDocumentNumber) {
		if (!reliable(printed)) {
			return FieldStatus.UNCERTAIN;
		}
		String value = printed.text().toUpperCase(Locale.ROOT).replaceAll("[\\s\\-]", "");
		return value.equals(mrzDocumentNumber) ? FieldStatus.MATCH : FieldStatus.MISMATCH;
	}

	private FieldStatus compareNationality(OcrField printed, String mrzCode) {
		if (!reliable(printed)) {
			return FieldStatus.UNCERTAIN;
		}
		Set<String> accepted = nationalityNames.getOrDefault(mrzCode, Set.of());
		boolean recognised = false;
		for (String part : printed.text().split("/")) { // bilingual, e.g. BAHAMIAN/BAHAMIENNE
			String value = normaliseWords(part);
			if (value.equals(mrzCode) || accepted.contains(value)) {
				return FieldStatus.MATCH;
			}
			recognised |= COUNTRY_CODE.matcher(value).matches() || isConfiguredName(value);
		}
		return recognised ? FieldStatus.MISMATCH : FieldStatus.NOT_COMPARED;
	}

	private boolean isConfiguredName(String value) {
		return nationalityNames.values().stream().anyMatch(names -> names.contains(value));
	}

	private FieldStatus compareDate(OcrField printed, LocalDate mrzDate) {
		if (!reliable(printed)) {
			return FieldStatus.UNCERTAIN;
		}
		Optional<PrintedDate> date = PrintedDate.parse(printed.text());
		if (date.isEmpty()) {
			return FieldStatus.UNCERTAIN;
		}
		return date.get().sameDayAs(mrzDate) ? FieldStatus.MATCH : FieldStatus.MISMATCH;
	}

	private FieldStatus compareSex(OcrField printed, Sex mrzSex) {
		if (!reliable(printed)) {
			return FieldStatus.UNCERTAIN;
		}
		String value = printed.text().toUpperCase(Locale.ROOT).trim().split("/")[0].trim(); // e.g. F/F
		Sex sex = switch (value) {
		case "M", "MALE" -> Sex.MALE;
		case "F", "FEMALE" -> Sex.FEMALE;
		case "X", "<", "UNSPECIFIED" -> Sex.UNSPECIFIED;
		default -> null;
		};
		if (sex == null) {
			return FieldStatus.UNCERTAIN;
		}
		return sex == mrzSex ? FieldStatus.MATCH : FieldStatus.MISMATCH;
	}

	private boolean reliable(OcrField field) {
		return !isBlank(field) && field.confidence() >= minConfidence;
	}

	private static boolean isBlank(OcrField field) {
		return field == null || field.text() == null || field.text().isBlank();
	}

	/** Upper case, accents removed, anything but letters to single spaces. */
	static String normaliseWords(String s) {
		String upper = Normalizer.normalize(s.toUpperCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return upper.replaceAll("[^A-Z]+", " ").trim();
	}
}
