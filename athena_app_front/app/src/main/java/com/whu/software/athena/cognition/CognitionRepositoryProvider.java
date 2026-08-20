package com.whu.software.athena.cognition;

import android.content.Context;
import android.content.SharedPreferences;

/** Single composition point used by screens. Demo remains the default until a backend environment is selected. */
public final class CognitionRepositoryProvider {

    private static final String PREFS = "athena_cognition_config";
    private static final String USE_HTTP = "use_http";
    private static CognitionRepository instance;

    private CognitionRepositoryProvider() {}

    public static synchronized CognitionRepository get(Context context) {
        if (instance == null) {
            SharedPreferences preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            instance = preferences.getBoolean(USE_HTTP, false)
                    ? new HttpCognitionRepository(context)
                    : new DemoCognitionRepository(context);
        }
        return instance;
    }

    public static synchronized void useHttp(Context context, boolean useHttp) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(USE_HTTP, useHttp).apply();
        instance = null;
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
