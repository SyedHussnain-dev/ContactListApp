package com.example.pulsedialer;

/** Normalizes user-entered dial strings without changing their dialing meaning. */
public final class PhoneNumberFormatter {
    private PhoneNumberFormatter() {}

    public static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        boolean hasPlus = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '0' && c <= '9' || c == '*' || c == '#' || c == ',' || c == ';' || c == 'N') {
                out.append(c);
            } else if (c == '+' && !hasPlus && out.length() == 0) {
                out.append(c);
                hasPlus = true;
            }
        }
        return out.toString();
    }

    public static boolean isDialable(String value) {
        String sanitized = sanitize(value);
        if (sanitized.isEmpty() || "+".equals(sanitized)) return false;
        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            if (c >= '0' && c <= '9') return true;
        }
        return "*".equals(sanitized) || "#".equals(sanitized);
    }

    /** Deletes the selected range, or the character immediately before the cursor. */
    public static String deleteBackward(String value, int selectionStart, int selectionEnd) {
        if (value == null || value.isEmpty()) return "";
        int start = Math.max(0, Math.min(selectionStart, value.length()));
        int end = Math.max(0, Math.min(selectionEnd, value.length()));
        if (start > end) {
            int swap = start;
            start = end;
            end = swap;
        }
        if (start != end) return value.substring(0, start) + value.substring(end);
        if (start == 0) return value;
        return value.substring(0, start - 1) + value.substring(start);
    }
}
