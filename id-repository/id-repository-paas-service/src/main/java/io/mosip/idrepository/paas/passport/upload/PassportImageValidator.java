package io.mosip.idrepository.paas.passport.upload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import io.mosip.idrepository.paas.config.PassportProperties;

/**
 * File checks for an uploaded passport image (MP-256, ENR-PSP-001), in this order; the first failure stops the
 * rest:
 * <ol>
 *   <li>Not empty, and no larger than the size limit (5 MB): HTTP 413 if larger.</li>
 *   <li>File type from the contents (magic bytes), not the name or declared content type: JPEG or PNG only.</li>
 *   <li>Dimensions from the header, without decoding: pixel count under the limit (decompression bomb protection),
 *       long side at least 1200 px (API spec, section 3).</li>
 *   <li>Metadata removed (EXIF incl. GPS, XMP, comments, text chunks), pixels untouched.</li>
 *   <li>The cleaned file is fully decoded once, to prove it's a real image.</li>
 * </ol>
 *
 * <p>Malware scanning (if the platform has one) and the image quality check (blur, glare, whole page) are not
 * part of this.
 */
@Component
public class PassportImageValidator {

	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	private final long maxBytes;
	private final int minLongSide;
	private final long maxPixels;

	/** Fails at startup on limits that are zero or negative. */
	public PassportImageValidator(PassportProperties properties) {
		this.maxBytes = properties.getImageMaxSize().toBytes();
		this.minLongSide = properties.getImageMinLongSide();
		this.maxPixels = properties.getImageMaxPixels();
		if (maxBytes <= 0 || minLongSide <= 0 || maxPixels <= 0) {
			throw new IllegalArgumentException(
					"mosip.idpaas.passport.image-max-size, image-min-long-side and image-max-pixels must be positive");
		}
	}

	/**
	 * @param part the request part, {@code frontImage} or {@code backImage}; named in the log message on failure
	 * @throws UploadException ENR-PSP-001 if the file fails a check
	 */
	public SanitisedImage validate(String part, byte[] file) {
		try {
			return validate(file);
		} catch (UploadException e) {
			throw e.forPart(part);
		}
	}

	private SanitisedImage validate(byte[] file) {
		if (file == null || file.length == 0) {
			throw new UploadException(UploadError.EMPTY_FILE);
		}
		if (file.length > maxBytes) {
			throw new UploadException(UploadError.FILE_TOO_LARGE);
		}
		SanitisedImage.Format format = detectFormat(file);
		int[] size = readDimensions(file);
		int width = size[0];
		int height = size[1];
		if ((long) width * height > maxPixels) {
			throw new UploadException(UploadError.IMAGE_TOO_LARGE);
		}
		if (Math.max(width, height) < minLongSide) {
			throw new UploadException(UploadError.RESOLUTION_TOO_LOW);
		}
		MetadataStripper.Result stripped = format == SanitisedImage.Format.JPEG ? MetadataStripper.stripJpeg(file)
				: MetadataStripper.stripPng(file);
		decodeFully(stripped.data());
		return new SanitisedImage(stripped.data(), format, width, height, stripped.exifOrientation());
	}

	private static SanitisedImage.Format detectFormat(byte[] file) {
		if (MetadataStripper.startsWith(file, 0, JPEG_MAGIC)) {
			return SanitisedImage.Format.JPEG;
		}
		if (MetadataStripper.startsWith(file, 0, PNG_MAGIC)) {
			return SanitisedImage.Format.PNG;
		}
		throw new UploadException(UploadError.UNSUPPORTED_FILE_TYPE);
	}

	/** Width and height from the image header only; no pixel data is decoded. */
	private static int[] readDimensions(byte[] file) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
			ImageReader reader = firstReader(in);
			try {
				reader.setInput(in, true, true);
				return new int[] { reader.getWidth(0), reader.getHeight(0) };
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			throw new UploadException(UploadError.MALFORMED_IMAGE);
		}
	}

	private static void decodeFully(byte[] file) {
		try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
			ImageReader reader = firstReader(in);
			try {
				reader.setInput(in, true, true);
				reader.read(0);
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException e) {
			throw new UploadException(UploadError.MALFORMED_IMAGE);
		}
	}

	private static ImageReader firstReader(ImageInputStream in) {
		if (in == null) {
			throw new UploadException(UploadError.MALFORMED_IMAGE);
		}
		Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
		if (!readers.hasNext()) {
			throw new UploadException(UploadError.MALFORMED_IMAGE);
		}
		return readers.next();
	}
}
