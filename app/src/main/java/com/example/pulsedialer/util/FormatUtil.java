package com.example.pulsedialer.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Formatting helpers for dates, durations, and numbers. */
public final class FormatUtil {
    private FormatUtil() {}

    public static String duration(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    public static String dateLabel(long millis) {
        Date date = new Date(millis);
        Calendar now = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = dayFormat.format(now.getTime());
        now.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = dayFormat.format(now.getTime());
        String value = dayFormat.format(date);

        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
        if (value.equals(today)) return "Today, " + timeFormat.format(date);
        if (value.equals(yesterday)) return "Yesterday, " + timeFormat.format(date);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(date);
    }

    public static String dateGroup(long millis) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String today = dayFormat.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -1);
        String yesterday = dayFormat.format(cal.getTime());
        String value = dayFormat.format(new Date(millis));

        if (value.equals(today)) return "Today";
        if (value.equals(yesterday)) return "Yesterday";
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(millis));
    }

    public static String cleanNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9+*#;,N]", "");
    }
}
