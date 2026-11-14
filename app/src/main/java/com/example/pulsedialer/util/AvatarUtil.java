package com.example.pulsedialer.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.Locale;

/** Generates consistent avatar colors and initials. */
public final class AvatarUtil {
    private static final int[] COLORS = {
        0xFF3B82F6, 0xFF6366F1, 0xFF14B8A6, 0xFF10B981,
        0xFFF59E0B, 0xFFF43F5E, 0xFF8B5CF6, 0xFF06B6D4,
        0xFF0EA5E9, 0xFFEC4899, 0xFF84CC16, 0xFFE11D48
    };

    private AvatarUtil() {}

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "•";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.getDefault());
    }

    public static int colorFor(String name) {
        if (name == null || name.isEmpty()) return COLORS[0];
        int hash = 0;
        for (char c : name.toCharArray()) hash = 31 * hash + c;
        return COLORS[Math.abs(hash) % COLORS.length];
    }

    public static void apply(@NonNull TextView avatar, String name) {
        avatar.setText(initials(name));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(colorFor(name));
        avatar.setBackground(bg);
    }
}
