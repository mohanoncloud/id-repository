package io.mosip.idrepository.paas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import org.junit.Test;

import io.mosip.idrepository.paas.dto.device.PublicKeyJwk;

public class DeviceKeyTest {

	/** The example key and its thumbprint from RFC 9449 (DPoP). */
	private static final String RFC_X = "l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs";
	private static final String RFC_Y = "9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA";
	private static final String RFC_JKT = "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I";

	private static PublicKeyJwk jwk(String kty, String crv, String x, String y) {
		PublicKeyJwk jwk = new PublicKeyJwk();
		jwk.setKty(kty);
		jwk.setCrv(crv);
		jwk.setX(x);
		jwk.setY(y);
		return jwk;
	}

	@Test
	public void thumbprintMatchesTheRfc9449Example() {
		DeviceKey key = DeviceKey.of(jwk("EC", "P-256", RFC_X, RFC_Y));

		assertEquals(RFC_JKT, key.jkt());
		assertEquals("{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"" + RFC_X + "\",\"y\":\"" + RFC_Y + "\"}",
				key.canonicalJwk());
	}

	@Test
	public void aRealGeneratedKeyIsAccepted() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		for (int i = 0; i < 20; i++) {
			ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
			String x = coordinate(key.getW().getAffineX());
			String y = coordinate(key.getW().getAffineY());

			DeviceKey deviceKey = DeviceKey.of(jwk("EC", "P-256", x, y));

			String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(("{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x + "\",\"y\":\"" + y + "\"}")
							.getBytes(StandardCharsets.UTF_8)));
			assertEquals(expected, deviceKey.jkt());
		}
	}

	/** 32 bytes, big-endian, leading zeros kept. */
	private static String coordinate(java.math.BigInteger value) {
		byte[] raw = value.toByteArray();
		byte[] fixed = new byte[32];
		int copy = Math.min(raw.length, 32);
		System.arraycopy(raw, raw.length - copy, fixed, 32 - copy, copy);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(fixed);
	}

	@Test
	public void aPointNotOnTheCurveIsRejected() {
		// Valid x, y with its first character changed.
		assertThrows(IllegalArgumentException.class,
				() -> DeviceKey.of(jwk("EC", "P-256", RFC_X, "A" + RFC_Y.substring(1))));
	}

	@Test
	public void anotherSpellingOfTheSameBytesIsRejected() {
		// The last base64url character of a 32-byte value has 2 spare bits; 's' becomes 't' (spare bits 01).
		// Accepting it would give the same key a second thumbprint and so a second registration.
		assertThrows(IllegalArgumentException.class,
				() -> DeviceKey.of(jwk("EC", "P-256", RFC_X.substring(0, 42) + "t", RFC_Y)));
	}

	@Test
	public void wrongLengthOrEncodingIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("EC", "P-256", RFC_X.substring(1), RFC_Y)));
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("EC", "P-256", RFC_X + "=", RFC_Y)));
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("EC", "P-256", "+" + RFC_X.substring(1), RFC_Y)));
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("EC", "P-256", null, RFC_Y)));
	}

	@Test
	public void onlyEcP256IsAccepted() {
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("RSA", "P-256", RFC_X, RFC_Y)));
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk("EC", "P-384", RFC_X, RFC_Y)));
		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(null));
	}

	@Test
	public void aKeyWithPrivateMaterialIsRejected() {
		PublicKeyJwk jwk = jwk("EC", "P-256", RFC_X, RFC_Y);
		jwk.setD("c2VjcmV0");

		assertThrows(IllegalArgumentException.class, () -> DeviceKey.of(jwk));
	}
}
