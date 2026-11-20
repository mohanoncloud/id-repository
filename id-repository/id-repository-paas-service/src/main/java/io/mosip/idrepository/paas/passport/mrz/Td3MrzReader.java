package io.mosip.idrepository.paas.passport.mrz;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Reads and validates a TD3 (passport, 2 x 44 characters) MRZ as defined in ICAO 9303 Part 4 (MP-261).
 *
 * <p>Steps, in order; the first failure stops the rest:
 * <ol>
 *   <li>Normalise the OCR text: upper case, filler look-alikes to {@code <}, spaces handled.</li>
 *   <li>Correct common OCR mix-ups, only where the position's character type is known (letters vs
 *       digits). Alphanumeric fields (document number, optional data) are left as read.</li>
 *   <li>Check both lines are 44 characters of {@code A-Z 0-9 <} → else ENR-PSP-003.</li>
 *   <li>Check every check digit: document number, date of birth, expiry date, optional data (when it has
 *       a check digit) and the composite → else ENR-PSP-003.</li>
 *   <li>Check the document type is {@code P} → else ENR-PSP-004.</li>
 *   <li>Parse the fields and convert the dates.</li>
 * </ol>
 *
 * <p>Thread-safe. Never logs or puts MRZ content in exception messages.
 */
@Component
public class Td3MrzReader {

	static final int LINE_LENGTH = 44;

	private static final Pattern VALID_LINE = Pattern.compile("[A-Z0-9<]{" + LINE_LENGTH + "}");

	/** Characters OCR commonly returns for the filler {@code <}. None of them is a valid MRZ character. */
	private static final String FILLER_LOOKALIKES = "«‹˂＜";

	/** Letters OCR confuses with digits; applied only in digit-only positions. */
	private static final Map<Character, Character> TO_DIGIT = Map.of('O', '0', 'Q', '0', 'D', '0', 'I', '1', 'L',
			'1', 'Z', '2', 'S', '5', 'G', '6', 'B', '8');

	/** Digits OCR confuses with letters; applied only in letter-only positions. */
	private static final Map<Character, Character> TO_LETTER = Map.of('0', 'O', '1', 'I', '2', 'Z', '5', 'S', '6',
			'G', '8', 'B');

	/** Character type of each MRZ position, used to decide which OCR correction is safe. */
	private enum CharType {
		/** Letters or filler: no digits allowed. */
		ALPHA,
		/** Digits only. */
		NUMERIC,
		/** Digit or filler (optional data check digit). */
		NUMERIC_OR_FILLER,
		/** Letters and digits both valid: no safe correction. */
		ALNUM,
		/** Not corrected (sex: M, F or filler). */
		NONE
	}

	private static final CharType[] LINE1_TYPES = new CharType[LINE_LENGTH];
	private static final CharType[] LINE2_TYPES = new CharType[LINE_LENGTH];

	static {
		// Line 1: document code (0-1), issuing state (2-4), name (5-43). All letters or filler.
		Arrays.fill(LINE1_TYPES, CharType.ALPHA);

		// Line 2
		Arrays.fill(LINE2_TYPES, 0, 9, CharType.ALNUM); // document number
		LINE2_TYPES[9] = CharType.NUMERIC; // check digit
		Arrays.fill(LINE2_TYPES, 10, 13, CharType.ALPHA); // nationality
		Arrays.fill(LINE2_TYPES, 13, 19, CharType.NUMERIC); // date of birth
		LINE2_TYPES[19] = CharType.NUMERIC; // check digit
		LINE2_TYPES[20] = CharType.NONE; // sex
		Arrays.fill(LINE2_TYPES, 21, 27, CharType.NUMERIC); // expiry date
		LINE2_TYPES[27] = CharType.NUMERIC; // check digit
		Arrays.fill(LINE2_TYPES, 28, 42, CharType.ALNUM); // optional data
		LINE2_TYPES[42] = CharType.NUMERIC_OR_FILLER; // optional data check digit
		LINE2_TYPES[43] = CharType.NUMERIC; // composite check digit
	}

	private final Clock clock;

	/** @param clock gives "today" for the date-of-birth century rule */
	public Td3MrzReader(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Reads an MRZ given as OCR text with the two lines separated by a line break. Blank lines are ignored;
	 * anything other than exactly two lines is unreadable.
	 */
	public PassportMrz read(String mrzText) {
		if (mrzText == null) {
			throw new MrzException(MrzError.MRZ_UNREADABLE);
		}
		List<String> lines = mrzText.lines().map(String::strip).filter(Predicate.not(String::isEmpty)).toList();
		if (lines.size() != 2) {
			throw new MrzException(MrzError.MRZ_UNREADABLE);
		}
		return read(lines.get(0), lines.get(1));
	}

	/** Reads an MRZ given as its two lines. */
	public PassportMrz read(String rawLine1, String rawLine2) {
		String line1 = correct(normalise(rawLine1), LINE1_TYPES);
		String line2 = correct(normalise(rawLine2), LINE2_TYPES);

		if (!VALID_LINE.matcher(line1).matches() || !VALID_LINE.matcher(line2).matches()) {
			throw new MrzException(MrzError.MRZ_UNREADABLE);
		}

		verifyCheckDigits(line2);

		if (line1.charAt(0) != 'P') {
			throw new MrzException(MrzError.UNSUPPORTED_DOCUMENT_TYPE);
		}

		return parse(line1, line2);
	}

	// ---------------------------------------------------------------- normalise and correct

	static String normalise(String raw) {
		if (raw == null) {
			throw new MrzException(MrzError.MRZ_UNREADABLE);
		}
		String s = raw.strip().toUpperCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			sb.append(FILLER_LOOKALIKES.indexOf(c) >= 0 ? '<' : c);
		}
		s = sb.toString();
		// A full-length line with spaces: OCR read fillers as spaces. Otherwise spaces are noise.
		s = s.length() == LINE_LENGTH ? s.replaceAll("\\s", "<") : s.replaceAll("\\s", "");
		if (s.length() != LINE_LENGTH) {
			throw new MrzException(MrzError.MRZ_UNREADABLE);
		}
		return s;
	}

	private static String correct(String line, CharType[] types) {
		char[] chars = line.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			chars[i] = switch (types[i]) {
			case ALPHA -> TO_LETTER.getOrDefault(chars[i], chars[i]);
			case NUMERIC, NUMERIC_OR_FILLER -> TO_DIGIT.getOrDefault(chars[i], chars[i]);
			case ALNUM, NONE -> chars[i];
			};
		}
		return new String(chars);
	}

	// ---------------------------------------------------------------- check digits

	private static void verifyCheckDigits(String line2) {
		verify(line2.substring(0, 9), line2.charAt(9), MrzField.DOCUMENT_NUMBER);
		verify(line2.substring(13, 19), line2.charAt(19), MrzField.DATE_OF_BIRTH);
		verify(line2.substring(21, 27), line2.charAt(27), MrzField.EXPIRY_DATE);

		// Optional data check digit may be a filler when the state doesn't use one.
		char optionalCheckDigit = line2.charAt(42);
		if (optionalCheckDigit != '<') {
			verify(line2.substring(28, 42), optionalCheckDigit, MrzField.OPTIONAL_DATA);
		}

		String composite = line2.substring(0, 10) + line2.substring(13, 20) + line2.substring(21, 43);
		verify(composite, line2.charAt(43), MrzField.COMPOSITE);
	}

	private static void verify(String data, char checkDigit, MrzField field) {
		if (!MrzCheckDigit.matches(data, checkDigit)) {
			throw new MrzException(MrzError.CHECK_DIGIT_FAILED, field);
		}
	}

	// ---------------------------------------------------------------- parse

	private PassportMrz parse(String line1, String line2) {
		String nameField = line1.substring(5);
		boolean nameTruncated = nameField.charAt(nameField.length() - 1) != '<';
		String names = stripTrailingFillers(nameField);
		int separator = names.indexOf("<<");
		String surname = separator < 0 ? names : names.substring(0, separator);
		String givenNames = separator < 0 ? "" : names.substring(separator + 2);

		return new PassportMrz(stripTrailingFillers(line1.substring(0, 2)),
				stripTrailingFillers(line1.substring(2, 5)), toWords(surname), toWords(givenNames), nameTruncated,
				stripTrailingFillers(line2.substring(0, 9)), stripTrailingFillers(line2.substring(10, 13)),
				parseDateOfBirth(line2.substring(13, 19)), Sex.fromMrz(line2.charAt(20)),
				parseExpiryDate(line2.substring(21, 27)), stripTrailingFillers(line2.substring(28, 42)));
	}

	/** Date of birth: 20YY, unless that is after today, in which case 19YY. */
	private LocalDate parseDateOfBirth(String yymmdd) {
		int[] p = splitDate(yymmdd);
		LocalDate date = toDate(2000 + p[0], p[1], p[2]);
		return date.isAfter(LocalDate.now(clock)) ? toDate(1900 + p[0], p[1], p[2]) : date;
	}

	/** Expiry date: always 20YY. */
	private static LocalDate parseExpiryDate(String yymmdd) {
		int[] p = splitDate(yymmdd);
		return toDate(2000 + p[0], p[1], p[2]);
	}

	private static int[] splitDate(String yymmdd) {
		// Fillers here mean an unknown date, which can't be enrolled.
		if (!yymmdd.chars().allMatch(Character::isDigit)) {
			throw new MrzException(MrzError.INVALID_DATE);
		}
		return new int[] { Integer.parseInt(yymmdd.substring(0, 2)), Integer.parseInt(yymmdd.substring(2, 4)),
				Integer.parseInt(yymmdd.substring(4, 6)) };
	}

	private static LocalDate toDate(int year, int month, int day) {
		try {
			return LocalDate.of(year, month, day);
		} catch (DateTimeException e) {
			throw new MrzException(MrzError.INVALID_DATE);
		}
	}

	private static String stripTrailingFillers(String s) {
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == '<') {
			end--;
		}
		return s.substring(0, end);
	}

	private static String toWords(String s) {
		return s.replace('<', ' ').trim().replaceAll(" +", " ");
	}
}
