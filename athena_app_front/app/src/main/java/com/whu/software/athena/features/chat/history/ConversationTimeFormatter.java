package com.whu.software.athena.features.chat.history;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class ConversationTimeFormatter {

    private static final String[] SERVER_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss"
    };

    private ConversationTimeFormatter() {
    }

    @NonNull
    public static String formatMillis(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                .format(new Date(millis));
    }

    public static long parseServerTimeToMillis(String rawTime) {
        if (TextUtils.isEmpty(rawTime)) {
            return 0L;
        }
        for (String pattern : SERVER_PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = format.parse(rawTime);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }

    @NonNull
    public static String formatServerTime(String rawTime) {
        long millis = parseServerTimeToMillis(rawTime);
        return millis > 0L ? formatMillis(millis) : "";
    }
}
