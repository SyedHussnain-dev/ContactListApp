package com.example.pulsedialer.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.Locale;

/** Generates consistent avatar colors and initials. */
public final class AvatarUtil {
    private static final int[] COLORS = {
        0xFF1A73E8, 0xFF5C6BC0, 0xFF00897B, 0xFF0D904F,
        0xFFF9AB00, 0xFFD93025, 0xFF7B61FF, 0xFF00A3C4,
        0xFF039BE5, 0xFFE91E63, 0xFF7CB342, 0xFFE53935
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
