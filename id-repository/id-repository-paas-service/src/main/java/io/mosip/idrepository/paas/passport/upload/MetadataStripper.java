package io.mosip.idrepository.paas.passport.upload;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Removes metadata (EXIF incl. GPS location, XMP, comments, text chunks) from JPEG and PNG files without
 * re-encoding the pixels, and reads the EXIF orientation first.
 */
final class MetadataStripper {

	record Result(byte[] data, int exifOrientation) {
	}

	/** PNG chunks kept: image data plus colour information. Everything else (tEXt, iTXt, zTXt, eXIf, tIME...) is dropped. */
	private static final Set<String> PNG_KEEP = Set.of("IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB",
			"iCCP", "sBIT", "pHYs", "bKGD");

	private static final byte[] EXIF_HEADER = { 'E', 'x', 'i', 'f', 0, 0 };
	private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] ADOBE_HEADER = "Adobe".getBytes(StandardCharsets.US_ASCII);

	private MetadataStripper() {
	}

	// ------------------------------------------------------------------ JPEG

	/**
	 * Keeps APP0 (JFIF), APP2 ICC profile and APP14 Adobe (colour transform); drops APP1 (EXIF/XMP), other APPn and
	 * COM. Everything from the first SOS onwards is copied as is.
	 */
	static Result stripJpeg(byte[] in) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
		out.write(in, 0, 2); // SOI
		int orientation = 1;
		int pos = 2;
		while (pos < in.length) {
			if ((in[pos] & 0xFF) != 0xFF) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			while (pos < in.length && (in[pos] & 0xFF) == 0xFF) {
				pos++; // fill bytes
			}
			if (pos >= in.length) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			int marker = in[pos++] & 0xFF;
			if (marker == 0xDA || marker == 0xD9) { // start of scan / end of image: copy the rest
				out.write(0xFF);
				out.write(in, pos - 1, in.length - (pos - 1));
				return new Result(out.toByteArray(), orientation);
			}
			if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // standalone markers
				out.write(0xFF);
				out.write(marker);
				continue;
			}
			if (pos + 2 > in.length) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			int length = ((in[pos] & 0xFF) << 8) | (in[pos + 1] & 0xFF);
			if (length < 2 || pos + length > in.length) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			int dataStart = pos + 2;
			int dataLength = length - 2;
			if (marker == 0xE1 && startsWith(in, dataStart, EXIF_HEADER)) {
				orientation = exifOrientation(in, dataStart + EXIF_HEADER.length, dataLength - EXIF_HEADER.length);
			}
			if (keepJpegSegment(marker, in, dataStart)) {
				out.write(0xFF);
				out.write(marker);
				out.write(in, pos, length);
			}
			pos += length;
		}
		throw new UploadException(UploadError.MALFORMED_IMAGE); // no image data
	}

	private static boolean keepJpegSegment(int marker, byte[] in, int dataStart) {
		if (marker == 0xFE) {
			return false; // COM
		}
		if (marker < 0xE0 || marker > 0xEF) {
			return true; // not an APPn: tables, frame header etc.
		}
		return marker == 0xE0 || (marker == 0xE2 && startsWith(in, dataStart, ICC_HEADER))
				|| (marker == 0xEE && startsWith(in, dataStart, ADOBE_HEADER));
	}

	// ------------------------------------------------------------------ PNG

	static Result stripPng(byte[] in) {
		ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
		out.write(in, 0, 8); // signature
		int orientation = 1;
		int pos = 8;
		boolean ended = false;
		while (pos < in.length && !ended) {
			if (pos + 8 > in.length) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			long length = Integer.toUnsignedLong(readInt(in, pos));
			String type = new String(in, pos + 4, 4, StandardCharsets.US_ASCII);
			long total = 12 + length;
			if (pos + total > in.length) {
				throw new UploadException(UploadError.MALFORMED_IMAGE);
			}
			if (type.equals("eXIf")) {
				orientation = exifOrientation(in, pos + 8, (int) length);
			}
			if (PNG_KEEP.contains(type)) {
				out.write(in, pos, (int) total);
			}
			ended = type.equals("IEND");
			pos += (int) total;
		}
		if (!ended) {
			throw new UploadException(UploadError.MALFORMED_IMAGE);
		}
		return new Result(out.toByteArray(), orientation);
	}

	// ------------------------------------------------------------------ EXIF orientation

	/** Reads tag 0x0112 from IFD0 of a TIFF-structured EXIF block. Returns 1 if absent or unreadable. */
	static int exifOrientation(byte[] in, int tiffStart, int tiffLength) {
		try {
			if (tiffLength < 8) {
				return 1;
			}
			boolean little = in[tiffStart] == 'I' && in[tiffStart + 1] == 'I';
			boolean big = in[tiffStart] == 'M' && in[tiffStart + 1] == 'M';
			if (!little && !big) {
				return 1;
			}
			int ifd0 = tiffStart + read32(in, tiffStart + 4, little);
			int end = tiffStart + tiffLength;
			if (ifd0 + 2 > end) {
				return 1;
			}
			int entries = read16(in, ifd0, little);
			for (int i = 0; i < entries; i++) {
				int entry = ifd0 + 2 + i * 12;
				if (entry + 12 > end) {
					return 1;
				}
				if (read16(in, entry, little) == 0x0112) {
					int value = read16(in, entry + 8, little);
					return value >= 1 && value <= 8 ? value : 1;
				}
			}
			return 1;
		} catch (ArrayIndexOutOfBoundsException e) {
			return 1;
		}
	}

	// ------------------------------------------------------------------ helpers

	private static int read16(byte[] b, int pos, boolean little) {
		return little ? (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8) : ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
	}

	private static int read32(byte[] b, int pos, boolean little) {
		return little ? read16(b, pos, true) | (read16(b, pos + 2, true) << 16)
				: (read16(b, pos, false) << 16) | read16(b, pos + 2, false);
	}

	private static int readInt(byte[] b, int pos) {
		return read32(b, pos, false);
	}

	static boolean startsWith(byte[] data, int offset, byte[] prefix) {
		if (offset < 0 || offset + prefix.length > data.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (data[offset + i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}
}
