package io.mosip.idrepository.paas.util;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Dotted numeric app versions ({@code 1.10.0}), compared part by part as numbers, so {@code 1.10.0} is newer
 * than {@code 1.9.2}. A missing part counts as 0 ({@code 1.2} equals {@code 1.2.0}). Same format as the
 * {@code ck_appcfg_*_version} checks on {@code idpaas.app_config}.
 */
public final class AppVersion {

	private static final Pattern FORMAT = Pattern.compile("^[0-9]+(\\.[0-9]+)*$");

	/** Longer than any real version; stops a huge parameter from being split and parsed. */
	private static final int MAX_LENGTH = 32;

	private AppVersion() {
	}

	public static boolean isValid(String version) {
		return version != null && version.length() <= MAX_LENGTH && FORMAT.matcher(version).matches();
	}

	/**
	 * Negative if {@code a} is older than {@code b}, 0 if they are the same version, positive if newer.
	 *
	 * @throws IllegalArgumentException if either is not a valid version
	 */
	public static int compare(String a, String b) {
		if (!isValid(a) || !isValid(b)) {
			throw new IllegalArgumentException("Not a dotted numeric version");
		}
		String[] left = a.split("\\.");
		String[] right = b.split("\\.");
		for (int i = 0; i < Math.max(left.length, right.length); i++) {
			// BigInteger: a part can have more digits than a long holds, and leading zeros are ignored.
			BigInteger l = i < left.length ? new BigInteger(left[i]) : BigInteger.ZERO;
			BigInteger r = i < right.length ? new BigInteger(right[i]) : BigInteger.ZERO;
			int result = l.compareTo(r);
			if (result != 0) {
				return result;
			}
		}
		return 0;
	}

	/** True if {@code version} is older than {@code than}. */
	public static boolean isOlder(String version, String than) {
		return compare(version, than) < 0;
	}
}
