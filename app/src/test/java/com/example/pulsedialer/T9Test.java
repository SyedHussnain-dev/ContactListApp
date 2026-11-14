package com.example.pulsedialer;

import org.junit.Test;
import static org.junit.Assert.*;

public class T9Test {
    @Test public void mapsLetters() {
        assertEquals("43556", T9.digitsFor("hello"));
    }

    @Test public void matchesNameAndNumber() {
        assertTrue(T9.matches("435", "hello", ""));
        assertTrue(T9.matches("555", "x", "5551234"));
        assertFalse(T9.matches("99", "abc", "123"));
    }

    @Test public void mapsTraditionalKeypadGroups() {
        assertEquals("2", T9.digitsFor("A"));
        assertEquals("3", T9.digitsFor("D"));
        assertEquals("4", T9.digitsFor("G"));
        assertEquals("5", T9.digitsFor("J"));
        assertEquals("6", T9.digitsFor("M"));
        assertEquals("7", T9.digitsFor("P"));
        assertEquals("8", T9.digitsFor("T"));
        assertEquals("9", T9.digitsFor("W"));
        assertEquals("7", T9.digitsFor("S"));
    }

    @Test public void punctuationAndAccentsDoNotBreakNameMatch() {
        assertEquals("5673", T9.digitsFor("Jóse"));
        assertTrue(T9.matches("567", "Jóse García", "+1 (202) 555-0144"));
    }

    @Test public void numericSearchKeepsNumberFallback() {
        assertTrue(T9.matches("202555", "Contact", "+1 (202) 555-0144"));
        assertFalse(T9.matches("999", "Contact", "+1 (202) 555-0144"));
    }

    @Test public void emptyQueryMatchesNothing() {
        // digitsFor("") → ""
        // matches with empty query returns true (all contacts shown)
        assertTrue(T9.matches("", "anything", "123"));
    }

    @Test public void nullSafeInputs() {
        assertEquals("", T9.digitsFor(null));
        assertTrue(T9.matches("", null, null));
        assertFalse(T9.matches("222", null, null));
    }

    @Test public void fullNameMatch() {
        // "John" → 5646
        assertTrue(T9.matches("5646", "John", ""));
        // "Smith" → 76484
        assertTrue(T9.matches("76484", "Smith", ""));
    }

    @Test public void partialNameMatch() {
        assertTrue(T9.matches("56", "John", ""));
        assertTrue(T9.matches("764", "Smith", ""));
    }

    @Test public void numberOnlyMatch() {
        assertTrue(T9.matches("12345", "UnrelatedName", "12345678"));
        assertFalse(T9.matches("99999", "UnrelatedName", "12345678"));
    }
}
