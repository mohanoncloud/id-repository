package io.mosip.idrepository.paas.passport.upload;

import static io.mosip.idrepository.paas.TestImages.jpeg;
import static io.mosip.idrepository.paas.TestImages.png;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import io.mosip.idrepository.paas.TestImages;
import io.mosip.idrepository.paas.config.PassportProperties;
import io.mosip.idrepository.paas.constant.EnrolmentErrorCode;

/** File checks (MP-256, ENR-PSP-001): type from content, size, resolution, decompression bomb, metadata removal. */
public class PassportImageValidatorTest {

	private final PassportImageValidator validator = new PassportImageValidator(new PassportProperties());

	// ------------------------------------------------------------------ accepted files

	@Test
	public void acceptsJpeg() {
		SanitisedImage image = validator.validate("frontImage", jpeg(1600, 1200));
		assertEquals(SanitisedImage.Format.JPEG, image.format());
		assertEquals(1600, image.width());
		assertEquals(1200, image.height());
		assertEquals(1, image.exifOrientation());
	}

	@Test
	public void acceptsPng() {
		SanitisedImage image = validator.validate("frontImage", png(1200, 1700));
		assertEquals(SanitisedImage.Format.PNG, image.format());
		assertEquals(1200, image.width());
	}

	@Test
	public void onlyTheLongSideNeedsToReach1200() {
		assertEquals(1200, validator.validate("frontImage", jpeg(1200, 800)).width());
		assertEquals(1200, validator.validate("frontImage", png(700, 1200)).height());
	}

	@Test
	public void fileExactlyAtTheSizeLimitIsAccepted() {
		byte[] file = jpeg(1600, 1200);
		PassportProperties properties = new PassportProperties();
		properties.setImageMaxSize(DataSize.ofBytes(file.length));
		assertEquals(SanitisedImage.Format.JPEG, new PassportImageValidator(properties).validate("frontImage", file).format());
	}

	// ------------------------------------------------------------------ rejected files

	@Test
	public void emptyFile() {
		assertRejected(UploadError.EMPTY_FILE, new byte[0]);
		assertRejected(UploadError.EMPTY_FILE, null);
	}

	@Test
	public void pdfRenamedAsJpg() {
		assertRejected(UploadError.UNSUPPORTED_FILE_TYPE, "%PDF-1.4\n%fake scan".getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	public void gifIsNotAccepted() {
		assertRejected(UploadError.UNSUPPORTED_FILE_TYPE, TestImages.encode(TestImages.image(1300, 1300), "gif"));
	}

	@Test
	public void overFiveMegabytesIsHttp413() {
		byte[] big = Arrays.copyOf(jpeg(1600, 1200), 5 * 1024 * 1024 + 1);
		UploadException e = assertRejected(UploadError.FILE_TOO_LARGE, big);
		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getHttpStatus());
	}

	@Test
	public void otherFileProblemsAreHttp200() {
		assertEquals(HttpStatus.OK, assertRejected(UploadError.RESOLUTION_TOO_LOW, jpeg(1199, 900)).getHttpStatus());
	}

	@Test
	public void longSideUnder1200() {
		assertRejected(UploadError.RESOLUTION_TOO_LOW, jpeg(1199, 900));
		assertRejected(UploadError.RESOLUTION_TOO_LOW, png(800, 1199));
	}

	@Test
	public void decompressionBombIsRejectedFromTheHeaderWithoutDecoding() {
		byte[] bomb = pngWithHeaderOnly(50_000, 50_000); // a tiny file claiming 2.5 gigapixels
		assertTrue(bomb.length < 100);
		assertRejected(UploadError.IMAGE_TOO_LARGE, bomb);
	}

	@Test
	public void garbageAfterJpegMagic() {
		assertRejected(UploadError.MALFORMED_IMAGE,
				new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'x', 'y', 'z' });
	}

	@Test
	public void truncatedPng() {
		byte[] png = png(1200, 1300);
		assertRejected(UploadError.MALFORMED_IMAGE, Arrays.copyOf(png, png.length / 2));
	}

	@Test
	public void limitsComeFromConfig() {
		PassportProperties properties = new PassportProperties();
		properties.setImageMinLongSide(2000);
		properties.setImageMaxPixels(1_000_000L);
		PassportImageValidator strict = new PassportImageValidator(properties);
		try {
			strict.validate("frontImage", jpeg(1600, 1200)); // 1.92 MP > 1 MP
			fail("expected IMAGE_TOO_LARGE");
		} catch (UploadException e) {
			assertEquals(UploadError.IMAGE_TOO_LARGE, e.getError());
		}
	}

	@Test
	public void nonPositiveLimitsFailAtStartup() {
		PassportProperties zeroSide = new PassportProperties();
		zeroSide.setImageMinLongSide(0);
		PassportProperties zeroSize = new PassportProperties();
		zeroSize.setImageMaxSize(DataSize.ofBytes(0));
		for (PassportProperties properties : new PassportProperties[] { zeroSide, zeroSize }) {
			try {
				new PassportImageValidator(properties);
				fail("expected a startup failure");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage().contains("mosip.idpaas.passport.image-"));
			}
		}
	}

	@Test
	public void errorIsEnrPsp001NamingTheReasonAndPartOnly() {
		UploadException e = assertRejected(UploadError.UNSUPPORTED_FILE_TYPE, new byte[] { 1, 2, 3 }, "backImage");
		assertEquals(EnrolmentErrorCode.PSP_001, e.getErrorCode());
		assertEquals("UNSUPPORTED_FILE_TYPE (backImage)", e.getMessage());
		assertEquals(Optional.of("backImage"), e.getPart());
	}

	@Test
	public void malformedImageAlsoNamesThePart() {
		byte[] noImageData = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
		UploadException e = assertRejected(UploadError.MALFORMED_IMAGE, noImageData, "frontImage");
		assertEquals("MALFORMED_IMAGE (frontImage)", e.getMessage());
	}

	// ------------------------------------------------------------------ metadata removal

	@Test
	public void jpegExifAndCommentsAreRemovedAndOrientationIsKept() throws IOException {
		byte[] original = jpeg(1600, 1200);
		byte[] withMetadata = insertAfterSoi(original, segment(0xE1, exif(6, "GPS-LAT-25.0443-LON-77.3504")),
				segment(0xFE, "secret comment".getBytes(StandardCharsets.US_ASCII)));
		assertTrue(contains(withMetadata, "GPS-LAT"));

		SanitisedImage image = validator.validate("frontImage", withMetadata);
		assertFalse(contains(image.data(), "Exif"));
		assertFalse(contains(image.data(), "GPS-LAT"));
		assertFalse(contains(image.data(), "secret comment"));
		assertEquals(6, image.exifOrientation());
		assertEquals(original.length, image.data().length); // only metadata removed, pixels untouched
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.data()));
		assertEquals(1600, decoded.getWidth());
	}

	@Test
	public void littleEndianExifOrientation() {
		byte[] withExif = insertAfterSoi(jpeg(1600, 1200), segment(0xE1, exifLittleEndian(8)));
		assertEquals(8, validator.validate("frontImage", withExif).exifOrientation());
	}

	@Test
	public void xmpIsRemoved() {
		byte[] xmp = "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>secret-location</x:xmpmeta>"
				.getBytes(StandardCharsets.US_ASCII);
		SanitisedImage image = validator.validate("frontImage", insertAfterSoi(jpeg(1600, 1200), segment(0xE1, xmp)));
		assertFalse(contains(image.data(), "secret-location"));
	}

	@Test
	public void pngTextChunksAreRemoved() {
		byte[] withText = insertPngChunkAfterIhdr(png(1200, 1300), "tEXt",
				"Comment\0taken at 25.0443,-77.3504".getBytes(StandardCharsets.ISO_8859_1));
		assertTrue(contains(withText, "25.0443"));

		SanitisedImage image = validator.validate("frontImage", withText);
		assertFalse(contains(image.data(), "25.0443"));
		assertFalse(contains(image.data(), "tEXt"));
		assertEquals(1200, image.width());
	}

	@Test
	public void toStringHasNoImageBytes() {
		SanitisedImage image = validator.validate("frontImage", jpeg(1600, 1200));
		assertTrue(image.toString().startsWith("SanitisedImage[JPEG, 1600x1200, orientation=1, "));
	}

	// ------------------------------------------------------------------ helpers

	private UploadException assertRejected(UploadError expected, byte[] file) {
		return assertRejected(expected, file, "frontImage");
	}

	private UploadException assertRejected(UploadError expected, byte[] file, String part) {
		try {
			validator.validate(part, file);
			fail("expected " + expected);
			return null;
		} catch (UploadException e) {
			assertEquals(expected, e.getError());
			assertEquals(EnrolmentErrorCode.PSP_001, e.getErrorCode());
			return e;
		}
	}

	private static byte[] segment(int marker, byte[] data) {
		ByteBuffer b = ByteBuffer.allocate(4 + data.length);
		b.put((byte) 0xFF).put((byte) marker).putShort((short) (data.length + 2)).put(data);
		return b.array();
	}

	private static byte[] insertAfterSoi(byte[] jpeg, byte[]... segments) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, 2);
		for (byte[] s : segments) {
			out.writeBytes(s);
		}
		out.write(jpeg, 2, jpeg.length - 2);
		return out.toByteArray();
	}

	/** Big-endian EXIF: IFD0 with orientation and a GPS IFD pointer, followed by GPS-like text. */
	private static byte[] exif(int orientation, String gpsText) {
		byte[] gps = gpsText.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer b = ByteBuffer.allocate(6 + 8 + 2 + 2 * 12 + 4 + gps.length);
		b.put(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
		b.put(new byte[] { 'M', 'M', 0, 42 }).putInt(8); // TIFF header, IFD0 at offset 8
		b.putShort((short) 2); // 2 entries
		b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
		b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(8 + 2 + 24 + 4);
		b.putInt(0); // no next IFD
		b.put(gps);
		return b.array();
	}

	private static byte[] exifLittleEndian(int orientation) {
		return new byte[] { 'E', 'x', 'i', 'f', 0, 0, 'I', 'I', 42, 0, 8, 0, 0, 0, // TIFF header, IFD0 at 8
				1, 0, // 1 entry
				0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static byte[] insertPngChunkAfterIhdr(byte[] png, String type, byte[] data) {
		int afterIhdr = 8 + 12 + 13; // signature + IHDR chunk
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(png, 0, afterIhdr);
		out.writeBytes(pngChunk(type, data));
		out.write(png, afterIhdr, png.length - afterIhdr);
		return out.toByteArray();
	}

	private static byte[] pngWithHeaderOnly(int width, int height) {
		ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put(new byte[] { 8, 2, 0, 0, 0 });
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
		out.writeBytes(pngChunk("IHDR", ihdr.array()));
		out.writeBytes(pngChunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	private static byte[] pngChunk(String type, byte[] data) {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		return ByteBuffer.allocate(12 + data.length).putInt(data.length).put(typeBytes).put(data)
				.putInt((int) crc.getValue()).array();
	}

	private static boolean contains(byte[] data, String text) {
		byte[] needle = text.getBytes(StandardCharsets.US_ASCII);
		outer: for (int i = 0; i <= data.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (data[i + j] != needle[j]) {
					continue outer;
				}
			}
			return true;
		}
		return false;
	}
}
