package io.mosip.idrepository.paas.passport.compare;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A date as printed in the visual zone, e.g. {@code 12 AUG 1974}, {@code 12 AUG/AOUT 74} or {@code 12AUG74}. A
 * two-digit year stays two digits and is compared only with the last two digits of the MRZ year, so no century
 * guess is needed.
 */
record PrintedDate(int day, int month, int year, boolean twoDigitYear) {

	private static final Pattern FORMAT = Pattern.compile("^(\\d{1,2}) ?([A-Z]+(?:/[A-Z]+)*) ?(\\d{4}|\\d{2})$");

	private static final Map<String, Integer> MONTHS = Map.ofEntries(Map.entry("JAN", 1), Map.entry("FEB", 2),
			Map.entry("MAR", 3), Map.entry("APR", 4), Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7),
			Map.entry("AUG", 8), Map.entry("SEP", 9), Map.entry("SEPT", 9), Map.entry("OCT", 10),
			Map.entry("NOV", 11), Map.entry("DEC", 12));

	/** Parses a printed date; empty if the format isn't recognised. */
	static Optional<PrintedDate> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		String s = Normalizer.normalize(text.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "") // AOÛT -> AOUT
				.replaceAll("[.,\\-]", " ")
				.replaceAll("\\s*/\\s*", "/")
				.replaceAll("\\s+", " ")
				.trim();
		Matcher m = FORMAT.matcher(s);
		if (!m.matches()) {
			return Optional.empty();
		}
		Integer month = null;
		for (String part : m.group(2).split("/")) { // bilingual months, e.g. AUG/AOUT
			month = MONTHS.get(part);
			if (month != null) {
				break;
			}
		}
		int day = Integer.parseInt(m.group(1));
		if (month == null || day < 1 || day > 31) {
			return Optional.empty();
		}
		String year = m.group(3);
		return Optional.of(new PrintedDate(day, month, Integer.parseInt(year), year.length() == 2));
	}

	/** True if this printed date is the same day as {@code date}. */
	boolean sameDayAs(LocalDate date) {
		int expectedYear = twoDigitYear ? date.getYear() % 100 : date.getYear();
		return day == date.getDayOfMonth() && month == date.getMonthValue() && year == expectedYear;
	}
}
