package com.whu.software.athena;

import android.app.Application;
import android.util.Log;

import com.heytap.databaseengine.HeytapHealthApi;


public class AthenaApplication extends Application {

    private static final String TAG = "AthenaApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            HeytapHealthApi.init(this);
            HeytapHealthApi.setLoggable(true);
            Log.i(TAG, "HeyTap Health SDK initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "HeyTap Health SDK init failed: " + e.getMessage(), e);
        }
    }
}
