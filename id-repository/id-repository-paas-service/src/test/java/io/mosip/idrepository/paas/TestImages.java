package io.mosip.idrepository.paas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** Synthetic test images (a white page with a line of made-up text). Never real passports. */
public final class TestImages {

	private TestImages() {
	}

	public static BufferedImage image(int width, int height) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.BLACK);
		g.drawString("P<UTOERIKSSON<<ANNA<MARIA", 50, height - 100);
		g.dispose();
		return img;
	}

	public static byte[] jpeg(int width, int height) {
		return encode(image(width, height), "jpg");
	}

	public static byte[] png(int width, int height) {
		return encode(image(width, height), "png");
	}

	/** A JPEG that passes the file checks (1600 x 1200). */
	public static byte[] validJpeg() {
		return jpeg(1600, 1200);
	}

	public static byte[] encode(BufferedImage img, String format) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(img, format, out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
