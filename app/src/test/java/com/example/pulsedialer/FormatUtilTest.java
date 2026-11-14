package com.example.pulsedialer;

import com.example.pulsedialer.util.FormatUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class FormatUtilTest {

    @Test public void durationFormatsMinutesAndSeconds() {
        assertEquals("0:00", FormatUtil.duration(0));
        assertEquals("0:01", FormatUtil.duration(1));
        assertEquals("0:59", FormatUtil.duration(59));
        assertEquals("1:00", FormatUtil.duration(60));
        assertEquals("1:30", FormatUtil.duration(90));
        assertEquals("10:05", FormatUtil.duration(605));
    }

    @Test public void durationFormatsHours() {
        assertEquals("1:00:00", FormatUtil.duration(3600));
        assertEquals("1:05:30", FormatUtil.duration(3930));
        assertEquals("2:00:00", FormatUtil.duration(7200));
    }

    @Test public void durationHandlesNegative() {
        assertEquals("0:00", FormatUtil.duration(-5));
    }

    @Test public void cleanNumberRemovesFormatting() {
        assertEquals("+12025551234", FormatUtil.cleanNumber("+1 (202) 555-1234"));
        assertEquals("555", FormatUtil.cleanNumber("555"));
        assertEquals("", FormatUtil.cleanNumber(null));
        assertEquals("", FormatUtil.cleanNumber(""));
    }

    @Test public void cleanNumberPreservesSpecialChars() {
        assertEquals("+12025551234", FormatUtil.cleanNumber("+1 (202) 555-1234"));
        assertEquals("*67", FormatUtil.cleanNumber("*67"));
        assertEquals("#31#", FormatUtil.cleanNumber("#31#"));
    }

    @Test public void dateGroupReturnsNonNull() {
        String group = FormatUtil.dateGroup(System.currentTimeMillis());
        assertNotNull(group);
        assertEquals("Today", group);
    }

    @Test public void dateLabelReturnsNonNull() {
        String label = FormatUtil.dateLabel(System.currentTimeMillis());
        assertNotNull(label);
        assertTrue(label.startsWith("Today"));
    }

    @Test public void dateGroupYesterday() {
        long yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
        String group = FormatUtil.dateGroup(yesterday);
        assertEquals("Yesterday", group);
    }
}
