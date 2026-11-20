package io.mosip.idrepository.paas.passport.mrz;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Check digits from the ICAO 9303 Part 4 TD3 specimen (Anna Maria Eriksson, Utopia). */
public class MrzCheckDigitTest {

	@Test
	public void specimenCheckDigits() {
		assertEquals(6, MrzCheckDigit.compute("L898902C3"));
		assertEquals(2, MrzCheckDigit.compute("740812"));
		assertEquals(9, MrzCheckDigit.compute("120415"));
		assertEquals(1, MrzCheckDigit.compute("ZE184226B<<<<<"));
		assertEquals(0, MrzCheckDigit.compute("L898902C36" + "7408122" + "1204159ZE184226B<<<<<1"));
	}

	@Test
	public void fillerCountsAsZero() {
		assertEquals(0, MrzCheckDigit.compute("<<<<<<<<<<<<<<"));
		assertEquals(MrzCheckDigit.compute("AB0"), MrzCheckDigit.compute("AB<"));
	}

	@Test
	public void matchesRequiresADigit() {
		assertTrue(MrzCheckDigit.matches("L898902C3", '6'));
		assertFalse(MrzCheckDigit.matches("L898902C3", '7'));
		assertFalse(MrzCheckDigit.matches("<<<<<<<<<<<<<<", '<'));
	}

	@Test(expected = IllegalArgumentException.class)
	public void lowerCaseIsNotAnMrzCharacter() {
		MrzCheckDigit.compute("ab1");
	}

	@Test(expected = IllegalArgumentException.class)
	public void spaceIsNotAnMrzCharacter() {
		MrzCheckDigit.compute("A B");
	}
}
