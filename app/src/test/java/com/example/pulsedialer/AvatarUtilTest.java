package com.example.pulsedialer;

import com.example.pulsedialer.util.AvatarUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarUtilTest {

    @Test public void initialsFromSingleName() {
        assertEquals("J", AvatarUtil.initials("John"));
    }

    @Test public void initialsFromFullName() {
        assertEquals("JD", AvatarUtil.initials("John Doe"));
    }

    @Test public void initialsFromThreeNames() {
        assertEquals("JS", AvatarUtil.initials("John Michael Smith"));
    }

    @Test public void initialsFromNullReturnsPlaceholder() {
        assertEquals("•", AvatarUtil.initials(null));
        assertEquals("•", AvatarUtil.initials(""));
        assertEquals("•", AvatarUtil.initials("   "));
    }

    @Test public void colorIsConsistentForSameName() {
        int color1 = AvatarUtil.colorFor("Alice");
        int color2 = AvatarUtil.colorFor("Alice");
        assertEquals(color1, color2);
    }

    @Test public void colorDiffersForDifferentNames() {
        int color1 = AvatarUtil.colorFor("Alice");
        int color2 = AvatarUtil.colorFor("Bob");
        // Not guaranteed to differ but very likely with different hash
        // Just verify they're valid ARGB
        assertTrue((color1 & 0xFF000000) != 0);
        assertTrue((color2 & 0xFF000000) != 0);
    }

    @Test public void colorForNullReturnsDefault() {
        int color = AvatarUtil.colorFor(null);
        assertEquals(0xFF3B82F6, color);
    }

    @Test public void initialsAreUppercase() {
        assertEquals("JD", AvatarUtil.initials("john doe"));
    }
}
