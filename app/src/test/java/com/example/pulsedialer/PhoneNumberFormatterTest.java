package com.example.pulsedialer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneNumberFormatterTest {
    @Test public void keepsDialingCharactersAndOnlyLeadingPlus() {
        assertEquals("+12025550144;123", PhoneNumberFormatter.sanitize(" +1 (202) 555-0144;123"));
        assertEquals("1202", PhoneNumberFormatter.sanitize("120+2"));
    }

    @Test public void rejectsEmptyAndPlusOnlyValues() {
        assertFalse(PhoneNumberFormatter.isDialable(""));
        assertFalse(PhoneNumberFormatter.isDialable("+"));
        assertTrue(PhoneNumberFormatter.isDialable("+1202"));
        assertTrue(PhoneNumberFormatter.isDialable("*"));
    }

    @Test public void deletesSelectionOrCharacterBeforeCursor() {
        assertEquals("124", PhoneNumberFormatter.deleteBackward("1234", 3, 3));
        assertEquals("14", PhoneNumberFormatter.deleteBackward("1234", 1, 3));
        assertEquals("1234", PhoneNumberFormatter.deleteBackward("1234", 0, 0));
    }
}
