package io.mosip.idrepository.paas.passport.compare;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts a printed name to MRZ form so it can be compared with the MRZ: upper case, accents removed, spaces
 * and hyphens become {@code <}, apostrophes and other punctuation dropped.
 *
 * <p>Some letters have two accepted MRZ spellings (ICAO 9303 Part 3), e.g. {@code Ä} as {@code A} or
 * {@code AE}, so {@link #variants(String)} returns every spelling to try.
 */
final class MrzNameFormat {

	/** Letters with no decomposed form: plain spelling. */
	private static final Map<Character, String> PLAIN = Map.of('Æ', "AE", 'Ø', "O", 'Œ', "OE", 'Þ', "TH", 'Ð', "D",
			'Đ', "D", 'Ł', "L");

	/** ICAO recommended transliterations that differ from simply removing the accent. */
	private static final Map<Character, String> ICAO = Map.of('Ä', "AE", 'Ö', "OE", 'Ü', "UE", 'Å', "AA", 'Æ', "AE",
			'Ø', "OE", 'Œ', "OE", 'Þ', "TH", 'Ð', "D", 'Đ', "D");

	private MrzNameFormat() {
	}

	/** All MRZ spellings of a printed name (usually one). Empty spellings are not returned. */
	static Set<String> variants(String printed) {
		String upper = printed.toUpperCase(Locale.ROOT); // also turns ß into SS
		Set<String> result = new LinkedHashSet<>();
		result.add(toMrz(upper, PLAIN));
		result.add(toMrz(upper, ICAO));
		result.remove("");
		return result;
	}

	/** MRZ-form name from the parsed MRZ value (words separated by spaces). */
	static String fromParsedMrz(String words) {
		return words.trim().replaceAll(" +", "<");
	}

	private static String toMrz(String upper, Map<Character, String> transliteration) {
		StringBuilder sb = new StringBuilder(upper.length());
		for (int i = 0; i < upper.length(); i++) {
			char c = upper.charAt(i);
			String replacement = transliteration.get(c);
			if (replacement == null) {
				replacement = PLAIN.get(c);
			}
			sb.append(replacement != null ? replacement : String.valueOf(c));
		}
		String s = Normalizer.normalize(sb, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		s = s.replaceAll("[\\s\\-]+", "<"); // spaces and hyphens separate name parts
		s = s.replaceAll("[^A-Z<]", ""); // apostrophes, dots, commas etc. are dropped
		s = s.replaceAll("<+", "<");
		return stripFillers(s);
	}

	private static String stripFillers(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && s.charAt(start) == '<') {
			start++;
		}
		while (end > start && s.charAt(end - 1) == '<') {
			end--;
		}
		return s.substring(start, end);
	}
}
