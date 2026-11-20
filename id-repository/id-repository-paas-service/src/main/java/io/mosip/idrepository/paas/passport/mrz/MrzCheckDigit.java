package io.mosip.idrepository.paas.passport.mrz;

/**
 * ICAO 9303 check digit: each character is valued (0-9 as is, A-Z as 10-35, filler {@code <} as 0),
 * multiplied by the repeating weights 7, 3, 1, summed, and taken modulo 10.
 */
public final class MrzCheckDigit {

	private static final int[] WEIGHTS = { 7, 3, 1 };

	private MrzCheckDigit() {
	}

	/** Computes the check digit (0-9) for the given MRZ characters. */
	public static int compute(CharSequence data) {
		int sum = 0;
		for (int i = 0; i < data.length(); i++) {
			sum += valueOf(data.charAt(i)) * WEIGHTS[i % 3];
		}
		return sum % 10;
	}

	/** True if {@code checkDigit} is a digit equal to the computed check digit of {@code data}. */
	public static boolean matches(CharSequence data, char checkDigit) {
		return checkDigit >= '0' && checkDigit <= '9' && compute(data) == checkDigit - '0';
	}

	static int valueOf(char c) {
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'A' && c <= 'Z') {
			return c - 'A' + 10;
		}
		if (c == '<') {
			return 0;
		}
		throw new IllegalArgumentException("Not an MRZ character");
	}
}
