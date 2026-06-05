package com.whu.software.athena.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/**
 * 生理期本地数据中心：
 * - 管理用户设置的经期时长 / 周期长度 / 是否不规律
 * - 记录最后一次真实「月经来了」的起始日期
 */
public final class CycleDataManager {

    private static final String PREF_NAME            = "cycle_settings";
    private static final String KEY_PERIOD_DAYS      = "period_days";
    private static final String KEY_CYCLE_DAYS       = "cycle_days";
    private static final String KEY_IRREGULAR        = "irregular";
    private static final String KEY_LAST_PERIOD_DATE = "last_period_start"; // yyyy-MM-dd
    private static final String KEY_ACTUAL_PERIOD_VISIBLE = "actual_period_visible";

    private static final int  DEFAULT_PERIOD_DAYS = 5;
    private static final int  DEFAULT_CYCLE_DAYS  = 28;
    private static final boolean DEFAULT_IRREGULAR = false;
    private static final boolean DEFAULT_ACTUAL_PERIOD_VISIBLE = true;

    private CycleDataManager() {}

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------------
    // 任务 1：规律设置的读写
    // ---------------------------------------------------------------------

    public static void saveSettings(Context ctx,
                                    int periodDays,
                                    int cycleDays,
                                    boolean irregular) {
        sp(ctx).edit()
                .putInt(KEY_PERIOD_DAYS, periodDays)
                .putInt(KEY_CYCLE_DAYS,  cycleDays)
                .putBoolean(KEY_IRREGULAR, irregular)
                .apply();
    }

    public static int getPeriodDays(Context ctx) {
        return sp(ctx).getInt(KEY_PERIOD_DAYS, DEFAULT_PERIOD_DAYS);
    }

    public static int getCycleDays(Context ctx) {
        return sp(ctx).getInt(KEY_CYCLE_DAYS, DEFAULT_CYCLE_DAYS);
    }

    public static boolean isIrregular(Context ctx) {
        return sp(ctx).getBoolean(KEY_IRREGULAR, DEFAULT_IRREGULAR);
    }

    // ---------------------------------------------------------------------
    // 扩展：记录最后一次真实经期起始日（供预测算法使用）
    // ---------------------------------------------------------------------

    public static void saveLastPeriodStart(Context ctx, LocalDate startDate) {
        if (startDate == null) {
            sp(ctx).edit().remove(KEY_LAST_PERIOD_DATE).apply();
            return;
        }
        sp(ctx).edit().putString(KEY_LAST_PERIOD_DATE, startDate.toString()).apply();
    }

    public static LocalDate getLastPeriodStart(Context ctx) {
        String stored = sp(ctx).getString(KEY_LAST_PERIOD_DATE, null);
        if (stored == null || stored.isEmpty()) return null;
        try {
            return LocalDate.parse(stored);
        } catch (Exception e) {
            return null;
        }
    }

    public static void setActualPeriodVisible(Context ctx, boolean visible) {
        sp(ctx).edit().putBoolean(KEY_ACTUAL_PERIOD_VISIBLE, visible).apply();
    }

    public static boolean isActualPeriodVisible(Context ctx) {
        return sp(ctx).getBoolean(KEY_ACTUAL_PERIOD_VISIBLE, DEFAULT_ACTUAL_PERIOD_VISIBLE);
    }
}
