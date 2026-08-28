package com.whu.software.athena;

import android.app.Application;
import android.util.Log;

import com.shuyu.gsyvideoplayer.player.PlayerFactory;

import tv.danmaku.ijk.media.exo2.Exo2PlayerManager;


public class AthenaApplication extends Application {

    private static final String TAG = "AthenaApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        // ExoPlayer has no dependency on the legacy IJK .so files and runs on 16 KB devices.
        PlayerFactory.setPlayManager(Exo2PlayerManager.class);

        try {
            Class<?> api = Class.forName("com.heytap.databaseengine.HeytapHealthApi");
            api.getMethod("init", android.content.Context.class).invoke(null, this);
            api.getMethod("setLoggable", boolean.class).invoke(null, true);
            Log.i(TAG, "HeyTap Health SDK initialized successfully");
        } catch (Throwable e) {
            // The SDK is optional on non-OPPO devices and local 16 KB emulators.
            Log.i(TAG, "HeyTap Health SDK unavailable; device integration disabled");
        }
    }
}
