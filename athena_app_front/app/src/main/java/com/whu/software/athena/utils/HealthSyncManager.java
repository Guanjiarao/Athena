package com.whu.software.athena.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared sync state between health pages and analysis report page.
 */
public final class HealthSyncManager {

    private static final String PREF_NAME = "health_sync_state";
    private static final String KEY_INSIGHT_DIRTY = "insight_dirty";
    private static final String KEY_LAST_CYCLE_MUTATION_AT = "last_cycle_mutation_at";
    private static final String KEY_DIRTY_MONTHS = "calendar_dirty_months";

    private HealthSyncManager() {
    }

    private static SharedPreferences sp(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void markInsightDirty(@NonNull Context context) {
        sp(context).edit()
                .putBoolean(KEY_INSIGHT_DIRTY, true)
                .putLong(KEY_LAST_CYCLE_MUTATION_AT, System.currentTimeMillis())
                .apply();
    }

    public static boolean isInsightDirty(@NonNull Context context) {
        return sp(context).getBoolean(KEY_INSIGHT_DIRTY, false);
    }

    public static void clearInsightDirty(@NonNull Context context) {
        sp(context).edit()
                .putBoolean(KEY_INSIGHT_DIRTY, false)
                .apply();
    }

    public static long getLastCycleMutationAt(@NonNull Context context) {
        return sp(context).getLong(KEY_LAST_CYCLE_MUTATION_AT, 0L);
    }

    public static void markCalendarMonthDirty(@NonNull Context context, int year, int month) {
        Set<String> months = new HashSet<>(sp(context).getStringSet(KEY_DIRTY_MONTHS, new HashSet<>()));
        months.add(monthKey(year, month));
        sp(context).edit().putStringSet(KEY_DIRTY_MONTHS, months).apply();
    }

    public static void markCalendarMonthDirty(@NonNull Context context, @NonNull LocalDate date) {
        markCalendarMonthDirty(context, date.getYear(), date.getMonthValue());
    }

    public static boolean isCalendarMonthDirty(@NonNull Context context, int year, int month) {
        Set<String> months = sp(context).getStringSet(KEY_DIRTY_MONTHS, new HashSet<>());
        return months != null && months.contains(monthKey(year, month));
    }

    public static void clearCalendarMonthDirty(@NonNull Context context, int year, int month) {
        Set<String> months = new HashSet<>(sp(context).getStringSet(KEY_DIRTY_MONTHS, new HashSet<>()));
        months.remove(monthKey(year, month));
        sp(context).edit().putStringSet(KEY_DIRTY_MONTHS, months).apply();
    }

    public static void markCycleMutation(@NonNull Context context, @NonNull String dateText) {
        markInsightDirty(context);
        try {
            LocalDate date = LocalDate.parse(dateText);
            markCalendarMonthDirty(context, date);
        } catch (Exception ignored) {
        }
    }

    private static String monthKey(int year, int month) {
        return year + "-" + month;
    }
}
