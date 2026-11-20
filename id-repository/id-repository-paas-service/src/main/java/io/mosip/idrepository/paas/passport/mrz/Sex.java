package io.mosip.idrepository.paas.passport.mrz;

/** Sex as encoded in the MRZ: {@code M}, {@code F} or filler {@code <} (unspecified). */
public enum Sex {
	MALE,
	FEMALE,
	UNSPECIFIED;

	static Sex fromMrz(char c) {
		return switch (c) {
		case 'M' -> MALE;
		case 'F' -> FEMALE;
		case '<' -> UNSPECIFIED;
		default -> throw new MrzException(MrzError.MRZ_UNREADABLE);
		};
	}
}
