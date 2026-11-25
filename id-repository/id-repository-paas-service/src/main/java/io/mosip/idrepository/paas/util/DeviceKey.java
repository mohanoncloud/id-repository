package io.mosip.idrepository.paas.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.util.Base64;

import io.mosip.idrepository.paas.dto.device.PublicKeyJwk;

/**
 * A device public key that has been checked and put in canonical form, with its RFC 7638 JWK thumbprint
 * (SHA-256, base64url). The thumbprint is what the database keeps unique (decision B1), so the key is accepted
 * in one encoding only: exactly 32 bytes for each coordinate, canonical base64url without padding, on the
 * P-256 curve. Without that, the same key written two ways would get two thumbprints and register twice.
 *
 * @param jkt           thumbprint, 43 characters
 * @param canonicalJwk  {@code {"kty":"EC","crv":"P-256","x":"..","y":".."}}, what is stored
 */
public record DeviceKey(String jkt, String canonicalJwk) {

	private static final int COORDINATE_BYTES = 32;
	private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64D = Base64.getUrlDecoder();
	private static final ECParameterSpec P256 = p256();

	/**
	 * @throws IllegalArgumentException if the key is missing, not EC P-256, carries private material, or the
	 *                                  point is not on the curve; the message is safe to log
	 */
	public static DeviceKey of(PublicKeyJwk jwk) {
		if (jwk == null) {
			throw new IllegalArgumentException("public key missing");
		}
		if (jwk.getD() != null) {
			throw new IllegalArgumentException("private key member present");
		}
		if (!"EC".equals(jwk.getKty()) || !"P-256".equals(jwk.getCrv())) {
			throw new IllegalArgumentException("not an EC P-256 key");
		}
		byte[] x = coordinate(jwk.getX());
		byte[] y = coordinate(jwk.getY());
		if (!onCurve(new BigInteger(1, x), new BigInteger(1, y))) {
			throw new IllegalArgumentException("point is not on the P-256 curve");
		}
		String xs = B64.encodeToString(x);
		String ys = B64.encodeToString(y);
		// RFC 7638: required members only, in lexicographic order, no whitespace.
		String thumbprintInput = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + xs + "\",\"y\":\"" + ys + "\"}";
		String jkt = B64.encodeToString(sha256(thumbprintInput.getBytes(StandardCharsets.UTF_8)));
		return new DeviceKey(jkt, "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"" + xs + "\",\"y\":\"" + ys + "\"}");
	}

	private static byte[] coordinate(String value) {
		if (value == null || value.length() != 43) {
			throw new IllegalArgumentException("coordinate is not 32 bytes of base64url");
		}
		byte[] bytes;
		try {
			bytes = B64D.decode(value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("coordinate is not base64url");
		}
		// The last base64 character carries 2 spare bits; a different spelling of the same bytes is refused.
		if (bytes.length != COORDINATE_BYTES || !B64.encodeToString(bytes).equals(value)) {
			throw new IllegalArgumentException("coordinate is not canonical base64url of 32 bytes");
		}
		return bytes;
	}

	/** y² = x³ + ax + b (mod p), with x and y below p. */
	private static boolean onCurve(BigInteger x, BigInteger y) {
		EllipticCurve curve = P256.getCurve();
		BigInteger p = ((ECFieldFp) curve.getField()).getP();
		if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
			return false;
		}
		BigInteger left = y.multiply(y).mod(p);
		BigInteger right = x.pow(3).add(curve.getA().multiply(x)).add(curve.getB()).mod(p);
		return left.equals(right);
	}

	private static byte[] sha256(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(input);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}

	private static ECParameterSpec p256() {
		try {
			AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
			parameters.init(new ECGenParameterSpec("secp256r1"));
			return parameters.getParameterSpec(ECParameterSpec.class);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("P-256 unavailable", e);
		}
	}
}
