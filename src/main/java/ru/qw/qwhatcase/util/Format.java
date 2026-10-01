package ru.qw.qwhatcase.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private Format() {
    }

    /** Округление только для отображения: точное значение не меняется. */
    public static String percent(double value) {
        if (value >= 1) {
            return String.format(Locale.ROOT, "%.2f%%", value);
        }
        if (value >= 0.01) {
            return String.format(Locale.ROOT, "%.3f%%", value);
        }
        return String.format(Locale.ROOT, "%.5f%%", value);
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis));
    }
}
