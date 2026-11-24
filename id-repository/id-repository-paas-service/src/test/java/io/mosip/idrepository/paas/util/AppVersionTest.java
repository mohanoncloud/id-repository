package io.mosip.idrepository.paas.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppVersionTest {

	@Test
	public void partsAreComparedAsNumbersNotText() {
		assertTrue(AppVersion.compare("1.10.0", "1.9.2") > 0);
		assertTrue(AppVersion.compare("1.9.0", "1.10.0") < 0);
		assertTrue(AppVersion.compare("2.0", "1.99.99") > 0);
		assertTrue(AppVersion.compare("10", "9") > 0);
	}

	@Test
	public void missingPartsCountAsZero() {
		assertEquals(0, AppVersion.compare("1.2", "1.2.0"));
		assertEquals(0, AppVersion.compare("1", "1.0.0.0"));
		assertTrue(AppVersion.compare("1.2", "1.2.1") < 0);
	}

	@Test
	public void leadingZerosAndVeryLongPartsAreHandled() {
		assertEquals(0, AppVersion.compare("1.02.0", "1.2.0"));
		assertTrue(AppVersion.compare("1.99999999999999999999", "1.99999999999999999998") > 0);
	}

	@Test
	public void isOlderIsStrict() {
		assertTrue(AppVersion.isOlder("1.1.9", "1.2.0"));
		assertFalse(AppVersion.isOlder("1.2.0", "1.2.0"));
		assertFalse(AppVersion.isOlder("1.2.1", "1.2.0"));
	}

	@Test
	public void onlyDottedNumbersAreValid() {
		assertTrue(AppVersion.isValid("1.3.0"));
		assertTrue(AppVersion.isValid("7"));
		assertFalse(AppVersion.isValid(null));
		assertFalse(AppVersion.isValid(""));
		assertFalse(AppVersion.isValid("v1.3"));
		assertFalse(AppVersion.isValid("1.3.0-beta"));
		assertFalse(AppVersion.isValid("1..3"));
		assertFalse(AppVersion.isValid("1.3."));
		assertFalse(AppVersion.isValid(" 1.3"));
		assertFalse(AppVersion.isValid("1." + "1".repeat(40)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void compareRejectsAnInvalidVersion() {
		AppVersion.compare("1.0", "latest");
	}
}
