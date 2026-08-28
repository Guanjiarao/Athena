package com.whu.software.athena.cognition;

import android.content.Context;
import android.content.SharedPreferences;

/** Single composition point. Deployed HTTP is the default; demo is an explicit showcase mode. */
public final class CognitionRepositoryProvider {

    private static final String PREFS = "athena_cognition_config";
    private static final String USE_HTTP = "use_http";
    private static final String MODE_VERSION = "mode_version";
    private static final int CURRENT_MODE_VERSION = 2;
    private static CognitionRepository instance;

    private CognitionRepositoryProvider() {}

    public static synchronized CognitionRepository get(Context context) {
        if (instance == null) {
            SharedPreferences preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (preferences.getInt(MODE_VERSION, 0) < CURRENT_MODE_VERSION) {
                preferences.edit().putBoolean(USE_HTTP, true)
                        .putInt(MODE_VERSION, CURRENT_MODE_VERSION).apply();
            }
            instance = preferences.getBoolean(USE_HTTP, true)
                    ? new HttpCognitionRepository(context)
                    : new DemoCognitionRepository(context);
        }
        return instance;
    }

    public static synchronized void useHttp(Context context, boolean useHttp) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(USE_HTTP, useHttp).putInt(MODE_VERSION, CURRENT_MODE_VERSION).apply();
        instance = null;
    }

    public static boolean isHttp(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(USE_HTTP, true);
    }

    public static synchronized void resetDemo(Context context) {
        new DemoCognitionRepository(context).resetDemo();
        useHttp(context, false);
    }

    public static synchronized String exportDemo(Context context) {
        return new DemoCognitionRepository(context).exportDemoJson();
    }

    public static synchronized void clearDemo(Context context) {
        new DemoCognitionRepository(context).clearDemo();
        useHttp(context, false);
    }
}
