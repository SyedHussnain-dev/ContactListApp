package com.example.pulsedialer.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Centralised preference access for the Pulse design system settings. */
public final class PrefUtil {
    private static final String PREFS = "pulse_prefs";

    private PrefUtil() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // Theme: "dark", "light", "system"
    public static String theme(Context c) { return prefs(c).getString("theme", "dark"); }
    public static void setTheme(Context c, String v) { prefs(c).edit().putString("theme", v).apply(); }

    // Accent color (ARGB int)
    public static int accent(Context c) { return prefs(c).getInt("accent", 0xFF1A73E8); }
    public static void setAccent(Context c, int v) { prefs(c).edit().putInt("accent", v).apply(); }

    // Compact mode
    public static boolean compact(Context c) { return prefs(c).getBoolean("compact", false); }
    public static void setCompact(Context c, boolean v) { prefs(c).edit().putBoolean("compact", v).apply(); }

    // Haptics
    public static boolean haptics(Context c) { return prefs(c).getBoolean("haptics", true); }
    public static void setHaptics(Context c, boolean v) { prefs(c).edit().putBoolean("haptics", v).apply(); }
}
